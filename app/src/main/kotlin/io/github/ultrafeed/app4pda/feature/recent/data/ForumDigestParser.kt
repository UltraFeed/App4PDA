package io.github.ultrafeed.app4pda.feature.recent.data

import io.github.ultrafeed.app4pda.data.forum.ForumListing
import io.github.ultrafeed.app4pda.data.forum.ForumUrls
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

data class DigestPage(
    val groups: List<DigestGroup>,
    val nextUrl: String?,
    val issues: List<DigestIssue>,
)

/** Headings are forum data; introductory posts without a publication period are ignored. */
object ForumDigestParser {
    private const val TOPIC_ID = 127361
    const val URL = "https://4pda.to/forum/lofiversion/index.php?t$TOPIC_ID.html"
    private val period =
        Regex("\\d{1,2}\\s+\\p{L}+\\s+\\d{4}\\s*[-–—]\\s*\\d{1,2}\\s+\\p{L}+\\s+\\d{4}")

    fun parse(html: String, url: String): DigestPage {
        val document = Jsoup.parse(html, url)
        val posts = document.select(".postcontent")
        check(posts.isNotEmpty()) { "Unsupported digest page" }
        val parsed =
            posts
                .filter { post -> post.allElements.any { period.matches(it.ownText().trim()) } }
                .map { DigestPost(period).parse(it) }
        return DigestPage(
            parsed.flatMap { it.groups },
            ForumListing.next(document, url, "t", TOPIC_ID),
            parsed.flatMap { it.issues },
        )
    }
}

private class DigestPost(private val periodPattern: Regex) {
    private var period = ""
    private var category = ""
    private var kind: DigestKind? = null
    private val sections = linkedMapOf<String, MutableSet<DigestKind>>()
    private val entries =
        linkedMapOf<Triple<String, String, DigestKind>, MutableList<DigestEntry>>()

    fun parse(post: Element): DigestSnapshot {
        post.allElements.forEach { node ->
            if (node.closest("li") == null) heading(node)
            if (node.tagName() == "li") entry(node)
        }
        return DigestSnapshot(
            groups =
                entries.map { (key, rows) -> DigestGroup(key.first, key.second, key.third, rows) },
            issues = sections.map { (range, kinds) -> DigestIssue(range, kinds) },
        )
    }

    private fun heading(node: Element) {
        val text = node.ownText().trim()
        when {
            periodPattern.matches(text) -> {
                period = text
                kind = null
                category = ""
                sections.getOrPut(period) { mutableSetOf() }
            }
            text.startsWith("Новые программы, ранее не публиковавшиеся", true) ->
                section(DigestKind.NEW)
            text.startsWith("Обновление ранее опубликованных", true) -> section(DigestKind.UPDATED)
            node.tagName() == "strong" && text.isNotBlank() && kind != null -> category = text
        }
    }

    private fun section(value: DigestKind) {
        if (period.isBlank()) return
        kind = value
        category = ""
        sections.getValue(period).add(value)
    }

    private fun entry(node: Element) {
        val link = node.selectFirst("strong > a[href]") ?: return
        val url = link.absUrl("href").toHttpUrlOrNull()
        val topic =
            url?.takeIf { it.host == ForumUrls.HOST && it.scheme == "https" }
                ?.queryParameter("showtopic")
                ?.toIntOrNull()
        if (topic != null) {
            val type = checkNotNull(kind) { "Digest entry without a section heading" }
            check(period.isNotBlank() && category.isNotBlank()) {
                "Digest entry without period/category"
            }
            entries
                .getOrPut(Triple(period, category, type)) { mutableListOf() }
                .add(
                    DigestEntry(
                        topic,
                        link.text(),
                        node.text().removePrefix(link.text()).trim().removePrefix("-").trim(),
                    ),
                )
        }
    }
}
