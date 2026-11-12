package io.github.ultrafeed.app4pda.feature.installed

import io.github.ultrafeed.app4pda.core.packages.LocalPackage
import io.github.ultrafeed.app4pda.feature.installed.ui.VersionSource
import io.github.ultrafeed.app4pda.feature.installed.ui.installedVersionCells
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class RuStoreStatusTest {
    private val app =
        InstalledApp(
            "org.example.app",
            LocalPackage("Example", "1", 1, 0, true, 24, 0, launchable = false),
        )

    @Test
    fun sourceDiscoveryNeverOverridesStoredVisibility() {
        assertThat(app.hidden, `is`(false))
        val found = app.with(ruStoreDiscovery = SourceDiscovery(true, 1))
        assertThat(found.hidden, `is`(false))
        assertThat(found.belongsTo(AppSection.PENDING_LINKING), `is`(true))
        assertThat(found.with(preferences = AppPreferences(hidden = true)).hidden, `is`(true))
    }

    @Test
    fun ignoredRuStoreUpdateDoesNotAffectOtherSources() {
        val found = app.with(ruStore = MarketRelease("2", 2, 1))
        assertThat(found.visibleUpdate, `is`(true))
        assertThat(installedAppStatus(found, InstalledCardAppearance()).ruStoreUpdate, `is`(true))
        assertThat(
            found.with(preferences = AppPreferences(ignoreRuStore = true)).visibleUpdate,
            `is`(false),
        )
        val both =
            found.with(
                forum = ForumApp(42, versionName = "3"),
                preferences = AppPreferences(ignoreRuStore = true),
            )
        assertThat(both.visibleUpdate, `is`(true))
    }

    @Test
    fun versionRowsKeepAllFourSourcesInOrder() {
        val found =
            app.with(
                forum = ForumApp(42, versionName = "2"),
                market = MarketRelease("3", 3, 1),
                ruStore = MarketRelease("4", 4, 1),
            )
        assertThat(
            installedVersionCells(found, false).map { it.source },
            `is`(
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
