package io.github.ultrafeed.app4pda.data.forum

import java.net.URI
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** Retains the complete selected message; site code is never part of the rendered document. */
object ForumDocumentParser {
    fun parse(html: String, baseUrl: String = ForumUrls.BASE): String {
        val root = Jsoup.parseBodyFragment(html, baseUrl).body()
        root
            .select(".spoil > a[name^=Spoil-], .spoil > a[title^=Spoil-]")
            .filter { it.text().trim() == "#" }
            .forEach { it.remove() }
        root.select("script,style,form,object,embed,base,link,meta").remove()
        root.select("iframe,video,audio").forEach { replaceUnsupportedMedia(it, baseUrl) }
        root.allElements.forEach { sanitizeElement(it, baseUrl) }
        root.select("a").forEach { link ->
            if (!link.hasAttr("href") || link.attr("href") == "#") {
                link.selectFirst("img[src]")?.let { link.attr("href", it.attr("src")) }
            }
        }
        return root.html()
    }

    /** The edit footer is stored separately; retain original image/metadata structure for cache. */
    fun topicCacheContent(html: String): String {
        val body = Jsoup.parseBodyFragment(parse(html), ForumUrls.BASE).body()
        body.selectFirst(".edit")?.let { removeAfter(body, it) }
        return body.html()
    }

    /** Move only confirmed gallery images; retain text and links from their original containers. */
    fun topic(html: String, screenshots: List<String>): String {
        val body = Jsoup.parseBodyFragment(parse(html), ForumUrls.BASE).body()
        val media = ForumTopicIcon.extract(body)
        val screenshotBlocks = ForumTopicIcon.screenshotBlocks(body)
        val icons = body.select("img").filter { it.attr("src") in media.icons }
        val gallery = screenshots.toSet()
        val moved =
            body.select("img").filter {
                it.attr("src") in gallery || it.closest("a[href]")?.attr("href") in gallery
            }
        (icons + moved).distinct().forEach { removeTransferredImage(it, body) }
        screenshotBlocks.forEach(::unwrapScreenshotBlock)
        // Trim only this selected first message; metadata was extracted before presentation.
        val edit = body.selectFirst(".edit")
        if (edit != null) removeAfter(body, edit)
        val start =
            body.allElements.firstOrNull {
                it.children().isEmpty() && it.text().trim().startsWith("Краткое описание", true)
            }
        if (start != null) removeBefore(body, start)
        body.allElements.forEach(::collapseEmptyLines)
        return body.html()
    }

    private fun removeAfter(root: Element, boundary: Node) {
        var current = boundary
        while (current !== root) {
            generateSequence(current.nextSibling()) { it.nextSibling() }
                .toList()
                .forEach { it.remove() }
            val parent = current.parent() ?: break
            if (current === boundary) current.remove()
            current = parent
        }
    }

    private fun removeBefore(root: Element, boundary: Node) {
        var current = boundary
        while (current !== root) {
            generateSequence(current.previousSibling()) { it.previousSibling() }
                .toList()
                .forEach { it.remove() }
            current = current.parent() ?: break
        }
    }

    fun safeUrl(value: String, base: String): String? = runCatching {
        val uri = URI(base).resolve(value.trim())
        if (uri.scheme?.lowercase() !in setOf("https", "http", "mailto", "market", "tg")) null
        else
            ForumUrls.normalize(
                    "$uri",
                )
                .let { normalized ->
                    val http = normalized.toHttpUrlOrNull()
                    if (http?.host == ForumUrls.HOST)
                        http.newBuilder().removeAllQueryParameters("s").build().toString()
                    else normalized
                }
    }
        .getOrNull()
}

internal fun replaceUnsupportedMedia(media: Element, baseUrl: String) {

    val target =
        media.attr("src").ifBlank {
            media.selectFirst("source[src]")?.attr("src").orEmpty()
        }
    val link = ForumDocumentParser.safeUrl(target, baseUrl)
    if (link != null)
        media.replaceWith(
            Element("a").attr("href", link).text(media.attr("title").ifBlank { link }),
        )
    else media.remove()
}

internal fun sanitizeElement(element: Element, baseUrl: String) {

    element.attributes().asList().forEach { attribute ->
        val name = attribute.key.lowercase()
        if (
            name !in
                setOf(
                    "id",
                    "class",
                    "href",
                    "src",
                    "data-src",
                    "alt",
                    "title",
                    "width",
                    "height",
                    "colspan",
                    "rowspan",
                    "start",
                    "open",
                    "style",
                )
        ) {
            element.removeAttr(attribute.key)
        } else if (name in setOf("href", "src", "data-src")) {
            val value = attribute.value
            val url =
                if (name == "href" && value.startsWith("#")) value
                else ForumDocumentParser.safeUrl(value, baseUrl)
            if (url == null || (name != "href" && !url.startsWith("https://")))
                element.removeAttr(
                    attribute.key,
                )
            else element.attr(attribute.key, url)
        } else if (name == "style") {
            // Site sizes/colors/backgrounds are replaced by the application's stylesheet.
            element.removeAttr(attribute.key)
        }
    }
    if (element.tagName() == "img" && element.hasAttr("data-src"))
        element
            .attr(
                "src",
                element.attr("data-src"),
            )
            .removeAttr("data-src")
}

internal fun removeTransferredImage(image: Element, body: Element) {

    val containers = image.parents().takeWhile { it !== body }
    val link = image.closest("a")
    image.remove()
    if (link != null && link.text().isBlank() && link.select("img").isEmpty()) link.remove()
    containers.forEach { container ->
        if (isEmptyMediaWrapper(container)) container.remove()
    }
}

private fun isEmptyMediaWrapper(container: Element): Boolean {
    val structural = container.tagName() in setOf("a", "span", "div", "td", "tr", "tbody", "table")
    val contentWrapper = container.hasClass("block-body") || container.hasClass("post-block")
    val empty =
        container.text().isBlank() && container.select("img,a[href],video,audio,iframe").isEmpty()
    return structural && !contentWrapper && empty
}

private fun unwrapScreenshotBlock(block: Element) {

    val content = block.selectFirst(".block-body")
    if (content != null && content.select("img").isEmpty()) {
        if (content.text().isBlank() && content.select("a[href],video,audio,iframe").isEmpty())
            block.remove()
        else block.replaceWith(content.tagName("div"))
    }
}

private fun collapseEmptyLines(parent: Element) {

    var breaks = 0
    parent.childNodes().toList().forEach { node ->
        when (node) {
            is Element if node.tagName() == "br" -> {
                breaks++
                if (breaks > 2) node.remove()
            }

            is TextNode if node.isBlank -> Unit
            else -> breaks = 0
        }
    }
}
