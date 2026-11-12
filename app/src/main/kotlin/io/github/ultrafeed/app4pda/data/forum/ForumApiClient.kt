package io.github.ultrafeed.app4pda.data.forum

import android.app.Application
import android.content.Context
import android.util.Log
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.AppPolicy.SEARCH_COOLDOWN_MILLIS
import io.github.ultrafeed.app4pda.core.network.ForumHttpResponse
import io.github.ultrafeed.app4pda.core.network.HttpStatusFailure
import io.github.ultrafeed.app4pda.core.network.readForumResponse
import io.github.ultrafeed.app4pda.core.network.retryAfterMillis
import io.github.ultrafeed.app4pda.data.database.ForumDao
import io.github.ultrafeed.app4pda.data.database.ForumSnapshot
import io.github.ultrafeed.app4pda.data.preferences.AppSettings
import io.github.ultrafeed.app4pda.data.preferences.SettingsValues
import io.github.ultrafeed.app4pda.data.preferences.forumAccounts
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import java.io.IOException
import java.nio.charset.Charset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup

enum class ForumFailure {
    NETWORK,
    HTTP,
    EMPTY_CONTENT,
    INVALID_RESPONSE,
    PARSING,
    VERIFICATION_REQUIRED,
    LOGIN_REQUIRED,
}

class ForumApiException(
    val reason: ForumFailure,
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
    val retryAfterMillis: Long? = null,
    val searchQuota: Boolean = false,
    val retryAt: Long? = null,
) : IOException(message, cause), HttpStatusFailure {
    private companion object {
        private const val serialVersionUID = 1L
    }

    val topicMissing: Boolean
        get() = reason == ForumFailure.HTTP && statusCode == HttpStatusCode.NotFound.value

    override val httpStatus: Int?
        get() = statusCode
}

data class ForumTopicResult(
    val info: ForumInfo,
    val diagnostics: List<ForumParseDiagnostic>,
    val sourceHtml: String,
    val createdAt: Long? = null,
)

sealed interface ForumTopicPreviewResult {
    data class Found(val preview: ForumTopicPreview) : ForumTopicPreviewResult

    data object Missing : ForumTopicPreviewResult
}

private data class FilteredTopic(
    val html: String,
    val diagnostics: List<ForumParseDiagnostic>,
    val sourceHtml: String,
    val createdAt: Long? = null,
    val parsedPage: ForumPage? = null,
)

