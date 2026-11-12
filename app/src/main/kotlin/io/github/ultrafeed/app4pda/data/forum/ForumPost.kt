package io.github.ultrafeed.app4pda.data.forum

import android.webkit.MimeTypeMap
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class ForumPost(
    val topicId: Int,
    val postId: Int,
    val html: String,
    val updatedAt: Long?,
    val document: String = ForumDocumentParser.parse(html),
)

data class ForumPostAddress(val topicId: Int, val postId: Int) {
    companion object {
        fun parse(url: String, fallbackTopicId: Int = 0): ForumPostAddress? =
            ForumUrls.normalize(url.replace("&amp;", "&"))
                .toHttpUrlOrNull()
                ?.takeIf { isForumHost(it.host) && it.encodedPath == "/forum/index.php" }
                ?.let { parsed ->
                    val postId =
                        (parsed.queryParameter("p") ?: parsed.queryParameter("pid"))?.toIntOrNull()
                            ?: parsed.fragment?.removePrefix("entry")?.toIntOrNull()
                    postId
                        ?.takeIf { it > 0 }
                        ?.let { id ->
                            val topicId =
                                parsed.queryParameter("showtopic")?.toIntOrNull()?.takeIf { it > 0 }
                                    ?: fallbackTopicId
                            ForumPostAddress(topicId.coerceAtLeast(0), id)
                        }
                }
    }
}

internal fun isForumHost(host: String) = host.removePrefix("www.") == ForumUrls.HOST

sealed interface ForumPostLink {
    data class Image(val url: String) : ForumPostLink

    data class Attachment(val url: String) : ForumPostLink

    data class Post(val address: ForumPostAddress) : ForumPostLink

    data class Topic(val topicId: Int) : ForumPostLink

    data class External(val url: String) : ForumPostLink

    companion object {
        fun parse(url: String, baseUrl: String, topicId: Int): ForumPostLink? {
            val resolved =
                baseUrl.toHttpUrlOrNull()?.resolve(url.replace("&amp;", "&"))
                    ?: return url.takeIf {
                            it.startsWith("mailto:") ||
                                it.startsWith("market:") ||
                                it.startsWith("tg:")
                        }
                        ?.let(::External)
            val address = ForumUrls.normalize("$resolved").toHttpUrl()
            val normalized = "$address"
            val path = address.encodedPath.lowercase()
            val attachment = isForumHost(address.host) && path.startsWith("/forum/dl/post/")
            return when {
                listOf(".png", ".jpg", ".jpeg", ".gif", ".webp").any(path::endsWith) ->
                    Image(
                        normalized,
                    )

                attachment || isFileLink(path) -> Attachment(normalized)
                else ->
                    ForumPostAddress.parse(normalized, topicId)?.let(::Post)
                        ?: address
                            .queryParameter("showtopic")
                            ?.toIntOrNull()
                            ?.takeIf { it > 0 && isForumHost(address.host) }
                            ?.let(::Topic)
                        ?: External(
                            normalized,
                        )
            }
        }

        private fun isFileLink(path: String): Boolean {
            // These file formats are not present in every Android version's MIME registry.
            return when (val extension = path.substringAfterLast('.', "")) {
                "apk",
                "rar",
                "7z" -> true
                "html",
                "htm",
                "php" -> false
                else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) != null
            }
        }
    }
}
