package io.github.ultrafeed.app4pda.data.forum

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumSectionsTest {
    @Test
    fun scopeContainsOnlyTheSelectedBranch() {
        MatcherAssert.assertThat(
            ForumSections.scope(316),
            CoreMatchers.`is`(
                setOf(
                    316,
                ),
            ),
        )
        val programs = ForumSections.scope(212)
        assertTrue(programs.containsAll(setOf(212, 316, 558, 1109)))
        MatcherAssert.assertThat(
            programs,
            CoreMatchers.not(
                CoreMatchers.hasItem(
                    213,
                ),
            ),
        )
        MatcherAssert.assertThat(
            programs,
            CoreMatchers.not(
                CoreMatchers.hasItem(
                    1143,
                ),
            ),
        )
        assertTrue(ForumSections.scope(213).containsAll(setOf(1164, 1149)))
        MatcherAssert.assertThat(
            ForumSections.ids,
            CoreMatchers.not(
                CoreMatchers.hasItem(
                    1158,
                ),
            ),
        )
        MatcherAssert.assertThat(
            ForumSections.scope(529),
            CoreMatchers.`is`(
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
        MatcherAssert.assertThat(
            url.queryParameter("forums"),
            CoreMatchers.`is`(
                "316",
            ),
        )
        MatcherAssert.assertThat(
            url.queryParameter("subforums"),
            CoreMatchers.`is`(
                "0",
            ),
        )
        MatcherAssert.assertThat(
            url.queryParameter("forums[]"),
            CoreMatchers.nullValue(),
        )
        MatcherAssert.assertThat(
            ForumTopicSearch.query(url.toString()),
            CoreMatchers.`is`(
                "Авито",
            ),
        )
    }
}
