package io.github.ultrafeed.app4pda.app.navigation

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class MainSectionMotionTest {
    @Test
    fun everyCrossSectionDirectionUsesThePanelOrderForPushAndPop() {
        MainSection.entries.forEachIndexed { initialIndex, initial ->
            MainSection.entries.forEachIndexed { targetIndex, target ->
                if (initial != target)
                    listOf(false, true).forEach { pop ->
                        assertThat(
                            mainNavigationForward(initial, target, pop),
                            `is`(targetIndex > initialIndex),
                        )
                    }
            }
        }
        assertThat(
            mainNavigationForward(MainSection.CATALOG, MainSection.DIGEST, pop = false),
            `is`(false),
        )
    }

    @Test
    fun nestedNavigationUsesPushAndPopWithoutChangingSectionOwnership() {
        MainSection.entries.forEach {
            assertThat(mainNavigationForward(it, it, pop = false), `is`(true))
            assertThat(mainNavigationForward(it, it, pop = true), `is`(false))
        }
    }
}
