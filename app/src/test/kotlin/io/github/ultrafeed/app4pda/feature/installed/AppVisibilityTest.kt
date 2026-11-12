package io.github.ultrafeed.app4pda.feature.installed

import io.github.ultrafeed.app4pda.core.packages.LocalPackage
import io.github.ultrafeed.app4pda.feature.installed.ui.VersionSource
import io.github.ultrafeed.app4pda.feature.installed.ui.installedVersionCells
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class AppVisibilityTest {
    private val background =
        InstalledApp("service", LocalPackage("Service", "1", 1, 0, true, 24, 0, launchable = false))

    @Test
    fun visibilityIsOnlyTheStoredFlag() {
        assertThat(background.hidden, `is`(false))
        val hidden = background.with(preferences = AppPreferences(hidden = true))
        assertThat(hidden.hidden, `is`(true))
        assertThat(hidden.eligibleForUpdates, `is`(false))
        assertThat(hidden.with(forum = ForumApp(topicId = 42)).hidden, `is`(true))
        assertThat(hidden.with(playDiscovery = SourceDiscovery(true, 1)).hidden, `is`(true))
        assertThat(hidden.with(market = MarketRelease("2", 2, 1)).hidden, `is`(true))
    }

    @Test
    fun explicitChoicesAreNotChangedByDiscovery() {
        val shown = background.with(preferences = AppPreferences())
        assertThat(shown.hidden, `is`(false))
        val hidden =
            background.with(
                preferences = AppPreferences(hidden = true),
                forum = ForumApp(42),
            )
        assertThat(hidden.hidden, `is`(true))
        assertThat(hidden.with(playDiscovery = SourceDiscovery(true, 10)).hidden, `is`(true))
        assertThat(hidden.with(preferences = AppPreferences()).hidden, `is`(false))
    }

    @Test
    fun hiddenUpdatesAreNotCheckedOrCountedAndCountsMatchRows() {
        val hidden =
            background.with(
                market = MarketRelease("2", 2, 1),
                preferences = AppPreferences(hidden = true),
            )
        val shown = background.with(forum = ForumApp(42, versionName = "2"))
        val apps = listOf(hidden, shown)
        assertThat(hidden.eligibleForUpdates, `is`(false))
        assertThat(hidden.visibleUpdate, `is`(false))
        assertThat(shown.visibleUpdate, `is`(true))
        val state = InstalledAppsState(apps)
        AppSection.entries.forEach { section ->
            assertThat(
                state.counts[section],
                `is`(
                    apps.count {
                        it.belongsTo(
                            section,
                        )
                    },
                ),
            )
        }
        assertThat(
            state.counts[AppSection.UPDATES],
            `is`(
                1,
            ),
        )
    }

    @Test
    fun versionCellsContainOnlyAvailableNonEmptyVersionsInSourceOrder() {
        assertThat(
            installedVersionCells(background, false).map { it.source },
            `is`(
                listOf(
                    VersionSource.LOCAL,
                ),
            ),
        )
        val forum = background.with(forum = ForumApp(42, versionName = "2"))
        assertThat(
            installedVersionCells(forum, true).map { it.source },
            `is`(
                listOf(
                    VersionSource.LOCAL,
                    VersionSource.FORUM,
                ),
            ),
        )
        val play = background.with(market = MarketRelease("3", 3, 1))
        assertThat(
            installedVersionCells(play, false).map { it.source },
            `is`(
                listOf(
                    VersionSource.LOCAL,
                    VersionSource.PLAY,
                ),
            ),
        )
        assertThat(
            installedVersionCells(forum.with(market = play.market), false).map { it.source },
            `is`(
                listOf(
                    VersionSource.LOCAL,
                    VersionSource.FORUM,
                    VersionSource.PLAY,
                ),
            ),
        )
        assertThat(
            installedVersionCells(
                    background.with(
                        local =
                            (background.local ?: error("Required test value is missing")).copy(
                                versionName = "",
                            ),
                    ),
                    false,
                )
                .isEmpty(),
            `is`(true),
        )
    }
}
