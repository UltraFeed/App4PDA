package io.github.ultrafeed.app4pda.feature.installed.data

import android.app.Application
import android.content.Context
import android.database.SQLException
import android.util.Log
import io.github.ultrafeed.app4pda.core.AppPolicy
import io.github.ultrafeed.app4pda.core.network.SourceProblem
import io.github.ultrafeed.app4pda.core.network.SourceRateLimited
import io.github.ultrafeed.app4pda.data.database.LocalDatabase
import io.github.ultrafeed.app4pda.data.forum.ForumApiException
import io.github.ultrafeed.app4pda.data.forum.ForumFailure
import io.github.ultrafeed.app4pda.data.googleplay.MarketBatch
import io.github.ultrafeed.app4pda.data.preferences.AppSettings
import io.github.ultrafeed.app4pda.data.preferences.SettingsValues
import io.github.ultrafeed.app4pda.data.rustore.RuStoreRepository
import io.github.ultrafeed.app4pda.feature.installed.InstalledApp
import io.github.ultrafeed.app4pda.feature.installed.InstalledAppsState
import io.github.ultrafeed.app4pda.feature.installed.UpdateSource
import io.ktor.http.HttpStatusCode
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel as cancelScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InstalledAppsRepository
internal constructor(
    context: Context,
    private val settings: AppSettings,
    localDatabase: LocalDatabase,
    private val packages: InstalledAppsStore,
    private val sources: InstalledUpdateSources,
    coroutineScope: CoroutineScope,
) {
    private val context = context.applicationContext as Application
    private val scope =
        CoroutineScope(
            coroutineScope.coroutineContext +
                SupervisorJob(coroutineScope.coroutineContext[Job]) +
                CoroutineExceptionHandler { _, failure ->
                    Log.w("AppUpdates", "Update operation failed", failure)
                    mutableState.update {
                        it.copy(
                            errors =
                                it.errors +
                                    (UpdateSource.OPERATION to
                                        SourceProblem.from(
                                            context,
                                            "APP",
                                            failure,
                                        )),
                        )
                    }
                },
        )
    private val users = localDatabase.users()
    private val refreshMutex = Mutex()
    private val mutableState = MutableStateFlow(InstalledAppsState())
    val state: StateFlow<InstalledAppsState> = mutableState.asStateFlow()
    private var refreshJob: Deferred<UpdateCheckResult>? = null
    private var packageRefreshJob: Job? = null
    private var cooldownResume: Job? = null
    private var ruStoreResume: Job? = null

    @Volatile private var foreground = false
    private val failureKeys = ConcurrentHashMap<UpdateSource, String>()
    private val dismissedFailures = ConcurrentHashMap<UpdateSource, String>()

    init {
        scope.launch {
            combine(
                    users.observeInstalled().distinctUntilChanged(),
                    settings.changes
                        .map {
                            it.isGooglePlayCheckEnabled to it.isRuStoreCheckEnabled
                        }
                        .distinctUntilChanged(),
                ) { rows, display ->
                    rows.map { it.toApp(display.first, display.second) }
                }
                .catch { reportFailure(UpdateSource.OPERATION, it) }
                .collect { apps ->
                    mutableState.update {
                        it.copy(
                            apps = apps,
                            initialized = true,
                            unavailableOnPlay =
                                apps
                                    .filter { app ->
                                        app.playDiscovery.checkedAt > 0 && !app.playDiscovery.found
                                    }
                                    .mapTo(hashSetOf()) { app -> app.packageName },
                        )
                    }
                }
        }
    }

    suspend fun snapshot(): List<InstalledApp> {
        val display = settings.awaitReady()
        return users.installedSnapshot().map {
            it.toApp(display.isGooglePlayCheckEnabled, display.isRuStoreCheckEnabled)
        }
    }

    internal suspend fun reportFailure(source: UpdateSource, failure: Throwable) {
        if (failure is CancellationException) throw failure
        currentCoroutineContext().ensureActive()
        Log.w(
            "AppUpdates",
            "$source: ${forumFailureKey(failure)}\n" + failure.stackTrace.joinToString("\n"),
        )
        val key = forumFailureKey(failure)
        failureKeys[source] = key
        if (dismissedFailures[source] == key) return
        mutableState.update {
            it.copy(
                errors =
                    it.errors +
                        (source to
                            if (source == UpdateSource.FORUM)
                                SourceProblem.from(
                                        context,
                                        "4PDA",
                                        failure,
                                    )
                                    .copy(description = forumUpdateError(context, failure))
                            else
                                SourceProblem.from(
                                    context,
                                    source.name,
                                    failure,
                                )),
            )
        }
    }

    fun dismissErrors() {
        dismissedFailures.putAll(failureKeys.filterKeys { it in mutableState.value.errors })
        mutableState.update { it.copy(errors = emptyMap()) }
    }

    fun foregroundChanged(active: Boolean) {
        foreground = active
        sources.forum.foregroundChanged(active)
        if (!active) cooldownResume?.cancel()
        if (!active) ruStoreResume?.cancel()
        if (active) refresh()
    }

    @Synchronized
    fun requestUserRefresh() {
        if (refreshJob?.isActive != true) {
            dismissedFailures.clear()
            refresh(
                forceSources = setOf(UpdateSource.FORUM, UpdateSource.MARKET, UpdateSource.RUSTORE),
                userInitiated = true,
            )
        }
    }

    @Synchronized
    internal fun refresh(
        forceSources: Set<UpdateSource> = emptySet(),
        automaticBindings: Boolean = true,
        userInitiated: Boolean = false,
    ): Pair<Deferred<UpdateCheckResult>, Boolean> {
        val previous = refreshJob
        if (previous?.isActive == true && forceSources.isEmpty()) return previous to false
        previous?.cancel()
        val job =
            scope.async(start = CoroutineStart.LAZY) {
                refreshMutex.withLock {
                    UpdatePass(forceSources, automaticBindings, settings.awaitReady()).execute()
                }
            }
        refreshJob = job
        mutableState.update { it.copy(userRefreshing = userInitiated) }
        job.invokeOnCompletion {
            synchronized(this) {
                if (refreshJob === job) mutableState.update { it.copy(userRefreshing = false) }
            }
        }
        job.start()
        return job to true
    }

    /** One check's results and progress never escape into a later refresh. */
    private inner class UpdatePass(
        private val forceSources: Set<UpdateSource>,
        private val automaticBindings: Boolean,
        private val configuration: SettingsValues,
    ) {
        private var failures = 0
        private var needsUser = false

        suspend fun execute(): UpdateCheckResult {
            mutableState.update { it.copy(loading = true, errors = emptyMap(), completed = 0) }
            return try {
                scanPackages()
                val requested =
                    snapshot()
                        .filter { it.eligibleForUpdates }
                        .sortedWith(
                            compareBy<InstalledApp> { it.systemWithoutLauncher }.thenBy { it.name },
                        )
                val stores =
                    requested
                        .takeIf { configuration.isRuStoreCheckEnabled }
                        .orEmpty()
                        .filter {
                            UpdateSource.RUSTORE in forceSources ||
                                System.currentTimeMillis() - it.ruStoreDiscovery.checkedAt >
                                    AppPolicy.PLAY_CHECK_INTERVAL_MILLIS
                        }
                mutableState.update {
                    it.copy(
                        total =
                            requested.size +
                                (if (configuration.isGooglePlayCheckEnabled) 1 else 0) +
                                stores.size,
                    )
                }
                checkForum(requested)
                checkPlay()
                checkRuStore(stores)
                currentCoroutineContext().ensureActive()
                LocalUpdateNotifications.publish(context, snapshot(), configuration, users)
                when {
                    needsUser -> UpdateCheckResult.USER_VERIFICATION_REQUIRED
                    failures > 0 -> UpdateCheckResult.PARTIAL
                    else -> UpdateCheckResult.COMPLETED
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IOException) {
                failed(UpdateSource.OPERATION, failure)
                UpdateCheckResult.TEMPORARY_FAILURE
            } catch (failure: IllegalStateException) {
                failed(UpdateSource.OPERATION, failure)
                UpdateCheckResult.TEMPORARY_FAILURE
            } catch (failure: SQLException) {
                failed(UpdateSource.OPERATION, failure)
                UpdateCheckResult.TEMPORARY_FAILURE
            } catch (failure: SecurityException) {
                failed(UpdateSource.OPERATION, failure)
                UpdateCheckResult.TEMPORARY_FAILURE
            } finally {
                mutableState.update { it.copy(loading = false) }
            }
        }

        private suspend fun scanPackages() {
            try {
                packages.scan()
            } catch (failure: IOException) {
                failed(UpdateSource.PACKAGES, failure)
            } catch (failure: IllegalStateException) {
                failed(UpdateSource.PACKAGES, failure)
            } catch (failure: SQLException) {
                failed(UpdateSource.PACKAGES, failure)
            }
        }

        private suspend fun checkForum(requested: List<InstalledApp>) {
            for ((index, app) in requested.withIndex()) {
                currentCoroutineContext().ensureActive()
                if (users.installed(app.key)?.preferences?.hidden == false) {
                    if (!checkTopic(app)) break
                    currentCoroutineContext().ensureActive()
                    advance(index + 1)
                }
            }
        }

        private suspend fun checkTopic(app: InstalledApp): Boolean {
            return users.installed(app.key)?.preferences?.ignoreForum != false ||
                try {
                    when {
                        app.forum.topicId > 0 -> {
                            if (
                                UpdateSource.FORUM in forceSources ||
                                    System.currentTimeMillis() - app.forum.fetchedAt >
                                        AppPolicy.LINKED_TOPIC_INTERVAL_MILLIS
                            )
                                sources.forum.refreshLinked(
                                    app.key,
                                    app.forum.topicId,
                                )
                        }

                        automaticBindings && !app.bindingSearchCompleted ->
                            sources.forum.discover(app.key)
                    }
                    true
                } catch (failure: IOException) {
                    forumFailed(failure)
                } catch (failure: IllegalStateException) {
                    forumFailed(failure)
                } catch (failure: IllegalArgumentException) {
                    forumFailed(failure)
                }
        }

        private suspend fun forumFailed(failure: Exception): Boolean {
            failed(UpdateSource.FORUM, failure)
            val forumFailure = failure as? ForumApiException
            scheduleForumResume(forumFailure)
            return forumFailure?.let {
                it.statusCode !in
                    setOf(
                        HttpStatusCode.TooManyRequests.value,
                        HttpStatusCode.Forbidden.value,
                    ) &&
                    it.reason !in
                        setOf(
                            ForumFailure.VERIFICATION_REQUIRED,
                            ForumFailure.LOGIN_REQUIRED,
                        )
            } ?: true
        }

        private fun scheduleForumResume(failure: ForumApiException?) {
            if (
                failure?.statusCode == HttpStatusCode.TooManyRequests.value &&
                    failure.retryAt == null &&
                    foreground
            ) {
                cooldownResume?.cancel()
                cooldownResume = scope.launch {
                    delay(
                        maxOf(
                                failure.retryAfterMillis ?: 0,
                                AppPolicy.SEARCH_COOLDOWN_MILLIS,
                            )
                            .milliseconds,
                    )
                    if (foreground) refresh(forceSources = setOf(UpdateSource.FORUM))
                }
            }
        }

        private suspend fun checkPlay() {
            if (configuration.isGooglePlayCheckEnabled) {
                try {
                    if (!refreshMarket(UpdateSource.MARKET in forceSources)) failures++
                } catch (failure: IOException) {
                    failed(UpdateSource.MARKET, failure)
                } catch (failure: IllegalStateException) {
                    failed(UpdateSource.MARKET, failure)
                } catch (failure: SQLException) {
                    failed(UpdateSource.MARKET, failure)
                }
                advance()
            }
        }

        private suspend fun checkRuStore(requested: List<InstalledApp>) {
            try {
                checkRuStoreVersions(requested, sources.ruStore, settings, packages) { advance() }
            } catch (failure: SourceRateLimited) {
                failed(UpdateSource.RUSTORE, failure)
                resumeRuStoreAt(failure.retryAt)
            } catch (failure: IOException) {
                failed(UpdateSource.RUSTORE, failure)
            } catch (failure: IllegalArgumentException) {
                failed(UpdateSource.RUSTORE, failure)
            } catch (failure: IllegalStateException) {
                failed(UpdateSource.RUSTORE, failure)
            }
        }

        private fun resumeRuStoreAt(retryAt: Long) {
            if (foreground) {
                ruStoreResume?.cancel()
                ruStoreResume = scope.launch {
                    delay((retryAt - System.currentTimeMillis()).coerceAtLeast(1).milliseconds)
                    if (foreground) refresh()
                }
            }
        }

        private suspend fun failed(source: UpdateSource, error: Exception) {
            reportFailure(source, error)
            failures++
            if ((error as? ForumApiException)?.reason == ForumFailure.VERIFICATION_REQUIRED)
                needsUser = true
        }

        private fun advance(target: Int? = null) {
            mutableState.update {
                it.copy(
                    completed =
                        ((target ?: (it.completed + 1))).coerceAtMost(
                            (it.total - if (failures > 0) 1 else 0).coerceAtLeast(0),
                        ),
                )
            }
        }
    }

    suspend fun checkInBackground(): UpdateCheckResult {
        val (requested, owned) = refresh(automaticBindings = false)
        try {
            var pending = requested
            while (true) {
                val result =
                    try {
                        pending.await()
                    } catch (cancelled: CancellationException) {
                        currentCoroutineContext().ensureActive()
                        val replacement = synchronized(this) { refreshJob }
                        if (replacement == null || replacement === pending) throw cancelled
                        pending = replacement
                        continue
                    }
                val latest = synchronized(this) { refreshJob }
                if (latest == null || latest === pending) return result
                pending = latest
            }
        } catch (cancelled: CancellationException) {
            if (owned) requested.cancel()
            throw cancelled
        }
    }

    @Synchronized
    fun packageChanged(): Job {
        packageRefreshJob?.cancel()
        return scope
            .launch {
                delay(
                    AppPolicy.PACKAGE_EVENT_DEBOUNCE_MILLIS.milliseconds,
                )
                packages.scan()
            }
            .also { packageRefreshJob = it }
    }

    internal suspend fun refreshMarket(force: Boolean): Boolean {
        val requested =
            snapshot()
                .filter {
                    it.eligibleForUpdates &&
                        !it.preferences.ignoreMarket &&
                        (force ||
                            System.currentTimeMillis() - it.playDiscovery.checkedAt >
                                AppPolicy.PLAY_CHECK_INTERVAL_MILLIS)
                }
                .filter { app ->
                    users.installed(app.key)?.let {
                        !it.preferences.hidden && !it.preferences.ignoreMarket
                    } == true
                }
                .sortedBy { it.systemWithoutLauncher }
                .associateBy { it.packageName }
        if (requested.isEmpty() || !settings.current.isGooglePlayCheckEnabled) return true
        val batch = sources.marketLoad(requested.keys.toList())
        return if (!settings.current.isGooglePlayCheckEnabled) true
        else {
            if (batch.errors.isNotEmpty())
                reportFailure(
                    UpdateSource.MARKET,
                    batch.failure ?: IOException(batch.errors.joinToString("\n")),
                )
            packages.sources.applyPlay(
                requested.mapNotNull { (name, app) -> app.local?.let { name to it } }.toMap(),
                batch,
            )
            batch.errors.isEmpty()
        }
    }

    fun close() {
        scope.cancelScope()
    }
}

/** Providers needed by a complete update pass, independent of its scope and UI state. */
data class InstalledUpdateSources(
    val forum: ForumBindings,
    val marketLoad: suspend (List<String>) -> MarketBatch,
    val ruStore: RuStoreRepository,
)

internal val InstalledApp.systemWithoutLauncher: Boolean
    get() = local?.let { it.system && !it.launchable } == true
