package io.github.ultrafeed.app4pda.feature.installed

import io.github.ultrafeed.app4pda.core.packages.LocalPackage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledSectionsTest {
    private fun app(completed: Boolean, topic: Int = 0, hidden: Boolean = false) =
        InstalledApp(
            "example.app",
            local = LocalPackage("App", "1", 1, 0, false, 24, 0),
            forum = ForumApp(topicId = topic),
            bindingSearchCompleted = completed,
            preferences = AppPreferences(hidden = hidden),
        )

    @Test
    fun completedUnlinkedApplicationsBelongToNotLinked() {
        assertFalse(app(true).belongsTo(AppSection.APPLICATIONS))
        assertTrue(app(true).belongsTo(AppSection.UNLINKED))
        assertFalse(app(true).belongsTo(AppSection.PENDING_LINKING))
        for (completed in listOf(false, true)) {
            val disabled = app(completed).with(preferences = AppPreferences(ignoreForum = true))
            assertTrue(disabled.belongsTo(AppSection.APPLICATIONS))
            assertFalse(disabled.belongsTo(AppSection.UNLINKED))
            assertFalse(disabled.belongsTo(AppSection.PENDING_LINKING))
            assertTrue(disabled.userChoicesEditable)
        }
    }

    @Test
    fun pendingApplicationsMoveToAppsOnlyAfterLinking() {
        assertTrue(app(false).belongsTo(AppSection.PENDING_LINKING))
        assertFalse(app(false).belongsTo(AppSection.UNLINKED))
        assertFalse(app(false).belongsTo(AppSection.APPLICATIONS))
        assertFalse(app(false).userChoicesEditable)
        assertTrue(app(true).userChoicesEditable)
        for (completed in listOf(false, true)) {
            assertTrue(app(completed, topic = 1).belongsTo(AppSection.APPLICATIONS))
            assertFalse(app(completed, topic = 1).belongsTo(AppSection.UNLINKED))
            assertFalse(app(completed, topic = 1).belongsTo(AppSection.PENDING_LINKING))
        }
    }

    @Test
    fun hiddenApplicationsNeverEnterEitherVisibleSection() {
        listOf(false, true).forEach { completed ->
            assertFalse(app(completed, hidden = true).userChoicesEditable)
            assertFalse(app(completed, hidden = true).belongsTo(AppSection.APPLICATIONS))
            assertFalse(app(completed, hidden = true).belongsTo(AppSection.UNLINKED))
            assertFalse(app(completed, hidden = true).belongsTo(AppSection.PENDING_LINKING))
        }
    }
}
