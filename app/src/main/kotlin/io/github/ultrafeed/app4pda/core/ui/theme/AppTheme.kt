package io.github.ultrafeed.app4pda.core.ui.theme

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import com.materialkolor.rememberDynamicColorScheme

@Composable
fun AppTheme(
    darkTheme: Boolean,
    accentColor: Int,
    animationsEnabled: Boolean = true,
    useSystemAccent: Boolean = true,
    applySystemBarContrast: Boolean = true,
    content: @Composable () -> Unit,
) {
    val customScheme =
        rememberDynamicColorScheme(
            seedColor = Color(accentColor),
            isDark = darkTheme,
            isAmoled = false,
        )
    val context = LocalContext.current
    val scheme =
        if (useSystemAccent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else customScheme
    val view = LocalView.current
    val activity = LocalActivity.current
    if (!view.isInEditMode)
        SideEffect {
            activity?.window?.setBackgroundDrawable(scheme.surface.toArgb().toDrawable())
            activity
                ?.takeIf { applySystemBarContrast }
                ?.let {
                    val controller = WindowCompat.getInsetsController(it.window, view)
                    val darkIcons =
                        usesDarkSystemIcons(
                            scheme.surface.toArgb(),
                        )
                    controller.isAppearanceLightStatusBars = darkIcons
                    controller.isAppearanceLightNavigationBars = darkIcons
                }
        }
    CompositionLocalProvider(
        LocalAppAnimations provides animationsEnabled,
    ) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = if (animationsEnabled) MotionScheme.expressive() else NoMotionScheme,
            content = content,
        )
    }
}

val LocalAppAnimations: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { true }

internal fun usesDarkSystemIcons(color: Int): Boolean =
    Color(color).luminance() > DARK_ICONS_LUMINANCE_THRESHOLD

private const val DARK_ICONS_LUMINANCE_THRESHOLD = 0.5f

private object NoMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}
