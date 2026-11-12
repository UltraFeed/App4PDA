package io.github.ultrafeed.app4pda.data.forum

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class ForumSectionsTest {
    @Test
    fun scopeContainsOnlyTheSelectedBranch() {
        assertThat(
            ForumSections.scope(316),
            `is`(
                setOf(
                    316,
                ),
            ),
        )
        val programs = ForumSections.scope(212)
        assertThat(programs.containsAll(setOf(212, 316, 558, 1109)), `is`(true))
        assertThat(
            programs,
            CoreMatchers.not(
                CoreMatchers.hasItem(
                    213,
                ),
            ),
        )
        assertThat(
            programs,
            CoreMatchers.not(
                CoreMatchers.hasItem(
                    1143,
                ),
            ),
        )
        assertThat(ForumSections.scope(213).containsAll(setOf(1164, 1149)), `is`(true))
        assertThat(
            ForumSections.ids,
            CoreMatchers.not(
                CoreMatchers.hasItem(
                    1158,
                ),
            ),
        )
        assertThat(
            ForumSections.scope(529),
            `is`(
                setOf(
                    529,
                    539,
                    540,
                ),
            ),
        )
    }

    @Test
    fun requestsDeclareExactScopeWithoutImplicitDescendants() {
        val url = ForumTopicSearch.url("Авито", ForumSearchSort.RELEVANCE, 316).toHttpUrl()
        assertThat(
            url.queryParameter("forums"),
            `is`(
                "316",
            ),
        )
        assertThat(
            url.queryParameter("subforums"),
            `is`(
                "0",
            ),
        )
        assertThat(
            url.queryParameter("forums[]"),
            CoreMatchers.nullValue(),
        )
        assertThat(
            ForumTopicSearch.query("$url"),
            `is`(
                "Авито",
            ),
        )
    }
}
