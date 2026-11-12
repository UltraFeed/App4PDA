package io.github.ultrafeed.app4pda.feature.installed

import io.github.ultrafeed.app4pda.core.packages.LocalPackage
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
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
        assertThat(app(true).belongsTo(AppSection.APPLICATIONS), `is`(false))
        assertThat(app(true).belongsTo(AppSection.UNLINKED), `is`(true))
        assertThat(app(true).belongsTo(AppSection.PENDING_LINKING), `is`(false))
        for (completed in listOf(false, true)) {
            val disabled = app(completed).with(preferences = AppPreferences(ignoreForum = true))
            assertThat(disabled.belongsTo(AppSection.APPLICATIONS), `is`(true))
            assertThat(disabled.belongsTo(AppSection.UNLINKED), `is`(false))
            assertThat(disabled.belongsTo(AppSection.PENDING_LINKING), `is`(false))
            assertThat(disabled.userChoicesEditable, `is`(true))
        }
    }

    @Test
    fun pendingApplicationsMoveToAppsOnlyAfterLinking() {
        assertThat(app(false).belongsTo(AppSection.PENDING_LINKING), `is`(true))
        assertThat(app(false).belongsTo(AppSection.UNLINKED), `is`(false))
        assertThat(app(false).belongsTo(AppSection.APPLICATIONS), `is`(false))
        assertThat(app(false).userChoicesEditable, `is`(false))
        assertThat(app(true).userChoicesEditable, `is`(true))
        for (completed in listOf(false, true)) {
            assertThat(app(completed, topic = 1).belongsTo(AppSection.APPLICATIONS), `is`(true))
            assertThat(app(completed, topic = 1).belongsTo(AppSection.UNLINKED), `is`(false))
            assertThat(app(completed, topic = 1).belongsTo(AppSection.PENDING_LINKING), `is`(false))
        }
    }

    @Test
    fun hiddenApplicationsNeverEnterEitherVisibleSection() {
        listOf(false, true).forEach { completed ->
            assertThat(app(completed, hidden = true).userChoicesEditable, `is`(false))
            assertThat(
                app(completed, hidden = true).belongsTo(AppSection.APPLICATIONS),
                `is`(false),
            )
            assertThat(app(completed, hidden = true).belongsTo(AppSection.UNLINKED), `is`(false))
            assertThat(
                app(completed, hidden = true).belongsTo(AppSection.PENDING_LINKING),
                `is`(false),
            )
        }
    }
}
