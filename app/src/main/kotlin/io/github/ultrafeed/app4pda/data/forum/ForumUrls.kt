package io.github.ultrafeed.app4pda.data.forum

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object ForumUrls {
    const val HOST: String = "4pda.to"
    const val BASE: String = "https://$HOST/forum/"
    const val INDEX: String = "${BASE}index.php"

    fun topic(topicId: Int): String = "$INDEX?showtopic=$topicId"

    fun post(topicId: Int, postId: Int): String =
        "$INDEX?showtopic=$topicId&view=findpost&p=$postId"

    fun normalize(value: String): String {
        val protocolRelative = value.startsWith("//")
        val parsed =
            (if (protocolRelative) "https:$value" else value).toHttpUrlOrNull() ?: return value
        val host = parsed.host
        val replacement =
            when {
                host == "4pda.ru" -> HOST
                host.endsWith(".4pda.ru") -> host.removeSuffix("4pda.ru") + HOST
                host == HOST || host.endsWith(".$HOST") -> host
                else -> null
            }
        return replacement?.let { parsed.newBuilder().scheme("https").host(it).build().toString() }
            ?: value
    }

    /** Only absolute forum URL tokens are rewritten; text and unrelated hosts remain untouched. */
    fun normalizeHtml(html: String): String = URL_TOKEN.replace(html) { normalize(it.value) }

    fun normalizeRule(value: String): String {
        if (
            value.startsWith("http://", true) ||
                value.startsWith(
                    "https://",
                    true,
                )
        )
            return normalize(value)
        var result = value
        for (host in listOf("4pda.ru", "4pda\\.ru")) {
            val pattern =
                Regex(
                    "(?<![A-Za-z0-9-])" + Regex.escape(host) + "(?![A-Za-z0-9.-])",
                    RegexOption.IGNORE_CASE,
                )
            result = pattern.replace(result) { host.replace("ru", "to") }
        }
        return result
    }

    private val URL_TOKEN = Regex("(?:https?:)?//[^\\s<>\"']+", RegexOption.IGNORE_CASE)
}
