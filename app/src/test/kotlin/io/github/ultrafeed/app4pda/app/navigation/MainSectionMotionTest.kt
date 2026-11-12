package io.github.ultrafeed.app4pda.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainSectionMotionTest {
    @Test
    fun everyCrossSectionDirectionUsesThePanelOrderForPushAndPop() {
        MainSection.entries.forEachIndexed { initialIndex, initial ->
            MainSection.entries.forEachIndexed { targetIndex, target ->
                if (initial != target)
                    listOf(false, true).forEach { pop ->
                        assertEquals(
                            targetIndex > initialIndex,
                            mainNavigationForward(initial, target, pop),
                        )
                    }
            }
        }
        assertFalse(mainNavigationForward(MainSection.CATALOG, MainSection.DIGEST, false))
    }

    @Test
    fun nestedNavigationUsesPushAndPopWithoutChangingSectionOwnership() {
        MainSection.entries.forEach {
            assertTrue(mainNavigationForward(it, it, false))
            assertFalse(mainNavigationForward(it, it, true))
        }
    }
}
