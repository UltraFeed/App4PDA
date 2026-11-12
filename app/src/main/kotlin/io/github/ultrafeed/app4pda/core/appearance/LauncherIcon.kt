package io.github.ultrafeed.app4pda.core.appearance

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** The launcher owns icon rendering and refresh timing. */
internal fun Context.applyLauncherIcon(accent: Boolean) {
    val useAccent = accent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val blue = ComponentName(this, "$packageName.app.BlueLauncher")
    val themed = ComponentName(this, "$packageName.app.AccentLauncher")
    val selected = if (useAccent) themed else blue
    val other = if (useAccent) blue else themed
    val pm = packageManager
    fun enabled(component: ComponentName): Boolean =
        when (pm.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> component == blue
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            else -> false
        }
    if (enabled(selected) && !enabled(other)) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.setComponentEnabledSettings(
            listOf(
                PackageManager.ComponentEnabledSetting(
                    selected,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                ),
                PackageManager.ComponentEnabledSetting(
                    other,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                ),
            ),
        )
    } else {
        pm.setComponentEnabledSetting(
            selected,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        pm.setComponentEnabledSetting(
            other,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
