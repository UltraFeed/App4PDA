package io.github.ultrafeed.app4pda.data.forum

import io.github.ultrafeed.app4pda.feature.gallery.ScreenshotInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup

/** Disposable canonical cache. No raw page, user panel, scripts or theme data. */
@Serializable
internal data class ForumCachedDocument(
    val topicId: Int,
    val postId: Int,
    val document: String,
    val name: String = "",
    val packages: List<String> = emptyList(),
    val version: String = "",
    val summary: String = "",
    val images: List<String> = emptyList(),
    val files: List<String> = emptyList(),
    val updatedAt: Long = 0,
    val postUpdatedAt: Long? = null,
    val category: Int = 0,
) {
    // Edit time is validation metadata, not a reason to reload an unchanged public document.
    fun bytes() =
        Json.encodeToString(copy(updatedAt = 0, postUpdatedAt = null)).toByteArray(Charsets.UTF_8)

    fun topic(): ForumInfo {
        val body = Jsoup.parseBodyFragment(document).body()
        val imageLinks = body.select("a[href]")
        val extracted = ForumTopicIcon.extract(body).screenshots
        return ForumInfo(
                ForumTopicHeader(
                    topicId,
                    name,
                    packages,
                    "",
                    version,
                    summary,
                    "",
                    category,
                    0,
                ),
                extracted.ifEmpty {
                    images.map { url ->
                        val image =
                            imageLinks
                                .firstOrNull { it.attr("href") == url }
                                ?.selectFirst("img[src]")
                        ScreenshotInfo(image?.attr("src")?.takeIf { it.isNotBlank() } ?: url, url)
                    }
                },
                document,
                files.map { ForumFileInfo(it, topicId) },
            )
            .also {
                it.shortDescription = summary
                it.postId = postId
                it.postUpdatedAtMillis = postUpdatedAt ?: 0
            }
    }

    fun post() = ForumPost(topicId, postId, document, postUpdatedAt)

    companion object {
        fun decode(bytes: ByteArray) =
            Json.decodeFromString<ForumCachedDocument>(bytes.toString(Charsets.UTF_8))

        fun from(info: ForumInfo) =
            ForumCachedDocument(
                info.topicId,
                info.postId,
                ForumDocumentParser.topicCacheContent(info.bodyHtml),
                info.name,
                info.packageNames,
                info.getVersionName(),
                info.getDescription(),
                info.screenshots.mapNotNull { it.galleryUrl },
                info.files.map { it.downloadUrl },
                info.getUpdatedAtMillis(),
                info.postUpdatedAtMillis,
                info.getType(),
            )

        fun from(post: ForumPost) =
            ForumCachedDocument(
                post.topicId,
                post.postId,
                post.document,
                postUpdatedAt = post.updatedAt,
            )
    }
}

internal suspend fun ForumCache.readTopic(topic: Int): ForumInfo? =
    getEntry(topic, -1)?.let {
        ForumCachedDocument.decode(it.content).topic().also { info ->
            info.postUpdatedAtMillis = it.postUpdatedAt ?: 0
        }
    }

internal suspend fun ForumCache.saveTopic(info: ForumInfo, revision: Long) =
    putEntry(
        info.topicId,
        -1,
        ForumCachedDocument.from(info).bytes(),
        postUpdatedAt = info.postUpdatedAtMillis,
        revision = revision,
    )

internal suspend fun ForumCache.readPost(topic: Int, post: Int): ForumPost? =
    getEntry(topic, post)?.let {
        ForumCachedDocument.decode(it.content).copy(postUpdatedAt = it.postUpdatedAt).post()
    }

internal suspend fun ForumCache.savePost(post: ForumPost, revision: Long) =
    putEntry(
        post.topicId,
        post.postId,
        ForumCachedDocument.from(post).bytes(),
        postUpdatedAt = post.updatedAt,
        revision = revision,
    )
