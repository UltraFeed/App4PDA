package io.github.ultrafeed.app4pda.data.forum

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.jsoup.Jsoup
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumDocumentParserTest {
    @Test
    fun spoilerServiceMarkersAreRemovedButMessageLinksRemain() {
        val html =
            """<div class="spoil"><a title="Spoil-2578146-1"> # </a><div class="block-title">Versions</div><a href="?showtopic=116273&amp;p=2578146">#2578146</a></div>"""
        val actual = Jsoup.parseBodyFragment(ForumDocumentParser.parse(html))
        assertTrue(actual.select("a[title^=Spoil-]").isEmpty())
        assertThat(
            actual.selectFirst("a[href]")!!.text(),
            `is`(
                "#2578146",
            ),
        )
    }

    @Test
    fun imageRemainsAnExplicitLinkAfterRemovingTheSiteHandler() {
        // Interaction contract fixture, not evidence of a successful forum response.
        val body =
            Jsoup.parseBodyFragment(
                ForumDocumentParser.parse(
                    """<a href="#" onclick="siteGallery()"><img src="https://example.org/photo.png"></a>""",
                ),
            )
        assertThat(
            body.selectFirst("a")!!.attr("href"),
            `is`(
                "https://example.org/photo.png",
            ),
        )
        Assert.assertFalse(body.selectFirst("a")!!.hasAttr("onclick"))
    }

    @Test
    fun movingAnImagePreservesCaptionLinksAndFollowingContent() {
        // Transformation contract, not a successful forum response fixture.
        val html =
            """<div class="post-block spoil"><div class="block-title">Скриншоты</div><div class="block-body"><img src="https://example.org/shot.png">Caption <a href="https://example.org/info">Details</a></div></div><p>Beta</p>"""
        val actual =
            Jsoup.parseBodyFragment(
                ForumDocumentParser.topic(html, listOf("https://example.org/shot.png")),
            )
        assertThat(
            actual.text(),
            `is`(
                "Caption Details Beta",
            ),
        )
        assertTrue(actual.select(".spoil,img").isEmpty())
        assertThat(
            actual.selectFirst("a")!!.attr("href"),
            `is`(
                "https://example.org/info",
            ),
        )
    }

    @Test
    fun movingTableWrappedImagesLeavesNoEmptyBordersOrLargeBreakRuns() {
        // Structural cleanup contract for containers that owned a moved image.
        val html =
            """<p>Header</p><table><tbody><tr><td><img class="linked-image" src="https://example.org/icon.png"></td></tr></tbody></table><br><br><br><br><p>Body</p>"""
        val actual = Jsoup.parseBodyFragment(ForumDocumentParser.topic(html, emptyList()))
        assertTrue(actual.select("table,img").isEmpty())
        assertThat(
            actual.text(),
            `is`(
                "Header Body",
            ),
        )
        assertThat(
            actual.select("br").size,
            `is`(
                2,
            ),
        )
    }

    @Test
    fun executableMarkupIsRemovedWithoutRemovingUnknownTextContainers() {
        // Adversarial parser contract, not a successful forum response fixture.
        val actual =
            Jsoup.parseBodyFragment(
                ForumDocumentParser.parse(
                    """<section><custom-node>Keep</custom-node><script>bad()</script><form>secret</form>
               <img src="https://example.org/icon.png" onerror="bad()">
               <a href="javascript:bad()">Unsafe</a><iframe src="https://example.org/video" title="Video"></iframe></section>""",
                ),
            )
        Assert.assertTrue(actual.text().contains("Keep"))
        Assert.assertTrue(actual.text().contains("Unsafe"))
        assertThat(
            actual.select("script,form,iframe,[onerror],[href^=javascript]").size,
            `is`(
                0,
            ),
        )
        assertThat(
            actual.select("a").last()!!.attr("href"),
            `is`(
                "https://example.org/video",
            ),
        )
    }
}
