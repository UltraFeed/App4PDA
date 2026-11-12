package io.github.ultrafeed.app4pda.data.forum

import java.io.IOException
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Serializable
data class ForumListingPage(
    val sections: List<ForumSection>,
    val topics: List<ForumSearchTopic>,
    val nextUrl: String?,
)

/** Current IPB text pages: forumwrap contains sections; topicwrap contains immediate topics. */
object ForumListing {
    private const val PAGE_OFFSET_GROUP = 3
    private val address =
        Regex(
            "([ft])(\\d+)(?:-(\\d+))?\\.html=?",
        )

    fun url(
        section: Int,
    ) = "https://4pda.to/forum/lofiversion/index.php?f$section.html"

    private fun addressMatch(url: String): MatchResult? =
        url.toHttpUrlOrNull()
            ?.takeIf { it.host == ForumUrls.HOST && it.scheme == "https" }
            ?.let { address.matchEntire(it.encodedQuery.orEmpty()) }

    internal fun id(url: String, kind: String): Int? =
        addressMatch(url)?.takeIf { it.groupValues[1] == kind }?.groupValues?.get(2)?.toIntOrNull()

    internal fun isFirstPage(url: String, kind: String, id: Int): Boolean =
        addressMatch(url)?.let {
            it.groupValues[1] == kind &&
                it.groupValues[2].toIntOrNull() == id &&
                (it.groupValues[PAGE_OFFSET_GROUP].toIntOrNull() ?: 0) == 0
        } == true

    fun parse(html: String, section: Int, currentUrl: String): ForumListingPage {
        require(section in ForumSections.ids)
        val document = Jsoup.parse(html, currentUrl)
        if (document.selectFirst(".topicwrap, .forumwrap") == null)
            throw IOException("Unsupported forum listing")
        return ForumListingPage(
            sections(document, section),
            document.select(".topicwrap ol > li").mapNotNull(::topic).distinctBy { it.id },
            next(document, currentUrl, "f", section),
        )
    }

    private fun sections(document: Document, parent: Int): List<ForumSection> =
        document
            .select(".forumwrap li > a[href]")
            .mapNotNull { link ->
                val child = id(link.absUrl("href"), "f")
                ForumSections.all
                    .firstOrNull { it.id == child && it.parent == parent }
                    ?.copy(name = link.text())
            }
            .distinctBy { it.id }

    private fun topic(row: Element): ForumSearchTopic? {
        if (row.ownText().trim().startsWith("Перемещена:", true)) return null
        val link = row.children().firstOrNull { it.tagName() == "a" && it.hasAttr("href") }
        return link?.let {
            id(it.absUrl("href"), "t")?.let { topic ->
                ForumSearchTopic(topic, it.text(), "", false, row.ownText().contains("(!)"))
            }
        }
    }

    internal fun next(
        doc: Document,
        currentUrl: String,
        kind: String,
        id: Int,
    ): String? {
        fun offset(
            value: String,
        ): Int =
            address
                .matchEntire(
                    value.toHttpUrlOrNull()?.encodedQuery.orEmpty(),
                )
                ?.groupValues
                ?.get(
                    PAGE_OFFSET_GROUP,
                )
                ?.toIntOrNull() ?: 0

        val current =
            offset(
                currentUrl,
            )
        return doc.select(
                ".ipbpagespan a[href]",
            )
            .map {
                it.absUrl(
                    "href",
                )
            }
            .filter {
                this.id(
                    it,
                    kind,
                ) == id &&
                    offset(
                        it,
                    ) > current
            }
            .minByOrNull(
                ::offset,
            )
    }
}