private class ForumSearchSessionChanged : IOException() {
    private companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Shared cancellable transport. Only application context and immutable request snapshots are
 * retained.
 */
class ForumApiClient
internal constructor(
    context: Context,
    private val settings: AppSettings,
    private val forumData: ForumDao,
    private val cache: ForumCache,
    private val transports: ForumTransports,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val context = context.applicationContext as Application

    suspend fun loadTopic(
        topicId: Int,
        userInitiated: Boolean = true,
    ): ForumTopicResult =
        withContext(ioDispatcher) {
            val snapshot = settings.awaitReady()
            val revision = cache.revision()
            val page =
                try {
                    loadFilteredTopic(topicId, 0, snapshot, userInitiated)
                } catch (failure: ForumApiException) {
                    if (failure.topicMissing) {
                        cache.removeTopic(topicId)
                        forumData.deleteSnapshot(topicId)
                    }
                    throw failure
                }
            val parsed =
                ForumTopicParser.fromSettings(snapshot)
                    .parse(
                        topicId,
                        page.html,
                        page.parsedPage ?: ForumPage.parse(page.html),
                    )
            val info =
                parsed.info ?: throw ForumApiException(ForumFailure.PARSING, "Empty forum response")
            ensureActive()
            cache.saveTopic(info, revision)
            val created = saveSnapshot(info, page.createdAt)
            ForumTopicResult(
                info,
                page.diagnostics + parsed.diagnostics,
                page.sourceHtml,
                created,
            )
        }

    private suspend fun saveSnapshot(info: ForumInfo, createdAt: Long?): Long? {
        val old = forumData.snapshot(info.topicId)
        val updated =
            info.getUpdatedAtMillis().takeIf { it > 0 && info.getVersionName().isNotBlank() }
        val created = createdAt ?: old?.createdAt
        forumData.saveSnapshot(
            ForumSnapshot(
                info.topicId,
                "",
                info.getVersionName(),
                updated ?: old?.updatedAt,
                created,
                System.currentTimeMillis(),
                info.name,
                info.getDescription(),
                info.packageNames.joinToString(";"),
            ),
        )
        return created
    }

    suspend fun loadListing(url: String, userInitiated: Boolean = true): String =
        withContext(ioDispatcher) {
            transports.requests.execute(userInitiated) {
                decodePage(
                    readForumPageResponse(url, false, settings.awaitReady(), emptyMap()),
                )
            }
        }

    suspend fun topicPreview(
        topicId: Int,
        refresh: Boolean = false,
    ): ForumTopicPreviewResult =
        withContext(ioDispatcher) {
            val cached = cache.readTopic(topicId)
            val info =
                try {
                    if (refresh || cached == null) loadTopic(topicId, userInitiated = false).info
                    else cached
                } catch (failure: ForumApiException) {
                    if (!failure.topicMissing) throw failure
                    cache.removeTopic(topicId)
                    forumData.deleteSnapshot(topicId)
                    return@withContext ForumTopicPreviewResult.Missing
                }
            ForumTopicPreviewResult.Found(
                ForumTopicPreview(
                    ForumTopicIcon.first(info.bodyHtml),
                    info.packageNames.isNotEmpty() ||
                        (info.getVersionName().isNotBlank() &&
                            Jsoup.parseBodyFragment(info.bodyHtml).select("a[href]").any {
                                it.attr("href").substringBefore('?').endsWith(".apk", true)
                            }),
                    version = info.getVersionName(),
                    description = info.shortDescription.orEmpty(),
                    updatedAt = info.getUpdatedAtMillis(),
                    application =
                        info.getVersionName().isNotBlank() &&
                            info.shortDescription?.isNotBlank() == true,
                ),
            )
        }

    suspend fun searchTopics(
        query: String,
        sorting: ForumSearchSort,
        nextUrl: String?,
        section: Int = 0,
        userInitiated: Boolean = true,
    ): ForumTopicSearchPage =
        withContext(ioDispatcher) {
            val url = nextUrl ?: ForumTopicSearch.url(query, sorting, section)
            validateSearchUrl(url, query, section)
            var result: ForumTopicSearchPage? = null
            while (result == null) {
                val snapshot = settings.accounts.forSearch()
                try {
                    requireSearchSession(snapshot, settings.current)
                    result =
                        transports.requests.execute(userInitiated) {
                            val account =
                                settings.current.forumAccounts().firstOrNull {
                                    it.id == snapshot.session.userId
                                }
                            val sessionChanged =
                                settings.current.session.userId != snapshot.session.userId
                            val obsolete =
                                sessionChanged || account?.cookies != snapshot.session.cookies
                            if (
                                account == null ||
                                    obsolete ||
                                    account.searchAvailableAt > System.currentTimeMillis()
                            )
                                throw ForumSearchSessionChanged()
                            val response =
                                readForumPageResponse(url, true, snapshot, emptyMap(), true)
                            ForumTopicSearch.parse(decodePage(response), url, section)
                        }
                } catch (_: ForumSearchSessionChanged) {
                    continue
                } catch (failure: ForumApiException) {
                    if (!failure.searchQuota) throw failure
                    settings.accounts.postponeSearch(
                        snapshot.session.userId,
                        System.currentTimeMillis() +
                            maxOf(
                                failure.retryAfterMillis ?: 0,
                                SEARCH_COOLDOWN_MILLIS,
                            ),
                    )
                }
            }
            result
        }

    suspend fun loadTopicHtml(
        topicId: Int,
        postId: Int = 0,
    ): String = withContext(ioDispatcher) { loadFilteredTopic(topicId, postId).html }

    private suspend fun loadFilteredTopic(
        topicId: Int,
        postId: Int,
        rules: SettingsValues = settings.current,
        userInitiated: Boolean = true,
    ): FilteredTopic {
        val url =
            when {
                postId > 0 ->
                    rules.rules["forum_url_find_post"]
                        .replace("%topic_id%", "$topicId")
                        .replace("%post_id%", "$postId")

                postId == -1 ->
                    rules.rules["forum_url_get_new_post"].replace("%topic_id%", "$topicId")

                else -> rules.rules["forum_url_show_topic"].replace("%topic_id%", "$topicId")
            }
        val response =
            transports.requests.execute(userInitiated) {
                readForumPageResponse(url, topic = true, snapshot = rules, validators = emptyMap())
            }
        var resolvedPost = postId
        if (resolvedPost == -1)
            Regex(rules.rules["post_id_from_url"]).find(response.url.toString())?.let {
                resolvedPost = it.groupValues[1].toIntOrNull() ?: 0
            }
        currentCoroutineContext().ensureActive()
        try {
            val source = decodePage(response)
            val page = ForumPage.parse(source, resolvedPost.coerceAtLeast(0))
            val displayed =
                page.selectedHtml()
                    ?: throw ForumApiException(
                        ForumFailure.EMPTY_CONTENT,
                        context.getString(R.string.error_empty_content),
                    )
            return FilteredTopic(
                displayed,
                emptyList(),
                displayed,
                page.createdAt(),
                page,
            )
        } catch (error: IllegalArgumentException) {
            throw ForumApiException(
                ForumFailure.PARSING,
                error.message ?: "Invalid forum HTML",
                cause = error,
            )
        }
    }

    private suspend fun readForumPageResponse(
        url: String,
        topic: Boolean,
        snapshot: SettingsValues,
        validators: Map<String, String>,
        accountRequired: Boolean = false,
    ): ForumHttpResponse {
        if (!transports.connected())
            throw ForumApiException(
                ForumFailure.NETWORK,
                context.getString(R.string.error_no_connection),
            )
        val cookies = if (accountRequired) forumSessionCookies(snapshot.session.cookies) else ""
        val headers = buildMap {
            putAll(validators)
            put("User-Agent", forumUserAgent(context))
            if (cookies.isNotEmpty()) put("Cookie", cookies)
            if (topic) {
                put("Cache-Control", "no-cache")
                put("Pragma", "no-cache")
            }
        }
        val target = ForumUrls.normalize(url)
        require(target.toHttpUrl().let { it.scheme == "https" && it.host == ForumUrls.HOST })
        val response =
            transports.pages.fetchForumResponse(target, headers) { hop, session ->
                forumHttpEvent(context, target, hop, session)
            }
        currentCoroutineContext().ensureActive()
        context.validateForumResponse(response, validators)
        return response
    }

    private fun decodePage(response: ForumHttpResponse): String {
        val charset =
            Regex(
                    "charset=([a-z\\d\\-]*)",
                    RegexOption.IGNORE_CASE,
                )
                .find(response.headers["Content-Type"].orEmpty())
                ?.groupValues
                ?.get(1)
                ?.takeIf { runCatching { Charset.isSupported(it) }.getOrDefault(false) }
                ?.let(Charset::forName) ?: Charsets.UTF_8
        return ForumUrls.normalizeHtml(String(response.bytes, charset))
    }
}

internal fun validateSearchUrl(url: String, query: String, section: Int) {
    val target = url.toHttpUrl()
    require(
        target.host == ForumUrls.HOST &&
            target.encodedPath == "/forum/index.php" &&
            target
                .queryParameter(
                    "act",
                )
                .equals("search", true) &&
            target.queryParameter("forums")?.split(',')?.mapNotNull(String::toIntOrNull)?.toSet() ==
                ForumSections.scope(section) &&
            ForumTopicSearch.query(url) ==
                ForumSections.searchQuery(
                    query,
                    section,
                ),
    )
}

internal fun requireSearchSession(
    snapshot: SettingsValues,
    current: SettingsValues,
) {
    if (!snapshot.session.authenticated) {
        val delay =
            current
                .forumAccounts()
                .map { it.searchAvailableAt - System.currentTimeMillis() }
                .filter { it > 0 }
                .minOrNull()
        throw ForumApiException(
            if (delay == null) ForumFailure.LOGIN_REQUIRED else ForumFailure.HTTP,
            if (delay == null) "Forum search requires an available account"
            else "Forum search quota",
            statusCode = if (delay == null) 401 else 429,
            retryAfterMillis = delay,
        )
    }
}

internal suspend fun HttpClient.fetchForumResponse(
    url: String,
    headers: Map<String, String>,
    onResponse: (ForumHttpResponse, Boolean) -> Unit,
): ForumHttpResponse {
    return try {
        readForumResponse(url, headers = headers, onResponse = onResponse)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: IOException) {
        throw ForumApiException(
            ForumFailure.NETWORK,
            error.message ?: "Network request failed",
            cause = error,
        )
    }
}

internal fun Context.validateForumResponse(
    response: ForumHttpResponse,
    validators: Map<String, String> = emptyMap(),
) {
    val challenge =
        isForumChallenge(
            response.headers["Cf-Mitigated"],
            String(response.bytes, Charsets.ISO_8859_1),
        )
    Log.d("ForumAccess", "page status=${response.code} challenge=$challenge")
    if (challenge)
        throw ForumApiException(
            ForumFailure.VERIFICATION_REQUIRED,
            getString(R.string.forum_verification_required),
            response.code,
            retryAfterMillis = retryAfterMillis(response.headers["Retry-After"]),
        )
    if (
        response.code != HttpStatusCode.OK.value &&
            !(response.code == HttpStatusCode.NotModified.value && validators.isNotEmpty())
    )
        throw ForumApiException(
            ForumFailure.HTTP,
            "(${response.code}) ${response.message}",
            response.code,
            retryAfterMillis = retryAfterMillis(response.headers["Retry-After"]),
        )
}

/** Public pages are anonymous; authenticated search shares the same request scheduler. */
data class ForumTransports(
    val pages: HttpClient,
    val requests: ForumRequests,
    val connected: () -> Boolean,
)
