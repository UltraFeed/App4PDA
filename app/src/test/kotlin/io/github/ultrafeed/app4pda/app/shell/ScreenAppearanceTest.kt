package io.github.ultrafeed.app4pda.app.shell

import android.graphics.Color as GraphicsColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.dynamicColorScheme
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenAppearanceTest {
    @Test
    fun systemIconContrastFollowsAdjacentThemeSurfaces() {
        for (dark in listOf(false, true)) {
            val scheme = dynamicColorScheme(Color(0xff123456), dark, false)
            val actual = screenColors(scheme)
            assertThat(
                actual.statusBar,
                `is`(
                    scheme.surface.toArgb(),
                ),
            )
            assertThat(
                actual.navigationBar,
                `is`(
                    scheme.surface.toArgb(),
                ),
            )
        }
    }

    @Test
    fun changingAccentChangesPaletteWithoutChangingThemeMode() {
        val blue = dynamicColorScheme(Color.Blue, false, false)
        val red = dynamicColorScheme(Color.Red, false, false)
        assertThat(
            red.primary,
            not(
                `is`(
                    blue.primary,
                ),
            ),
        )
        assertTrue(screenColors(blue).darkStatusIcons)
        assertTrue(screenColors(red).darkStatusIcons)
    }

    @Test
    fun darkThemeUsesLightSystemIconsForAnyAccent() {
        for (accent in listOf(Color.White, Color.Black, Color.Red)) {
            assertFalse(screenColors(dynamicColorScheme(accent, true, false)).darkStatusIcons)
        }
    }

    @Test
    fun iconContrastUsesStrictLuminanceThreshold() {
        assertFalse(usesDarkSystemIcons(GraphicsColor.BLACK))
        assertTrue(usesDarkSystemIcons(GraphicsColor.WHITE))
        assertFalse(usesDarkSystemIcons(0xff777777.toInt()))
    }
}
