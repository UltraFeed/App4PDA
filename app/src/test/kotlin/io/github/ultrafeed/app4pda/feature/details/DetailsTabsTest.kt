package io.github.ultrafeed.app4pda.feature.details

import io.github.ultrafeed.app4pda.core.network.SourceProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(listOf(DetailsTab.FORUM), details.availableTabs())
    }

    @Test
    fun successfulPagesDetermineOrderRegardlessOfArrivalOrder() {
        val forum =
            sampleDetails().copy(market = null, ruStore = DetailsSection("RuStore", "Description"))
        assertEquals(
            listOf(DetailsTab.FORUM, DetailsTab.RUSTORE),
            forum.availableTabs(),
        )
        val complete = forum.copy(market = DetailsSection("Google Play", "Description"))
        assertEquals(DetailsTab.entries.toList(), complete.availableTabs())
        assertEquals(DetailsTab.RUSTORE, complete.availableTabs().last())
    }

    @Test
    fun storeFailuresHaveNoEmptyPagesAndConfirmedContentSurvivesAWarning() {
        val failed = sampleDetails().copy(market = DetailsSection("Google Play", "", error = true))
        assertFalse(DetailsTab.GOOGLE_PLAY in failed.availableTabs())
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
        assertEquals(
            listOf(DetailsTab.FORUM, DetailsTab.GOOGLE_PLAY),
            cached.availableTabs(),
        )
    }

    @Test
    fun selectedSourceHasAStableSavedIdentityWhenItsPositionChanges() {
        assertEquals(DetailsTab.RUSTORE, DetailsTab.fromSaved(DetailsTab.RUSTORE.savedId))
        assertEquals(DetailsTab.FORUM, DetailsTab.fromSaved(null))
    }
}
