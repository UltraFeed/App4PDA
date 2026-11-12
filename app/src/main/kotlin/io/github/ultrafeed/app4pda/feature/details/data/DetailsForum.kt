package io.github.ultrafeed.app4pda.feature.details.data

import android.content.Context
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.AppPolicy
import io.github.ultrafeed.app4pda.core.network.SourceProblem
import io.github.ultrafeed.app4pda.data.database.LocalDatabase
import io.github.ultrafeed.app4pda.data.database.TopicSummary
import io.github.ultrafeed.app4pda.data.database.toApp
import io.github.ultrafeed.app4pda.data.forum.ForumApiClient
import io.github.ultrafeed.app4pda.data.forum.ForumApiException
import io.github.ultrafeed.app4pda.data.forum.ForumCache
import io.github.ultrafeed.app4pda.data.forum.ForumFailure
import io.github.ultrafeed.app4pda.data.forum.ForumInfo
import io.github.ultrafeed.app4pda.data.forum.ForumTopicResult
import io.github.ultrafeed.app4pda.data.forum.readTopic
import io.github.ultrafeed.app4pda.data.preferences.SettingsValues
import io.github.ultrafeed.app4pda.feature.installed.data.InstalledAppsStore
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/** Local basis, forum cache and revalidation share topic identity and missing-topic handling. */
class DetailsForum(
    context: Context,
    private val api: ForumApiClient,
    private val database: LocalDatabase,
    private val cache: ForumCache,
    installed: InstalledAppsStore,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val context = context.applicationContext
    val actions = DetailsActions(installed, ioDispatcher, database.users())

    internal suspend fun localPackage(pack: String) = database.users().installed(pack)?.local

    internal suspend fun loadAppSummary(topicId: Int, packageName: String): TopicSummary? {
        val row = if (topicId > 0) database.forum().snapshot(topicId) else null
        return row?.let {
            TopicSummary(
                it.topicId,
                packageName,
                it.name,
                it.description,
                it.version,
                favorite =
                    database.users().installedForTopic(it.topicId).any { app ->
                        app.preferences.favorite
                    },
            )
        }
            ?: packageName
                .takeIf { it.isNotBlank() }
                ?.let { database.users().installed(it)?.toApp() }
                ?.let {
                    // Preserve already known local metadata when the catalog has no row or the
                    // forum is unavailable.
                    TopicSummary(
                        topicId = topicId.takeIf { id -> id > 0 } ?: it.forum.topicId,
                        packageName = it.packageName,
                        name = it.name,
                        description = it.forum.description,
                        version = it.forum.versionName,
                        favorite = it.preferences.favorite,
                    )
                }
    }

    internal suspend fun localSummary(topic: Int, packageName: String): TopicSummary? =
        try {
            loadAppSummary(topic, packageName)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

    internal suspend fun cachedContent(
        topic: Int,
        packageName: String,
        app: TopicSummary?,
        values: SettingsValues,
    ): LoadedDetails? {
        val info = if (topic > 0) cache.readTopic(topic) else null
        if (info == null) return null
        return forumContent(
            context,
            DetailsContext(
                values.session.userId,
                (app ?: TopicSummary(topicId = topic, packageName = packageName)).withForumInfo(
                    info,
                ),
                info,
            ),
            packageName,
            values.session.authenticated,
            cached = true,
        )
    }

    internal data class ForumStage(
        val info: ForumInfo? = null,
        val cached: Boolean = false,
        val problem: SourceProblem? = null,
        val debug: DetailsDiagnostics = DetailsDiagnostics(),
    )

    internal suspend fun loadForum(topic: Int, previous: LoadedDetails?): ForumStage {
        if (topic <= 0) return ForumStage()
        return try {
            val result = fetchForum(topic)
            ForumStage(result.info, debug = result.detailsDiagnostics(context))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ForumApiException) {
            failedForum(
                topic,
                previous,
                failure,
                failure.reason == ForumFailure.HTTP &&
                    failure.statusCode == HttpStatusCode.NotFound.value,
            )
        } catch (failure: IOException) {
            failedForum(topic, previous, failure, false)
        } catch (failure: IllegalArgumentException) {
            failedForum(topic, previous, failure, false)
        }
    }

    private suspend fun failedForum(
        topic: Int,
        previous: LoadedDetails?,
        failure: Throwable,
        missing: Boolean,
    ): ForumStage {
        val info =
            if (missing) {
                cache.removeTopic(topic)
                null
            } else
                cache.readTopic(topic)
                    ?: previous
                        ?.context
                        ?.takeIf {
                            it.forum?.topicId == topic
                        }
                        ?.forum
        return ForumStage(
            info,
            cached = info != null,
            problem =
                SourceProblem.from(context, "4PDA", failure).let {
                    if (info != null)
                        it.copy(description = context.getString(R.string.forum_cached_check_failed))
                    else it
                },
            debug = DetailsDiagnostics(report = failure.stackTraceToString()),
        )
    }

    private suspend fun fetchForum(topic: Int): ForumTopicResult =
        withTimeoutOrNull(AppPolicy.DETAILS_FORUM_TIMEOUT_MILLIS.milliseconds) {
            val result = api.loadTopic(topic)
            currentCoroutineContext().ensureActive()
            result
        } ?: throw IOException("Forum topic request timed out")
}
