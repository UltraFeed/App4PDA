package io.github.ultrafeed.app4pda.app.shell

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import io.github.ultrafeed.app4pda.core.ui.theme.usesDarkSystemIcons

/** System-bar colors and their contrast decisions are derived from the current Material palette. */
internal data class ScreenColors(val statusBar: Int, val navigationBar: Int) {
    val darkStatusIcons
        get() = usesDarkSystemIcons(statusBar)

    val darkNavigationIcons
        get() = usesDarkSystemIcons(navigationBar)
}

internal fun screenColors(scheme: ColorScheme) =
    ScreenColors(scheme.surface.toArgb(), scheme.surface.toArgb())
