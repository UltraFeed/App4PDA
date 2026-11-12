package io.github.ultrafeed.app4pda.data.forum

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.jsoup.nodes.Document
import org.junit.Assert.assertThrows
import org.junit.Test

class ForumTopicSearchTest {
    @Test
    fun explicitPackageUsesFullTextWhileNamesUseTopicHeaders() {
        fun source(query: String) =
            ForumTopicSearch.url(query, ForumSearchSort.RELEVANCE)
                .toHttpUrl()
                .queryParameter("source")
        assertThat(
            source("com.google.android.apps.docs"),
            `is`(
                "all",
            ),
        )
        assertThat(
            source("Google Drive"),
            `is`(
                "top",
            ),
        )
        assertThat(
            source("Авито"),
            `is`(
                "top",
            ),
        )
    }

    @Test
    fun cyrillicUsesTheActualWindows1251FormEncodingIncludingContinuation() {
        val url = ForumTopicSearch.url("Авито", ForumSearchSort.RELEVANCE)
        assertThat(url.contains("query=%C0%E2%E8%F2%EE&"), `is`(true))
        assertThat(
            ForumTopicSearch.query(url),
            `is`(
                "Авито",
            ),
        )
        val next = "<a href=\"${url.replace("&", "&amp;")}&amp;st=50\">2</a>"
        val page = ForumTopicSearch.parse(sections + next, url)
        assertThat(
            ForumTopicSearch.query(
                page.nextUrl ?: return,
            ),
            `is`(
                "Авито",
            ),
        )
        assertThat(page.nextUrl.endsWith("st=50"), `is`(true))
    }

    @Test
    fun actualHttp200QuotaNoticeIsNotAnEmptySuccessfulSearch() {
        val notice = "Вы превысили квоту на поиск. Подождите 15 минут и попробуйте еще раз."
        val error =
            assertThrows(ForumApiException::class.java) {
                ForumTopicSearch.parse(
                    notice,
                    ForumTopicSearch.url("Google Messages", ForumSearchSort.RELEVANCE),
                )
            }
        assertThat(
            error.statusCode,
            `is`(
                429,
            ),
        )
    }

    // Minimal DOM for our scope policy only; live tests validate the actual forum form.
    private val sections =
        Document.createShell("")
            .body()
            .appendElement("select")
            .attr("name", "forums[]")
            .apply {
                for ((id, label) in
                    listOf(
                        212 to "Apps",
                        1143 to "|--Excluded",
                        529 to "Emulators",
                        539 to "|--Android",
                        558 to "|--Windows",
                        1241 to "Mixed",
                    )) {
                    appendElement("option")
                        .attr(
                            "value",
                            "$id",
                        )
                        .text(label)
                }
            }
            .outerHtml()

    @Test
    fun filteredEmptyPageKeepsTheServerContinuation() {
        // Local policy contract, not a captured successful response.
        val row =
            """<div data-topic="1"><div class="topic_title"><a href="?showtopic=1">Guide</a></div><a href="?showforum=1143">Special</a></div>"""
        val next = """<a href="?act=search&amp;query=Google&amp;result=topics&amp;st=50">2</a>"""
        val result =
            ForumTopicSearch.parse(
                sections + row + next,
                ForumTopicSearch.url("Google", ForumSearchSort.RELEVANCE),
            )
        assertThat(result.topics.isEmpty(), `is`(true))
        assertThat(
            (result.nextUrl ?: error("Required test value is missing")).endsWith("st=50"),
            `is`(true),
        )
    }

    @Test
    fun permittedPcBranchIsIncludedAndMixedAiRequiresConfirmation() {
        fun row(id: Int, section: Int) =
            """<div data-topic="$id"><div class="topic_title"><a href="?showtopic=$id">App</a></div><a href="?showforum=$section">Section</a></div>"""

        val result =
            ForumTopicSearch.parse(
                sections + row(1, 539) + row(2, 558) + row(3, 1241),
                ForumTopicSearch.url("App", ForumSearchSort.RELEVANCE),
            )
        assertThat(
            result.topics.map { it.id },
            `is`(
                listOf(
                    1,
                    2,
                    3,
                ),
            ),
        )
        assertThat(result.topics.last().mixedSection, `is`(true))
    }
}
