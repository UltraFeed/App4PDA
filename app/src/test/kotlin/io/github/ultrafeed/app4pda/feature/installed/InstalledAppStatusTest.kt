package io.github.ultrafeed.app4pda.feature.installed

import io.github.ultrafeed.app4pda.core.packages.LocalPackage
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertFalse
import org.junit.Test

class InstalledAppStatusTest {
    private val appearance = InstalledCardAppearance()

    @Test
    fun missingTopicCannotAdvertiseAnUpdateInEitherListOrCard() {
        val value = app(forum = "3", topic = 0)
        assertFalse(value.forumUpdate)
        assertFalse(value.visibleUpdate)
        assertFalse(installedAppStatus(value, appearance).forumUpdate)
    }

    private fun app(
        local: String = "2.0",
        forum: String = "2.0",
        topic: Int = 42,
        code: Long = 2,
        marketCode: Long? = null,
        preferences: AppPreferences = AppPreferences(),
    ) =
        InstalledApp(
            "test.app",
            LocalPackage("Application", local, code, 0, false, 21, 0),
            ForumApp(topic, versionName = forum),
            preferences,
            marketCode?.let { MarketRelease("market", it, 0) },
        )

    @Test
    fun forumComparisonMatchesOriginalThreeStates() {
        for ((available, expected) in
            listOf(
                "3.0" to AppVersionStatus.UPDATE_AVAILABLE,
                "1.0" to AppVersionStatus.INSTALLED_NEWER,
                "2.0" to AppVersionStatus.NONE,
                "" to AppVersionStatus.NONE,
            )) {
            assertThat(
                available,
                installedAppStatus(app(forum = available), appearance).localVersion,
                `is`(
                    expected,
                ),
            )
        }
        assertThat(
            installedAppStatus(app(local = "", forum = "3"), appearance).localVersion,
            `is`(
                AppVersionStatus.NONE,
            ),
        )
        assertThat(
            installedAppStatus(app(forum = "3", topic = 0), appearance).localVersion,
            `is`(
                AppVersionStatus.NONE,
            ),
        )
    }

    @Test
    fun playOverridesForumOnlyForAnAvailableUpdateUsingLongCodes() {
        assertThat(
            installedAppStatus(app(forum = "1", marketCode = 3000000000L), appearance).localVersion,
            `is`(
                AppVersionStatus.UPDATE_AVAILABLE,
            ),
        )
        assertThat(
            installedAppStatus(app(code = 5, marketCode = 4), appearance).localVersion,
            `is`(
                AppVersionStatus.NONE,
            ),
        )
        assertThat(
            installedAppStatus(app(forum = "1", code = 5, marketCode = 4), appearance).localVersion,
            `is`(
                AppVersionStatus.INSTALLED_NEWER,
            ),
        )
        assertFalse(installedAppStatus(app(code = 0, marketCode = 30), appearance).playUpdate)
    }

    @Test
    fun exclusionsAndSourceAvailabilityAreIndependent() {
        assertThat(
            installedAppStatus(
                    app(forum = "3", preferences = AppPreferences(ignoreForum = true)),
                    appearance,
                )
                .localVersion,
            `is`(
                AppVersionStatus.NONE,
            ),
        )
        assertThat(
            installedAppStatus(
                    app(
                        forum = "3",
                        marketCode = 5,
                        preferences = AppPreferences(ignoreMarket = true),
                    ),
                    appearance,
                )
                .localVersion,
            `is`(
                AppVersionStatus.UPDATE_AVAILABLE,
            ),
        )
        val play = app(marketCode = 4)
        assertThat(
            installedAppStatus(play, appearance.copy(showPlayUpdates = false)).localVersion,
            `is`(
                AppVersionStatus.NONE,
            ),
        )
        assertThat(
            installedAppStatus(play, appearance, unavailableOnPlay = true).localVersion,
            `is`(
                AppVersionStatus.NONE,
            ),
        )
    }

    @Test
    fun alternativesPrereleasesAndAllVersionComponentsAreCompared() {
        assertThat(
            installedAppStatus(app(forum = "1|2.0|3"), appearance).localVersion,
            `is`(
                AppVersionStatus.NONE,
            ),
        )
        assertThat(
            installedAppStatus(app(local = "2.0 beta", forum = "2.0"), appearance).localVersion,
            `is`(
                AppVersionStatus.UPDATE_AVAILABLE,
            ),
        )
        assertThat(
            installedAppStatus(app(local = "2.1", forum = "2.9"), appearance).localVersion,
            `is`(
                AppVersionStatus.UPDATE_AVAILABLE,
            ),
        )
    }
}
