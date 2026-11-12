package io.github.ultrafeed.app4pda.feature.details.data

import android.content.Context
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.AppPolicy
import io.github.ultrafeed.app4pda.core.network.SourceProblem
import io.github.ultrafeed.app4pda.core.packages.LocalPackage
import io.github.ultrafeed.app4pda.data.database.TopicSummary
import io.github.ultrafeed.app4pda.data.forum.ForumInfo
import io.github.ultrafeed.app4pda.data.googleplay.GooglePlayRepository
import io.github.ultrafeed.app4pda.data.googleplay.PlayAppDetails
import io.github.ultrafeed.app4pda.data.googleplay.PlaySourceException
import io.github.ultrafeed.app4pda.data.preferences.AppSettings
import io.github.ultrafeed.app4pda.data.preferences.SettingsValues
import io.github.ultrafeed.app4pda.data.rustore.RuStoreDetails
import io.github.ultrafeed.app4pda.data.rustore.RuStoreRepository
import io.github.ultrafeed.app4pda.data.rustore.RuStoreUnavailable
import io.github.ultrafeed.app4pda.feature.details.DetailsSection
import io.github.ultrafeed.app4pda.feature.details.DetailsTab
import io.github.ultrafeed.app4pda.feature.downloads.data.DownloadsStorage
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AppDetailsRepository(
    context: Context,
    private val settings: AppSettings,
    private val forumSource: DetailsForum,
    private val market: GooglePlayRepository,
    private val storage: DownloadsStorage,
    private val ruStore: RuStoreRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val context = context.applicationContext

    val actions: DetailsActions
        get() = forumSource.actions

    fun problem(source: String, failure: Throwable): SourceProblem =
        SourceProblem.from(context, source, failure)

    suspend fun load(
        topicId: Int,
        packageName: String,
        previous: LoadedDetails?,
        onContent: suspend (LoadedDetails) -> Unit,
        requestedSource: DetailsTab? = null,
        refreshStores: Boolean = false,
    ): LoadedDetails =
        withContext(ioDispatcher) {
            val values = settings.awaitReady()
            val local =
                packageName.takeIf(String::isNotBlank)?.let {
                    forumSource.localPackage(it)
                }
            val summary = forumSource.localSummary(topicId, packageName)
            DetailsLoad(
                    topicId,
                    packageName,
                    previous,
                    onContent,
                    values,
                    LocalDetailsBase(local, summary, requestedSource, refreshStores),
                )
                .run()
        }

    /** Request-local stages; no result or publication state is retained by the repository. */
    private inner class DetailsLoad(
        topicId: Int,
        private val packageName: String,
        private val previous: LoadedDetails?,
        private val onContent: suspend (LoadedDetails) -> Unit,
        private val values: SettingsValues,
        base: LocalDetailsBase,
    ) {
        private val local = base.local
        private val requestedSource = base.requestedSource
        private val refreshStores = base.refreshStores
        private val app = base.summary
        private val topic = topicId.takeIf { it > 0 } ?: app?.topicId ?: 0
        private val language = playLanguage()

        suspend fun run(): LoadedDetails {
            require(topic > 0 || packageName.isNotBlank())
            val cached = publishCache()
            if (cached.data.packageName.isNotBlank())
                return refreshSources(
                    cached,
                    refreshForum = requestedSource == null || requestedSource == DetailsTab.FORUM,
                )
            val content = forumStage()
            val pages = readPages(content.data.packageName)
            val base =
                content
                    .withStorePages(
                        context,
                        pages.play,
                        pages.store,
                        local?.versionCode ?: 0,
                        language,
                    )
                    .withPreviousStores(previous, language)
            return refreshSources(base)
        }

        private fun display(content: LoadedDetails): LoadedDetails =
            if (local == null) content
            else
                content.copy(
                    data = content.data.copy(title = local.name, version = local.versionName),
                )

        private suspend fun publish(content: LoadedDetails) {
            currentCoroutineContext().ensureActive()
            if (marketLocaleChanged(language)) throw CancellationException("App language changed")
            onContent(display(content))
        }

        private suspend fun readPages(pack: String) =
            StorePages(
                market.cachedDetails(pack, language),
                ruStore.cached(pack),
            )

        private suspend fun publishCache(): LoadedDetails {
            val cached = forumSource.cachedContent(topic, packageName, app, values)
            val pack = selectPackage(packageName, app, cached?.context?.forum)
            val pages = readPages(pack)
            var initial =
                cached
                    ?: forumContent(
                            context,
                            DetailsContext(
                                values.session.userId,
                                app ?: TopicSummary(topicId = topic, packageName = pack),
                                null,
                            ),
                            pack,
                            values.session.authenticated,
                            cached = true,
                        )
                        .let {
                            it.copy(data = it.data.copy(forum = it.data.forum.copy(error = false)))
                        }
            initial =
                initial
                    .withStorePages(
                        context,
                        pages.play,
                        pages.store,
                        local?.versionCode ?: 0,
                        language,
                    )
                    .withPreviousStores(previous, language)
            initial = withForumFiles(initial)
            if (cached != null || pages.play != null || pages.store != null) publish(initial)
            return initial
        }

        private suspend fun forumStage(): LoadedDetails {
            val forum = forumSource.loadForum(topic, previous)
            val summary =
                forum.info?.let { (app ?: TopicSummary(topicId = topic)).withForumInfo(it) } ?: app
            val pack = selectPackage(packageName, summary, forum.info)
            val actionContext =
                DetailsContext(
                    values.session.userId,
                    (summary ?: TopicSummary(topicId = topic, name = pack)).copy(
                        packageName = pack,
                    ),
                    forum.info,
                )
            val loaded =
                forumContent(
                    context,
                    actionContext,
                    pack,
                    values.session.authenticated,
                    forum.cached,
                    forum.problem,
                )
            return withForumFiles(
                loaded.copy(
                    data =
                        loaded.data.copy(
                            downloadDirectoryReady = storage.ensureDirectory(),
                            debugReport = forum.debug.report,
                            debugSteps = forum.debug.steps,
                            debugHtml = forum.debug.html,
                        ),
                ),
            )
        }

        private fun withForumFiles(loaded: LoadedDetails): LoadedDetails =
            loaded.copy(
                data =
                    loaded.data.copy(
                        files =
                            forumFiles(
                                context,
                                loaded.context.forum,
                                loaded.data.packageName,
                                context.installedVersionCode(loaded.data.packageName),
                                values.checksVersionCode,
                            ) +
                                loaded.data.files.filter {
                                    it.market || it.ruStore || it.id.startsWith("external:")
                                },
                    ),
            )

        private suspend fun refreshSources(
            sourceBase: LoadedDetails,
            refreshForum: Boolean = false,
        ): LoadedDetails {
            var completed = sourceBase
            var redirected: LoadedDetails? = null
            val pack = sourceBase.data.packageName
            publish(completed)
            val publication = Mutex()
            coroutineScope {
                val playJob = launch {
                    if (
                        !values.isGooglePlayCheckEnabled &&
                            requestedSource != DetailsTab.GOOGLE_PLAY
                    )
                        return@launch
                    val play = completePlay(sourceBase, pack)
                    publication.withLock {
                        completed = completed.withPlay(play)
                        publish(completed)
                    }
                }
                val ruStoreJob = launch {
                    if (!values.isRuStoreCheckEnabled && requestedSource != DetailsTab.RUSTORE)
                        return@launch
                    val store =
                        completeRuStore(sourceBase, pack, sourceBase.data.ruStore, refreshStores)
                    publication.withLock {
                        completed = completed.withRuStore(store)
                        publish(completed)
                    }
                }
                if (refreshForum)
                    launch {
                        val forum = forumStage()
                        publication.withLock {
                            if (forum.data.packageName != pack) {
                                redirected = forum
                                playJob.cancel()
                                ruStoreJob.cancel()
                            } else {
                                completed = forum.withPlay(completed).withRuStore(completed)
                                publish(completed)
                            }
                        }
                    }
            }
            currentCoroutineContext().ensureActive()
            redirected?.let { content ->
                val pages = readPages(content.data.packageName)
                return refreshSources(
                    content.withStorePages(
                        context,
                        pages.play,
                        pages.store,
                        local?.versionCode ?: 0,
                        language,
                    ),
                )
            }
            return display(completed)
        }

        private fun LoadedDetails.withPlay(play: LoadedDetails): LoadedDetails =
            copy(
                data =
                    data.copy(
                        market = play.data.market,
                        files =
                            data.files.filterNot { it.market } +
                                play.data.files.filter { it.market },
                    ),
                context =
                    context.copy(
                        playPackage = play.context.playPackage,
                        playLanguage = play.context.playLanguage,
                    ),
            )

        private fun LoadedDetails.withRuStore(store: LoadedDetails): LoadedDetails =
            copy(
                data =
                    data.copy(
                        ruStore = store.data.ruStore,
                        files =
                            data.files.filterNot { it.ruStore } +
                                store.data.files.filter { it.ruStore },
                    ),
            )
    }

    private data class StorePages(
        val play: PlayAppDetails?,
        val store: RuStoreDetails?,
    )

    internal fun selectPackage(
        requested: String,
        summary: TopicSummary?,
        forum: ForumInfo?,
    ): String {
        val candidates = forum?.packageNames.orEmpty()
        return if (candidates.isNotEmpty())
            requested.takeIf { it in candidates } ?: candidates.first()
        else requested.ifBlank { summary?.packageName.orEmpty() }
    }

    internal suspend fun completePlay(
        loaded: LoadedDetails,
        packageName: String,
    ): LoadedDetails {
        val installedVersion = context.installedVersionCode(packageName)
        val language = market.requestLocale().toLanguageTag()
        var market: DetailsSection? =
            loaded.data.market.takeIf { loaded.context.playLanguage == language }
        var playPackage: String? = loaded.context.playPackage.takeIf { market != null }
        val resultFiles = loaded.data.files.filter { !it.market || market != null }.toMutableList()
        if (packageName.isNotBlank()) {
            try {
                val info = fetchMarket(packageName)
                if (marketLocaleChanged(language))
                    throw CancellationException("App language changed")
                playPackage = info.release.packageName
                market = playSection(context, info)
                resultFiles.removeAll { it.market }
                playFile(context, info, installedVersion)?.let(resultFiles::add)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: PlaySourceException.NotFound) {
                resultFiles.removeAll { it.market }
                playPackage = null
                market = null
            } catch (failure: IOException) {
                market =
                    context.failedStoreSection(
                        context.getString(R.string.source_google_play),
                        market,
                        failure,
                    )
            } catch (failure: IllegalArgumentException) {
                market =
                    context.failedStoreSection(
                        context.getString(R.string.source_google_play),
                        market,
                        failure,
                    )
            }
        }

        return loaded.copy(
            data = loaded.data.copy(market = market, files = resultFiles),
            context = loaded.context.copy(playPackage = playPackage, playLanguage = language),
        )
    }

    fun playLanguage(): String = market.requestLocale().toLanguageTag()

    internal fun marketLocaleChanged(language: String): Boolean = playLanguage() != language

    suspend fun refreshPlay(loaded: LoadedDetails): LoadedDetails =
        settings.awaitReady().let { values ->
            if (values.isGooglePlayCheckEnabled) completePlay(loaded, loaded.data.packageName)
            else loaded
        }

    internal suspend fun completeRuStore(
        loaded: LoadedDetails,
        pack: String,
        cached: DetailsSection?,
        refresh: Boolean,
    ): LoadedDetails {
        if (pack.isBlank()) return loaded
        return try {
            val info = ruStore.details(pack, refresh)
            loaded.copy(
                data =
                    loaded.data.copy(
                        ruStore = ruStoreSection(context, info),
                        files =
                            loaded.data.files.filterNot { it.ruStore } + ruStoreFile(context, info),
                    ),
            )
        } catch (_: RuStoreUnavailable) {
            loaded.copy(
                data =
                    loaded.data.copy(
                        ruStore = null,
                        files = loaded.data.files.filterNot { it.ruStore },
                    ),
            )
        } catch (failure: IOException) {
            loaded.copy(
                data =
                    loaded.data.copy(
                        ruStore =
                            context.failedStoreSection(
                                context.getString(R.string.source_rustore),
                                cached,
                                failure,
                            ),
                    ),
            )
        } catch (failure: IllegalArgumentException) {
            loaded.copy(
                data =
                    loaded.data.copy(
                        ruStore =
                            context.failedStoreSection(
                                context.getString(R.string.source_rustore),
                                cached,
                                failure,
                            ),
                    ),
            )
        }
    }

    private suspend fun fetchMarket(pack: String): PlayAppDetails =
        withTimeoutOrNull(
            AppPolicy.DETAILS_PLAY_TIMEOUT_MILLIS.milliseconds,
        ) {
            market.details(pack)
        } ?: throw IOException("Google Play request timed out")
}

private data class LocalDetailsBase(
    val local: LocalPackage?,
    val summary: TopicSummary?,
    val requestedSource: DetailsTab?,
    val refreshStores: Boolean,
)
