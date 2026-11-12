package io.github.ultrafeed.app4pda.app

import android.graphics.Color
import android.os.Build
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowInsetsControllerCompat
import io.github.ultrafeed.app4pda.app.shell.ScreenColors

internal fun MainActivity.applySystemBars(colors: ScreenColors) {
    enableEdgeToEdge(
        statusBarStyle =
            if (colors.darkStatusIcons)
                SystemBarStyle.light(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                )
            else SystemBarStyle.dark(Color.TRANSPARENT),
        navigationBarStyle =
            if (colors.darkNavigationIcons)
                SystemBarStyle.light(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                )
            else SystemBarStyle.dark(Color.TRANSPARENT),
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }
    WindowInsetsControllerCompat(window, window.decorView).apply {
        isAppearanceLightStatusBars = colors.darkStatusIcons
        isAppearanceLightNavigationBars = colors.darkNavigationIcons
    }
}
