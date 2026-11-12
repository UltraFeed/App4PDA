package io.github.ultrafeed.app4pda.feature.installed

import io.github.ultrafeed.app4pda.core.packages.LocalPackage
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        MatcherAssert.assertThat(
            SourceDiscovery().checkedAt,
            CoreMatchers.`is`(
                0L,
            ),
        )
        assertFalse(app(SourceDiscovery(false, 1), SourceDiscovery()).hidden)
    }

    @Test
    fun missingSourcesNeverChangeVisibility() {
        assertFalse(app(SourceDiscovery(false, 1), SourceDiscovery(false, 1)).hidden)
        assertFalse(app(SourceDiscovery(true, 1), SourceDiscovery(false, 1)).hidden)
    }

    @Test
    fun storedVisibilitySurvivesSourceChanges() {
        assertFalse(
            app(SourceDiscovery(false, 1), SourceDiscovery(false, 1), false).hidden,
        )
        assertTrue(
            app(SourceDiscovery(true, 1), SourceDiscovery(true, 1), true).hidden,
        )
    }
}
