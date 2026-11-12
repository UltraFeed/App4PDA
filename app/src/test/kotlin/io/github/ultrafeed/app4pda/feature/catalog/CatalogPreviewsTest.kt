package io.github.ultrafeed.app4pda.feature.catalog

import io.github.ultrafeed.app4pda.data.forum.ForumTopicPreview
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogPreviewsTest {
    @Test
    fun visibleTopicMetadataSuppliesVersionsWithoutLosingKnownValues() {
        val row = CatalogApp("topic:42", 42, "", "App", "Search description", "")
        val state = CatalogUiState(CatalogQuery(search = "App"), rows = listOf(row))
        val loaded =
            state.rows.withTopicPreviews(
                mapOf(42 to ForumTopicPreview("icon", true, "3.9.3", "Topic description", 1234)),
            )
        val app = loaded.single() as CatalogApp
        assertThat(
            app.version,
            `is`(
                "3.9.3",
            ),
        )
        assertThat(
            app.description,
            `is`(
                "Topic description",
            ),
        )
        assertThat(
            app.updatedAt,
            `is`(
                1234L,
            ),
        )
        assertThat(
            app.icon,
            `is`(
                "icon",
            ),
        )
        assertThat(
            loaded.withTopicPreviews(mapOf(42 to ForumTopicPreview(null, true))).single(),
            `is`(
                app,
            ),
        )
    }

    @Test
    fun metadataChangesOnlyInspectedRowsAndRemovesRejectedTopics() {
        fun app(id: Int, mixed: Boolean = false) =
            CatalogApp(
                "topic:$id",
                id,
                "",
                "App $id",
                "",
                "",
                requiresAndroidConfirmation = mixed,
            )

        val untouched = app(4)
        val header = CatalogHeader("section", "Results")
        val state =
            CatalogUiState(
                CatalogQuery(search = "query"),
                rows = listOf(header, app(1, true), app(2, true), app(3), untouched),
                hasMore = true,
            )
        val updated =
            state.rows.withTopicPreviews(
                mapOf(
                    1 to ForumTopicPreview("icon-1", true),
                    2 to ForumTopicPreview(null, false),
                    3 to ForumTopicPreview(null, false),
                ),
            )
        assertThat(
            updated.map { it.key },
            `is`(
                listOf(
                    "section",
                    "topic:1",
                    "topic:3",
                    "topic:4",
                ),
            ),
        )
        assertThat(
            (updated[1] as CatalogApp).icon,
            `is`(
                "icon-1",
            ),
        )
        assertFalse((updated[1] as CatalogApp).requiresAndroidConfirmation)
        assertThat(
            updated[0],
            sameInstance(
                header,
            ),
        )
        assertThat(
            updated.last(),
            sameInstance(
                untouched,
            ),
        )
        assertTrue(state.hasMore)
        assertThat(
            state.rows.size,
            `is`(
                5,
            ),
        )
    }
}
