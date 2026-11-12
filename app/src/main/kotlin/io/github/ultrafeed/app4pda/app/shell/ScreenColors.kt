package io.github.ultrafeed.app4pda.app.shell

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/** System-bar colors and their contrast decisions are derived from the current Material palette. */
internal data class ScreenColors(val statusBar: Int, val navigationBar: Int) {
    val darkStatusIcons
        get() = usesDarkSystemIcons(statusBar)

    val darkNavigationIcons
        get() = usesDarkSystemIcons(navigationBar)
}

internal fun usesDarkSystemIcons(color: Int): Boolean =
    Color(color).luminance() > DARK_ICONS_LUMINANCE_THRESHOLD

internal fun screenColors(scheme: ColorScheme) =
    ScreenColors(scheme.surface.toArgb(), scheme.surface.toArgb())

// Preserve readable system icons over the app's light/dark surfaces.
private const val DARK_ICONS_LUMINANCE_THRESHOLD = 0.5f
