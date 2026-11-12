package io.github.ultrafeed.app4pda.data.preferences

import io.github.ultrafeed.app4pda.core.appearance.AppearanceDefaults
import io.github.ultrafeed.app4pda.core.appearance.ThemeMode
import io.github.ultrafeed.app4pda.feature.catalog.CatalogSort
import io.github.ultrafeed.app4pda.feature.installed.InstalledSort

/** Defaults retain the existing DataStore types; numeric strings are intentional. */
internal object SettingsDefaults {
    val booleans =
        mapOf(
            "use_system_accent" to true,
            "ui_animation" to true,
            "version_code_check" to true,
            "hide_navigation_labels" to true,
            "google_play_check_ver" to true,
            "rustore_check_ver" to true,
            "background_updates" to true,
            "notify_enable" to true,
            "no_calc_signatyre_app_befor_install" to false,
            "extended_log" to false,
            "forum_auto_switch" to true,
        )
    val integers =
        mapOf(
            "app_sorting" to InstalledSort.NAME.storageValue,
            "catalog_sorting" to CatalogSort.NAME.storageValue,
            "ui_theme_color" to AppearanceDefaults.ACCENT,
        )
    val strings =
        mapOf(
            "digest_new_issues" to "1",
            "digest_updated_issues" to "1",
            "language" to "default",
            "launcher_icon" to "blue",
            "theme_mode" to ThemeMode.SYSTEM.name,
            "background_update_interval" to "60",
            "open_downloads" to "ask",
            "forum_cache_size" to "100",
            "google_play_image_cache_size" to "100",
            "rustore_image_cache_size" to "100",
        )

    fun text(key: String): String =
        booleans[key]?.toString() ?: integers[key]?.toString() ?: strings[key].orEmpty()
}
