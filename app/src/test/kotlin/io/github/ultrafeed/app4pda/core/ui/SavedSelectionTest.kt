package io.github.ultrafeed.app4pda.core.ui

import androidx.lifecycle.SavedStateHandle
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedSelectionTest {
    @Test
    fun restoredKeysAreUniqueAndToggleUsesLatestStoredSelection() {
        val saved = SavedStateHandle(mapOf("selected" to arrayListOf("a", "a", "b")))
        val selection = SavedSelection(saved, "selected")
        assertThat(
            selection.value,
            `is`(
                setOf(
                    "a",
                    "b",
                ),
            ),
        )
        assertThat(
            selection.toggle("a"),
            `is`(
                setOf(
                    "b",
                ),
            ),
        )
        assertThat(
            selection.toggle("c"),
            `is`(
                setOf(
                    "b",
                    "c",
                ),
            ),
        )
        assertThat(
            SavedSelection(saved, "selected").value,
            `is`(
                setOf(
                    "b",
                    "c",
                ),
            ),
        )
    }

    @Test
    fun successfulCompleteResultPrunesMissingKeysAndClearPersists() {
        val saved = SavedStateHandle()
        val selection = SavedSelection(saved, "selected")
        selection.replace(listOf("one", "two"))
        assertThat(
            selection.retain(setOf("two", "three")),
            `is`(
                setOf(
                    "two",
                ),
            ),
        )
        assertThat(
            saved.get<ArrayList<String>>("selected"),
            `is`(
                arrayListOf(
                    "two",
                ),
            ),
        )
        selection.replace(emptySet())
        assertTrue(SavedSelection(saved, "selected").value.isEmpty())
    }
}
