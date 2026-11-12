package io.github.ultrafeed.app4pda.data.forum

import io.github.ultrafeed.app4pda.feature.gallery.ScreenshotInfo

/** Parsed forum data. Identity and equality remain reference based. */
data class ForumTopicHeader(
    val topicId: Int,
    val name: String,
    val packageNames: List<String>,
    val extra: String,
    val version: String,
    val summary: String,
    val groupName: String,
    val category: Int,
    val debugFlags: Int,
)

class ForumInfo(
    private val header: ForumTopicHeader,
    val screenshots: List<ScreenshotInfo>,
    val bodyHtml: String,
    val files: List<ForumFileInfo>,
    val document: String =
        ForumDocumentParser.topic(
            bodyHtml,
            screenshots.flatMap { listOfNotNull(it.previewUrl, it.galleryUrl) },
        ),
) : ForumSummary {
    val topicId: Int
        get() = header.topicId

    val name: String
        get() = header.name

    val packageNames: List<String>
        get() = header.packageNames

    var shortDescription: String? = null
    var postId: Int = 0
    var postUpdatedAtMillis: Long = 0
    var sourceHtml: String = ""

    fun getPrimaryPackageName(): String? = packageNames.firstOrNull()

    override fun getId(): Int = topicId

    override fun getType(): Int = header.category

    override fun getUpdatedAtMillis(): Long = postUpdatedAtMillis

    override fun getDescription(): String = header.summary

    override fun getVersionName(): String = header.version
}
