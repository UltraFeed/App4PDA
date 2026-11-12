package io.github.ultrafeed.app4pda.core.appearance

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test
    fun systemDefaultAndUnknownValuesHaveNoLegacyAliases() {
        for (value in listOf(null, "", "4", "6", "invalid")) assertThat(
            ThemeMode.parse(value),
            `is`(
                ThemeMode.SYSTEM,
            ),
        )
        assertThat(
            ThemeMode.parse("LIGHT"),
            `is`(
                ThemeMode.LIGHT,
            ),
        )
        assertThat(
            ThemeMode.parse("DARK"),
            `is`(
                ThemeMode.DARK,
            ),
        )
    }

    @Test
    fun explicitModesOverrideTheSystemAndSystemModeFollowsIt() {
        for (systemDark in listOf(false, true)) {
            assertThat(
                ThemeMode.SYSTEM.isDark(systemDark),
                `is`(
                    systemDark,
                ),
            )
            Assert.assertFalse(ThemeMode.LIGHT.isDark(systemDark))
            assertTrue(ThemeMode.DARK.isDark(systemDark))
        }
    }
}
