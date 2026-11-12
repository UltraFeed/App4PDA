package io.github.ultrafeed.app4pda.data.forum

import io.github.ultrafeed.app4pda.core.AppPolicy.SEARCH_COOLDOWN_MILLIS
import io.ktor.http.HttpStatusCode
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

@Serializable
data class ForumSearchTopic(
    val id: Int,
    val name: String,
    val description: String,
    val mixedSection: Boolean,
    val applicationCheck: Boolean = false,
)

data class ForumTopicSearchPage(val topics: List<ForumSearchTopic>, val nextUrl: String?)

/** Actual mobile search: data-topic rows, section links and the server's own continuation URL. */
object ForumTopicSearch {
    private val androidPackage = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")
    private val searchCharset = Charset.forName("windows-1251")

    internal fun query(url: String): String? =
        url.toHttpUrl()
            .encodedQuery
            ?.split('&')
            ?.firstOrNull { it.substringBefore('=') == "query" }
            ?.substringAfter('=', "")
            ?.let { URLDecoder.decode(it, searchCharset.name()) }

    fun url(query: String, sorting: ForumSearchSort, section: Int = 0): String =
        "https://4pda.to/forum/index.php"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("act", "search")
            .addEncodedQueryParameter(
                "query",
                encodeQuery(ForumSections.searchQuery(query, section)),
            )
            .addQueryParameter("result", "topics")
            .addQueryParameter("source", if (androidPackage.matches(query.trim())) "all" else "top")
            .addQueryParameter("forums", ForumSections.scope(section).joinToString(","))
            .addQueryParameter("subforums", "0")
            .addQueryParameter("sort", sorting.parameter)
            .build()
            .toString()

    private fun encodeQuery(query: String): String {
        require(searchCharset.newEncoder().canEncode(query)) {
            "Unsupported characters in forum search"
        }
        return URLEncoder.encode(query, searchCharset.name())
    }

    fun parse(html: String, url: String, section: Int = 0): ForumTopicSearchPage {
        val page = Jsoup.parse(html, url)
        checkQuota(page.text())
        val allowed = ForumSections.scope(section)
        val topics =
            page
                .select("[data-topic]")
                .mapNotNull { row ->
                    searchTopic(row, allowed)
                }
                .distinctBy { it.id }
        val current = url.toHttpUrl()
        val offset = current.queryParameter("st")?.toIntOrNull() ?: 0
        val next =
            page
                .select("a[href]")
                .mapNotNull { it.absUrl("href").toHttpUrlOrNull() }
                .filter {
                    it.host == ForumUrls.HOST &&
                        it.encodedPath == "/forum/index.php" &&
                        it.queryParameter(
                                "act",
                            )
                            .equals("search", true) &&
                        it.queryParameter("result") == "topics" &&
                        query(
                            "$it",
                        ) ==
                            query(
                                "$current",
                            ) &&
                        (it.queryParameter("st")?.toIntOrNull() ?: 0) > offset
                }
                .minByOrNull { it.queryParameter("st")?.toIntOrNull() ?: Int.MAX_VALUE }
                ?.newBuilder()
                ?.removeAllQueryParameters("s")
                ?.build()
                ?.toString()
        return ForumTopicSearchPage(topics, next)
    }

    private fun searchTopic(
        row: Element,
        allowed: Collection<Int>,
    ): ForumSearchTopic? {
        val id = row.attr("data-topic").toIntOrNull() ?: 0
        val sectionId =
            row.selectFirst("a[href*=showforum]")
                ?.absUrl("href")
                ?.toHttpUrlOrNull()
                ?.queryParameter("showforum")
                ?.toIntOrNull() ?: 0
        if (id <= 0 || sectionId !in allowed) return null
        val title = row.selectFirst(".topic_title a[href*=showtopic]")?.text().orEmpty()
        val description =
            row.selectFirst(".topic_desc")
                ?.clone()
                ?.apply {
                    select("a[href*=showforum]").remove()
                }
                ?.text()
                ?.substringBefore("форум:")
                ?.trim()
                .orEmpty()
        return if (title.isBlank()) null
        else
            ForumSearchTopic(
                id,
                title,
                description,
                sectionId in ForumSections.androidOnlyIds,
                row.selectFirst(".topic_title")?.text()?.contains("(!)") == true,
            )
    }

    private fun checkQuota(text: String) {
        // Actual HTTP 200 error body returned by the forum, not an intermediate results page.
        if (text.contains("Вы превысили квоту на поиск."))
            throw ForumApiException(
                ForumFailure.HTTP,
                "Search quota exceeded",
                HttpStatusCode.TooManyRequests.value,
                retryAfterMillis = SEARCH_COOLDOWN_MILLIS,
                searchQuota = true,
            )
    }
}
