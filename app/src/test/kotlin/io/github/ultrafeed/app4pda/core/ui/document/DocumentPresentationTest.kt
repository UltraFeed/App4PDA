package io.github.ultrafeed.app4pda.core.ui.document

import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.jsoup.Jsoup
import org.junit.Test

/** Tests our generated gallery markup, not compatibility with forum HTML. */
class DocumentPresentationTest {
    @Test
    fun galleryPrecedesContentAndPreservesOriginalIndices() {
        val body =
            DocumentPresentation(
                    "description",
                    listOf(
                        "https://example.org/one.jpg",
                        "javascript:alert(1)",
                        "https://example.org/two.jpg",
                    ),
                    listOf(
                        "https://example.org/preview?a=1&b=2",
                        "",
                        "https://example.org/preview2.jpg",
                    ),
                )
                .body()
        val parsed = Jsoup.parseBodyFragment(body)
        assertThat(
            parsed.select("a").map { it.attr("href") },
            `is`(
                listOf(
                    "app-gallery://0",
                    "app-gallery://2",
                ),
            ),
        )
        assertThat(
            (parsed.selectFirst("img") ?: return).attr("src"),
            `is`(
                "https://example.org/preview?a=1&b=2",
            ),
        )
        assertThat(
            parsed.body().ownText(),
            `is`(
                "description",
            ),
        )
        assertThat(
            body,
            CoreMatchers.not(
                CoreMatchers.containsString(
                    "javascript:",
                ),
            ),
        )
    }

    @Test
    fun missingOrUnsafePreviewUsesOriginalWithoutAddingInvalidImages() {
        val body =
            DocumentPresentation(
                    "description",
                    listOf(
                        "https://example.org/one.jpg",
                        "https://example.org/two.jpg",
                        "https://user:password@example.org/private.jpg",
                    ),
                    listOf("data:image/png;base64,invalid"),
                )
                .body()
        assertThat(
            Jsoup.parseBodyFragment(body).select("img").map { it.attr("src") },
            `is`(
                listOf(
                    "https://example.org/one.jpg",
                    "https://example.org/two.jpg",
                ),
            ),
        )
    }

    @Test
    fun noGalleryLeavesDocumentUnchanged() {
        assertThat(
            DocumentPresentation("description", emptyList(), emptyList()).body(),
            `is`(
                "description",
            ),
        )
    }
}
