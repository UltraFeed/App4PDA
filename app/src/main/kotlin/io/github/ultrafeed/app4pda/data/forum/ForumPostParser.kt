package io.github.ultrafeed.app4pda.data.forum

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Extracts one post, including nested containers, without returning the surrounding page. */
internal class ForumPostParser(
    private val rule: (String) -> String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun parse(page: String, topicId: Int, postId: Int): ForumPost? =
        parse(ForumPage.parse(page, postId), topicId)

    fun parse(page: ForumPage, topicId: Int): ForumPost? {
        val body = page.post?.clone() ?: return null
        val postId = page.postId
        val dateText =
            Regex(rule("post_update"))
                .find(body.selectFirst(".edit")?.text().orEmpty())
                ?.groupValues
                ?.getOrNull(1)
        body.select("script,style,form").remove()
        val html = body.html().trim()
        return html
            .takeIf { it.isNotBlank() }
            ?.let {
                ForumPost(topicId, postId, it, dateText?.let(::parseDate))
            }
    }

    private fun parseDate(value: String): Long? {
        val format = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT)
        val today = Calendar.getInstance().apply { timeInMillis = now() }
        val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -1) }
        val normalized =
            value
                .trim()
                .replace("Сегодня", format.format(today.time))
                .replace("Вчера", format.format(yesterday.time))
        return runCatching {
            SimpleDateFormat(rule("forum_date_format"), Locale.getDefault()).parse(normalized)?.time
        }
            .getOrNull()
    }
}
