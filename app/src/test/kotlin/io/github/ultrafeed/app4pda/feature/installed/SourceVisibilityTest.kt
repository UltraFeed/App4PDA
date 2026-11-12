package io.github.ultrafeed.app4pda.feature.installed

import io.github.ultrafeed.app4pda.core.packages.LocalPackage
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class SourceVisibilityTest {
    private fun app(
        play: SourceDiscovery,
        store: SourceDiscovery,
        hidden: Boolean = false,
    ) =
        InstalledApp(
            "org.example",
            LocalPackage("Example", "1", 1, 0, false, 24, 0),
            ForumApp(),
            AppPreferences(hidden = hidden),
            playDiscovery = play,
            ruStoreDiscovery = store,
        )

    @Test
    fun unknownIsNotAbsence() {
        assertThat(
            SourceDiscovery().checkedAt,
            `is`(
                0L,
            ),
        )
        assertThat(app(SourceDiscovery(checkedAt = 1), SourceDiscovery()).hidden, `is`(false))
    }

    @Test
    fun missingSourcesNeverChangeVisibility() {
        assertThat(
            app(SourceDiscovery(checkedAt = 1), SourceDiscovery(checkedAt = 1)).hidden,
            `is`(false),
        )
        assertThat(
            app(SourceDiscovery(true, 1), SourceDiscovery(checkedAt = 1)).hidden,
            `is`(false),
        )
    }

    @Test
    fun storedVisibilitySurvivesSourceChanges() {
        assertThat(
            app(SourceDiscovery(checkedAt = 1), SourceDiscovery(checkedAt = 1)).hidden,
            `is`(false),
        )
        assertThat(app(SourceDiscovery(true, 1), SourceDiscovery(true, 1), true).hidden, `is`(true))
    }
}
