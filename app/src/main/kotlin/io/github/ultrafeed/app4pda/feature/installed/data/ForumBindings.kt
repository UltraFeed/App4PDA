package io.github.ultrafeed.app4pda.feature.installed.data

import io.github.ultrafeed.app4pda.core.AppPolicy
import io.github.ultrafeed.app4pda.data.database.InstalledRecord
import io.github.ultrafeed.app4pda.data.database.LocalDatabase
import io.github.ultrafeed.app4pda.data.database.toApp
import io.github.ultrafeed.app4pda.data.forum.ForumApiException
import io.github.ultrafeed.app4pda.data.forum.ForumBindingResult
import io.github.ultrafeed.app4pda.data.forum.ForumFailure
import io.github.ultrafeed.app4pda.data.forum.ForumInfo
import io.github.ultrafeed.app4pda.data.forum.ForumTopicSearchPage
import io.github.ultrafeed.app4pda.data.forum.resolveForumPackage
import io.ktor.http.HttpStatusCode
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Exact first-message evidence; incomplete search results never establish uniqueness. */
class ForumBindings
internal constructor(
    private val database: LocalDatabase,
    private val records: InstalledAppsStore,
    private val topicLoad: suspend (Int) -> ForumInfo,
    private val search: suspend (String, String?) -> ForumTopicSearchPage,
) {
    private val pending = ConcurrentHashMap<String, Job>()

    @Volatile private var foreground = false

    fun foregroundChanged(active: Boolean) {
        foreground = active
        if (!active) pending.values.forEach { it.cancel() }
    }

    fun cancel(key: String) {
        pending[key]?.cancel()
    }

    suspend fun discover(key: String) {
        try {
            coroutineScope {
                val job = checkNotNull(currentCoroutineContext()[Job])
                if (pending.putIfAbsent(key, job) != null) return@coroutineScope
                try {
                    discoverNow(key)
                } finally {
                    pending.remove(key, job)
                }
            }
        } catch (cancelled: CancellationException) {
            // Cancelling this binding must not cancel the caller's remaining applications.
            // External cancellation still propagates through the caller's Job.
            if (!currentCoroutineContext().isActive) throw cancelled
        }
    }

    suspend fun refreshLinked(key: String, topic: Int) {
        val expected = database.users().installed(key) ?: return
        if (expected.forum.topicId != topic || expected.toApp().hidden) return
        try {
            records.sources.applyForum(expected, load(topic), confirmPackage = false)
        } catch (failure: ForumApiException) {
            if (
                failure.reason != ForumFailure.HTTP ||
                    failure.statusCode != HttpStatusCode.NotFound.value
            )
                throw failure
        }
    }

    private suspend fun discoverNow(key: String) {
        val expected =
            database.users().installed(key)?.takeIf {
                it.local != null && !it.bindingSearchCompleted && it.forum.topicId == 0
            } ?: return
        if (!foreground || expected.preferences.hidden || expected.preferences.ignoreForum) return
        val candidate = resolve(expected)
        currentCoroutineContext().ensureActive()
        when (candidate) {
            is ForumBindingResult.Unique -> {
                records.sources.applyForum(expected, candidate.info, confirmPackage = true)
            }

            ForumBindingResult.NotFound,
            ForumBindingResult.Ambiguous -> records.sources.markBindingSearch(expected)

            ForumBindingResult.Interrupted -> Unit
        }
    }

    private suspend fun resolve(expected: InstalledRecord): ForumBindingResult {
        val owner = checkNotNull(currentCoroutineContext()[Job])
        val monitor =
            CoroutineScope(currentCoroutineContext()).launch {
                database.users().observeInstalledRecord(expected.key).collect {
                    if (!it.matchesBinding(expected, foreground)) owner.cancel()
                }
            }
        return try {
            resolveForumPackage(
                expected.packageName,
                search,
                ::load,
            ) {
                database.users().installed(expected.key).matchesBinding(expected, foreground)
            }
        } finally {
            monitor.cancelAndJoin()
        }
    }

    private suspend fun load(topic: Int) =
        withTimeout(
            AppPolicy.BINDING_TOPIC_TIMEOUT_MILLIS.milliseconds,
        ) {
            topicLoad(topic).also {
                currentCoroutineContext().ensureActive()
                check(it.topicId == topic) { "Forum response does not match requested topic" }
            }
        }
}

private fun InstalledRecord?.matchesBinding(
    expected: InstalledRecord,
    foreground: Boolean,
) =
    foreground &&
        this?.toApp()?.hidden == false &&
        !this.preferences.ignoreForum &&
        this.bindingSearchCompleted == expected.bindingSearchCompleted &&
        this.bindingRevision == expected.bindingRevision &&
        this.forum == expected.forum &&
        this.local == expected.local
