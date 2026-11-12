package io.github.ultrafeed.app4pda.feature.installed

import io.github.ultrafeed.app4pda.core.packages.LocalPackage
import io.github.ultrafeed.app4pda.feature.installed.ui.VersionSource
import io.github.ultrafeed.app4pda.feature.installed.ui.installedVersionCells
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuStoreStatusTest {
    private val app =
        InstalledApp(
            "org.example.app",
            LocalPackage("Example", "1", 1, 0, true, 24, 0, launchable = false),
        )

    @Test
    fun sourceDiscoveryNeverOverridesStoredVisibility() {
        assertFalse(app.hidden)
        val found = app.with(ruStoreDiscovery = SourceDiscovery(true, 1))
        assertFalse(found.hidden)
        assertTrue(found.belongsTo(AppSection.PENDING_LINKING))
        assertTrue(found.with(preferences = AppPreferences(hidden = true)).hidden)
    }

    @Test
    fun ignoredRuStoreUpdateDoesNotAffectOtherSources() {
        val found = app.with(ruStore = MarketRelease("2", 2, 1))
        assertTrue(found.visibleUpdate)
        assertTrue(installedAppStatus(found, InstalledCardAppearance()).ruStoreUpdate)
        assertFalse(found.with(preferences = AppPreferences(ignoreRuStore = true)).visibleUpdate)
        val both =
            found.with(
                forum = ForumApp(42, versionName = "3"),
                preferences = AppPreferences(ignoreRuStore = true),
            )
        assertTrue(both.visibleUpdate)
    }

    @Test
    fun versionRowsKeepAllFourSourcesInOrder() {
        val found =
            app.with(
                forum = ForumApp(42, versionName = "2"),
                market = MarketRelease("3", 3, 1),
                ruStore = MarketRelease("4", 4, 1),
            )
        MatcherAssert.assertThat(
            installedVersionCells(found, false).map { it.source },
            CoreMatchers.`is`(
                listOf(
                    VersionSource.LOCAL,
                    VersionSource.FORUM,
                    VersionSource.PLAY,
                    VersionSource.RUSTORE,
                ),
            ),
        )
    }
}
