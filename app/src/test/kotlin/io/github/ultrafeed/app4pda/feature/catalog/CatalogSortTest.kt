package io.github.ultrafeed.app4pda.feature.catalog

import io.github.ultrafeed.app4pda.data.forum.ForumSearchSort
import io.github.ultrafeed.app4pda.data.forum.ForumTopicSearch
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert
import org.junit.Test

class CatalogSortTest {
    @Test
    fun storedCodesAndCycleRemainExplicit() {
        assertThat(
            CatalogSort.entries.map { it.storageValue },
            `is`(
                listOf(
                    0,
                    1,
                    2,
                ),
            ),
        )
        CatalogSort.entries.forEach {
            assertThat(
                CatalogSort.fromStorage(it.storageValue),
                `is`(
                    it,
                ),
            )
        }
        assertThat(
            CatalogSort.NAME.next(),
            `is`(
                CatalogSort.NAME_DESC,
            ),
        )
        assertThat(
            CatalogSort.NAME_DESC.next(),
            `is`(
                CatalogSort.RECENTLY_ADDED,
            ),
        )
        assertThat(
            CatalogSort.RECENTLY_ADDED.next(),
            `is`(
                CatalogSort.NAME,
            ),
        )
        listOf(-1, 3, 5, Int.MAX_VALUE).forEach {
            assertThat(
                CatalogSort.fromStorage(it),
                `is`(
                    CatalogSort.NAME,
                ),
            )
        }
    }

    @Test
    fun forumMappingPreservesExistingWireParameters() {
        assertThat(
            CatalogSort.NAME.forumSort,
            `is`(
                ForumSearchSort.RELEVANCE,
            ),
        )
        assertThat(
            CatalogSort.NAME_DESC.forumSort,
            `is`(
                ForumSearchSort.DATE_DESC,
            ),
        )
        assertThat(
            CatalogSort.RECENTLY_ADDED.forumSort,
            `is`(
                ForumSearchSort.RELEVANCE,
            ),
        )
        CatalogSort.entries.forEach { order ->
            val url = ForumTopicSearch.url("Авито", order.forumSort)
            Assert.assertTrue(url.contains("query=%C0%E2%E8%F2%EE&"))
            Assert.assertTrue(url.contains("sort=${order.forumSort.parameter}"))
        }
    }
}
