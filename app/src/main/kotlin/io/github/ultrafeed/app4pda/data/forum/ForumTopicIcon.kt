package io.github.ultrafeed.app4pda.data.forum

import io.github.ultrafeed.app4pda.feature.gallery.ScreenshotInfo
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

@Serializable
data class ForumTopicPreview(
    val icon: String?,
    val android: Boolean,
    val version: String = "",
    val description: String = "",
    val updatedAt: Long = 0,
    val application: Boolean = android,
)

/** Image metadata is extracted before presentation removes or moves any nodes. */
internal data class ForumPostMedia(
    val icons: List<String>,
    val screenshots: List<ScreenshotInfo>,
)

object ForumTopicIcon {
    internal fun screenshotTitle(title: String): Boolean {
        val normalized = title.trim().let { if (it.startsWith("C", true)) "С" + it.drop(1) else it }
        return normalized.startsWith("Скриншот", true)
    }

    internal fun screenshotBlocks(root: Element) =
        root.select(".post-block").filter {
            it.selectFirst(".block-title")?.text()?.let(::screenshotTitle) == true
        }

    internal fun extract(root: Element): ForumPostMedia {
        val blocks = screenshotBlocks(root)
        val first = blocks.firstOrNull()
        val icons =
            root.allElements
                .takeWhile {
                    it !== first &&
                        !(it.children().isEmpty() &&
                            it.text().trim().startsWith("Краткое описание", true))
                }
                .filter {
                    it.tagName() == "img" &&
                        (it.hasClass("linked-image") ||
                            (it.hasClass("ipb-attach") &&
                                it.hasClass(
                                    "attach-img",
                                ))) &&
                        it.closest(".post-block") == null
                }
                .mapNotNull { imageUrl(it) }
                .distinct()
        val screenshots =
            blocks
                .flatMap { it.select("img") }
                .filter { it.closest("a.attach-file") == null }
                .mapNotNull { image ->
                    val preview = imageUrl(image) ?: return@mapNotNull null
                    val original =
                        image.closest("a[href]")?.attr("href")?.let {
                            ForumDocumentParser.safeUrl(it, ForumUrls.BASE)
                        } ?: preview
                    ScreenshotInfo(preview, original)
                }
                .distinctBy { it.galleryUrl }
        return ForumPostMedia(icons, screenshots)
    }

    private fun imageUrl(image: Element): String? {
        val url =
            ForumDocumentParser.safeUrl(
                image.attr("data-src").ifBlank { image.attr("src") },
                ForumUrls.BASE,
            )
        return url?.takeIf { it.startsWith("https://") && !it.contains("/style_emoticons/") }
    }

    fun first(html: String): String? =
        extract(Jsoup.parseBodyFragment(html, ForumUrls.BASE).body()).icons.firstOrNull()
}
