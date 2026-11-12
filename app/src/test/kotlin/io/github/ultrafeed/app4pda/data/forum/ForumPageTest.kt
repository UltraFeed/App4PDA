package io.github.ultrafeed.app4pda.data.forum

import java.time.Instant
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class ForumPageTest {

    // Controlled DOM-boundary rules, not a substitute for live forum compatibility tests.
    @Test
    fun dateBelongsToSelectedHeaderNotItsTextOrAnotherPost() {
        val html =
            """
            <div data-post="1"><time datetime="2026-10-01T12:00:00Z"></time>
              <div id="post-main-1"><span class="post_date">02.10.2026, 12:00</span></div></div>
            <div data-post="2"><time datetime="2026-10-03T12:00:00Z"></time>
              <div id="post-main-2">Message</div></div>
            """
                .trimIndent()
        MatcherAssert.assertThat(
            ForumPage.parse(html, 1).createdAt(),
            CoreMatchers.`is`(
                Instant.parse(
                        "2026-10-01T12:00:00Z",
                    )
                    .toEpochMilli(),
            ),
        )
        MatcherAssert.assertThat(
            ForumPage.parse(html, 2).createdAt(),
            CoreMatchers.`is`(
                Instant.parse(
                        "2026-10-03T12:00:00Z",
                    )
                    .toEpochMilli(),
            ),
        )
        MatcherAssert.assertThat(
            ForumPage.parse(html.replace("<time datetime=\"2026-10-01T12:00:00Z\"></time>", ""), 1)
                .createdAt(),
            CoreMatchers.nullValue(),
        )
    }

    @Test
    fun packageQueryIsDecodedWithoutDecodingHtmlOrOtherReplies() {
        val page =
            ForumPage.parse(
                """
                <div id="post-main-1"><a href="https://play.google.com/store/apps/details?id=org%2Eexample&amp;hl=en">Play</a>
                <a href="market://details?id=org.example">Same</a>
                <a href="https://outside.invalid/store/apps/details?id=org.wrong">Wrong host</a>
                Package name: org.second</div>
                <div id="post-main-2">Package name: org.other</div>
                """
                    .trimIndent(),
            )
        MatcherAssert.assertThat(
            ForumPackageNames.extract(page.post),
            CoreMatchers.`is`(
                listOf(
                    "org.example",
                    "org.second",
                ),
            ),
        )
    }
}
