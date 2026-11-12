package io.github.ultrafeed.app4pda.feature.details

import io.github.ultrafeed.app4pda.core.network.SourceProblem
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class DetailsTabsTest {
    private fun sampleDetails() =
        AppDetails(
            42,
            "example",
            "Example",
            "1",
            DetailsSection("4PDA", "Description"),
            DetailsSection("Google Play", "Description"),
            emptyList(),
            "",
            "",
            false,
        )

    @Test
    fun unconfirmedStoresDoNotCreateTabs() {
        val details = sampleDetails().copy(market = null, ruStore = null)
        assertThat(
            details.availableTabs(),
            `is`(
                listOf(
                    DetailsTab.FORUM,
                ),
            ),
        )
    }

    @Test
    fun successfulPagesDetermineOrderRegardlessOfArrivalOrder() {
        val forum =
            sampleDetails().copy(market = null, ruStore = DetailsSection("RuStore", "Description"))
        assertThat(
            forum.availableTabs(),
            `is`(
                listOf(
                    DetailsTab.FORUM,
                    DetailsTab.RUSTORE,
                ),
            ),
        )
        val complete = forum.copy(market = DetailsSection("Google Play", "Description"))
        assertThat(
            complete.availableTabs(),
            `is`(
                DetailsTab.entries.toList(),
            ),
        )
        assertThat(
            complete.availableTabs().last(),
            `is`(
                DetailsTab.RUSTORE,
            ),
        )
    }

    @Test
    fun storeFailuresHaveNoEmptyPagesAndConfirmedContentSurvivesAWarning() {
        val failed = sampleDetails().copy(market = DetailsSection("Google Play", "", error = true))
        assertThat(DetailsTab.GOOGLE_PLAY in failed.availableTabs(), `is`(false))
        val cached =
            failed.copy(
                market =
                    DetailsSection(
                        "Google Play",
                        "Last successful description",
                        problem =
                            SourceProblem(
                                "PLAY · HTTP_429",
                                "Wait",
                            ),
                    ),
            )
        assertThat(
            cached.availableTabs(),
            `is`(
                listOf(
                    DetailsTab.FORUM,
                    DetailsTab.GOOGLE_PLAY,
                ),
            ),
        )
    }

    @Test
    fun selectedSourceHasAStableSavedIdentityWhenItsPositionChanges() {
        assertThat(
            DetailsTab.fromSaved(DetailsTab.RUSTORE.savedId),
            `is`(
                DetailsTab.RUSTORE,
            ),
        )
        assertThat(
            DetailsTab.fromSaved(null),
            `is`(
                DetailsTab.FORUM,
            ),
        )
    }
}
