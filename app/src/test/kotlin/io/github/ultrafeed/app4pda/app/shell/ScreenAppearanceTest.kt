package io.github.ultrafeed.app4pda.app.shell

import android.graphics.Color as GraphicsColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.dynamicColorScheme
import io.github.ultrafeed.app4pda.core.ui.theme.usesDarkSystemIcons
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class ScreenAppearanceTest {
    @Test
    fun systemIconContrastFollowsAdjacentThemeSurfaces() {
        for (dark in listOf(false, true)) {
            val scheme = dynamicColorScheme(Color(0xff123456), isDark = dark, isAmoled = false)
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
        val blue = dynamicColorScheme(Color.Blue, isDark = false, isAmoled = false)
        val red = dynamicColorScheme(Color.Red, isDark = false, isAmoled = false)
        assertThat(
            red.primary,
            not(
                `is`(
                    blue.primary,
                ),
            ),
        )
        assertThat(screenColors(blue).darkStatusIcons, `is`(true))
        assertThat(screenColors(red).darkStatusIcons, `is`(true))
    }

    @Test
    fun darkThemeUsesLightSystemIconsForAnyAccent() {
        for (accent in listOf(Color.White, Color.Black, Color.Red)) {
            assertThat(
                screenColors(dynamicColorScheme(accent, isDark = true, isAmoled = false))
                    .darkStatusIcons,
                `is`(false),
            )
        }
    }

    @Test
    fun iconContrastUsesStrictLuminanceThreshold() {
        assertThat(usesDarkSystemIcons(GraphicsColor.BLACK), `is`(false))
        assertThat(usesDarkSystemIcons(GraphicsColor.WHITE), `is`(true))
        assertThat(usesDarkSystemIcons(0xff777777.toInt()), `is`(false))
    }
}
