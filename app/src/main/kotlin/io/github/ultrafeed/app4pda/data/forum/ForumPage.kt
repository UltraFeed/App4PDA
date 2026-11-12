package io.github.ultrafeed.app4pda.data.forum

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.Locale
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Request-local DOM. Rendering works on clones, never on the extracted source. */
internal class ForumPage private constructor(val document: Document, val post: Element?) {
    val postId: Int
        get() = post?.id()?.removePrefix("post-main-")?.toIntOrNull() ?: 0

    fun selectedHtml(title: String? = null): String? {
        val body = post?.clone()?.tagName("div") ?: return null
        val heading =
            title?.let { Element("h1").text(it).outerHtml() }
                ?: document.selectFirst("h1")?.outerHtml().orEmpty()
        return heading + body.outerHtml()
    }

    fun createdAt(): Long? {
        // Message text (including quotations and edit dates) is not its header.
        val header =
            post?.closest("[data-post]")?.clone()?.apply { select("[id^=post-main-]").remove() }
                ?: return null
        val time = header.selectFirst("time[datetime]")?.attr("datetime")
        return if (time != null) {
            try {
                OffsetDateTime.parse(time).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }
        } else header.selectFirst(".post_date")?.text()?.trim()?.let(::headerDate)
    }

    private fun headerDate(text: String): Long? =
        listOf("dd.MM.yyyy, HH:mm", "dd.MM.yy, HH:mm").firstNotNullOfOrNull { pattern ->
            val position = ParsePosition(0)
            val parsed =
                SimpleDateFormat(pattern, Locale.ROOT)
                    .apply { isLenient = false }
                    .parse(text, position)
            parsed?.takeIf { position.index == text.length }?.time
        }

    companion object {
        fun parse(html: String, postId: Int = 0): ForumPage {
            val source =
                if (html.trimStart().startsWith("<td", true)) "<table><tr>$html</tr></table>"
                else html
            val document = Jsoup.parse(source, ForumUrls.BASE)
            val post =
                if (postId > 0) document.getElementById("post-main-$postId")
                else document.selectFirst("[id^=post-main-]")
            return ForumPage(document, post)
        }
    }
}
