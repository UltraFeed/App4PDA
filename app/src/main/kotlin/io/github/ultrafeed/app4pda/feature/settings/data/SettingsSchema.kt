package io.github.ultrafeed.app4pda.feature.settings.data

import android.content.Context
import android.content.pm.ApplicationInfo
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.appearance.ThemeMode
import io.github.ultrafeed.app4pda.core.images.ImageCacheSettings
import io.github.ultrafeed.app4pda.feature.installed.InstalledSort
import io.github.ultrafeed.app4pda.feature.recent.data.DigestCoverage
import io.github.ultrafeed.app4pda.feature.settings.SettingAction
import io.github.ultrafeed.app4pda.feature.settings.SettingId
import io.github.ultrafeed.app4pda.feature.settings.SettingItem
import io.github.ultrafeed.app4pda.feature.settings.SettingKind
import io.github.ultrafeed.app4pda.feature.settings.SettingOption
import io.github.ultrafeed.app4pda.feature.settings.SettingsPage
import java.util.concurrent.TimeUnit

/**
 * Explicit settings definitions; the retired Preference XML is not inflated or parsed at runtime.
 */
internal object SettingsSchema {
    fun create(context: Context): List<SettingItem> =
        with(context) {
            listOf(
                    rootSettings(),
                    appearanceSettings(),
                    sourcesSettings(),
                    cacheSettings(),
                    backupSettings(),
                    notificationsSettings(),
                    downloadsSettings(),
                    advancedSettings(),
                )
                .flatten()
        }
}

private fun Context.rootSettings(): List<SettingItem> =
    listOf(
            SettingId.UI to SettingsPage.APPEARANCE,
            SettingId.ACCOUNTS to SettingsPage.ACCOUNTS,
            SettingId.SOURCES to SettingsPage.SOURCES,
            SettingId.NOTIFY to SettingsPage.NOTIFICATIONS,
            SettingId.DOWNLOAD to SettingsPage.DOWNLOADS,
            SettingId.CACHE to SettingsPage.CACHE,
            SettingId.ADVANCED to SettingsPage.ADVANCED,
        )
        .flatMap { (id, page) ->
            val screen =
                SettingItem(
                    id,
                    SettingsPage.ROOT,
                    getString(page.title),
                    SettingKind.SCREEN,
                    destination = page,
                )
            if (page == SettingsPage.ACCOUNTS)
                listOf(
                    screen,
                    SettingItem(
                        SettingId.HIDDEN,
                        SettingsPage.ROOT,
                        getString(R.string.hidden),
                        SettingKind.ACTION,
                        action = SettingAction.HIDDEN,
                    ),
                )
            else listOf(screen)
        }

private fun Context.sortingSetting() =
    SettingItem(
        SettingId.APP_SORTING,
        SettingsPage.APPEARANCE,
        getString(R.string.app_sorting_title),
        SettingKind.CHOICE,
        options =
            InstalledSort.entries.map { order ->
                SettingOption(order.storageValue.toString(), getString(order.title))
            },
    )

private fun Context.appearanceSettings(): List<SettingItem> =
    listOf(
        SettingItem(
            SettingId.LAUNCHER_ICON,
            SettingsPage.APPEARANCE,
            getString(R.string.launcher_icon),
            SettingKind.CHOICE,
            options =
                listOf(
                    SettingOption("blue", getString(R.string.launcher_icon_blue)),
                    SettingOption("accent", getString(R.string.launcher_icon_accent)),
                ),
        ),
        SettingItem(
            SettingId.LANGUAGE,
            SettingsPage.APPEARANCE,
            getString(R.string.settings_main_language),
            SettingKind.CHOICE,
            options = options(R.array.language, R.array.language_value),
        ),
        SettingItem(
            SettingId.THEME_MODE,
            SettingsPage.APPEARANCE,
            getString(R.string.settings_ui_theme),
            SettingKind.CHOICE,
            options =
                listOf(
                    SettingOption(ThemeMode.SYSTEM.name, getString(R.string.system_default)),
                    SettingOption(ThemeMode.LIGHT.name, getString(R.string.theme_mode_light)),
                    SettingOption(ThemeMode.DARK.name, getString(R.string.theme_mode_dark)),
                ),
        ),
        SettingItem(
            SettingId.USE_SYSTEM_ACCENT,
            SettingsPage.APPEARANCE,
            getString(R.string.use_system_accent),
            SettingKind.SWITCH,
            summary = getString(R.string.use_system_accent_summary),
        ),
        SettingItem(
            SettingId.UI_THEME_COLOR,
            SettingsPage.APPEARANCE,
            getString(R.string.settings_ui_theme_color),
            SettingKind.COLOR,
            summary = getString(R.string.settings_ui_theme_color_ext),
        ),
        SettingItem(
            SettingId.HIDE_NAVIGATION_LABELS,
            SettingsPage.APPEARANCE,
            getString(R.string.hide_navigation_labels),
            SettingKind.SWITCH,
        ),
        SettingItem(
            SettingId.UI_ANIMATION,
            SettingsPage.APPEARANCE,
            getString(R.string.settings_ui_animation),
            SettingKind.SWITCH,
        ),
        sortingSetting(),
    )

private fun Context.sourcesSettings(): List<SettingItem> =
    listOf(
        SettingItem(
            SettingId.GOOGLE_PLAY_CHECK_VER,
            SettingsPage.SOURCES,
            getString(R.string.settings_google_play_check_ver),
            SettingKind.SWITCH,
            summary = getString(R.string.play_check_updates_summary),
        ),
        SettingItem(
            SettingId.RUSTORE_CHECK_VER,
            SettingsPage.SOURCES,
            getString(R.string.rustore_check_updates),
            SettingKind.SWITCH,
            summary = getString(R.string.rustore_check_updates_summary),
        ),
        SettingItem(
            SettingId.VERSION_CODE_CHECK,
            SettingsPage.SOURCES,
            getString(R.string.settings_forum_version_code_check),
            SettingKind.SWITCH,
            summary = getString(R.string.settings_forum_version_code_check_ext),
        ),
        SettingItem(
            SettingId.DIGEST_NEW_ISSUES,
            SettingsPage.SOURCES,
            getString(R.string.digest_new_issues),
            SettingKind.CHOICE,
            options = DigestCoverage.ISSUE_COUNTS.map { SettingOption("$it", "$it") },
            summary = getString(R.string.digest_issues_summary),
        ),
        SettingItem(
            SettingId.DIGEST_UPDATED_ISSUES,
            SettingsPage.SOURCES,
            getString(R.string.digest_updated_issues),
            SettingKind.CHOICE,
            options = DigestCoverage.ISSUE_COUNTS.map { SettingOption("$it", "$it") },
            summary = getString(R.string.digest_issues_summary),
        ),
        SettingItem(
            SettingId.BACKGROUND_UPDATES,
            SettingsPage.SOURCES,
            getString(R.string.background_updates),
            SettingKind.SWITCH,
        ),
        SettingItem(
            SettingId.BACKGROUND_UPDATE_INTERVAL,
            SettingsPage.SOURCES,
            getString(R.string.background_update_interval),
            SettingKind.CHOICE,
            dependency = SettingId.BACKGROUND_UPDATES,
            options = updateIntervals(),
        ),
    )

private fun Context.cacheSettings(): List<SettingItem> {
    val limits =
        ImageCacheSettings.LIMITS_MIB.map { limit ->
            SettingOption(
                "$limit",
                if (limit == 0) getString(R.string.image_cache_disabled_option)
                else getString(R.string.image_cache_limit_option, limit),
            )
        }
    return listOf(
        SettingItem(
            SettingId.FORUM_CACHE_SIZE,
            SettingsPage.CACHE,
            getString(R.string.settings_forum_cache_size),
            SettingKind.CHOICE,
            options = limits,
        ),
        SettingItem(
            SettingId.PLAY_IMAGE_CACHE_SIZE,
            SettingsPage.CACHE,
            getString(R.string.play_image_cache_size),
            SettingKind.CHOICE,
            options = limits,
        ),
        SettingItem(
            SettingId.RUSTORE_IMAGE_CACHE_SIZE,
            SettingsPage.CACHE,
            getString(R.string.rustore_image_cache_size),
            SettingKind.CHOICE,
            options = limits,
        ),
        SettingItem(
            SettingId.CLEAR_CACHE,
            SettingsPage.CACHE,
            getString(R.string.clear_app_cache),
            SettingKind.ACTION,
            action = SettingAction.CLEAR_CACHE,
        ),
    )
}

private fun Context.backupSettings(): List<SettingItem> =
    listOf(
        SettingItem(
            SettingId.EXPORT_BACKUP,
            SettingsPage.CACHE,
            getString(R.string.export_backup),
            SettingKind.ACTION,
            action = SettingAction.EXPORT_BACKUP,
        ),
        SettingItem(
            SettingId.IMPORT_BACKUP,
            SettingsPage.CACHE,
            getString(R.string.import_backup),
            SettingKind.ACTION,
            action = SettingAction.IMPORT_BACKUP,
        ),
    )

private fun Context.notificationsSettings(): List<SettingItem> =
    listOf(
        SettingItem(
            SettingId.NOTIFY_ENABLE,
            SettingsPage.NOTIFICATIONS,
            getString(R.string.settings_notify_enable),
            SettingKind.SWITCH,
        ),
        SettingItem(
            SettingId.NOTIFICATION_SETTINGS,
            SettingsPage.NOTIFICATIONS,
            getString(R.string.system_notification_settings),
            SettingKind.ACTION,
            action = SettingAction.NOTIFICATION_SETTINGS,
        ),
    )

private fun Context.downloadsSettings(): List<SettingItem> =
    listOf(
        SettingItem(
            SettingId.OPEN_DOWNLOADS,
            SettingsPage.DOWNLOADS,
            getString(R.string.settings_downloads_open_downloads),
            SettingKind.CHOICE,
            summary = getString(R.string.settings_downloads_open_downloads_ext_ask),
            options = options(R.array.open_downloads, R.array.open_downloads_value),
        ),
        SettingItem(
            SettingId.NO_CALC_SIGNATYRE_APP_BEFOR_INSTALL,
            SettingsPage.DOWNLOADS,
            getString(R.string.settings_downloads_no_calc_signature_app_before_install),
            SettingKind.SWITCH,
            summary =
                getString(R.string.settings_downloads_no_calc_signature_app_before_install_ext),
        ),
    )

private fun Context.advancedSettings(): List<SettingItem> =
    listOf(
        SettingItem(
            SettingId.EXTENDED_LOG,
            SettingsPage.ADVANCED,
            getString(R.string.settings_extended_extended_log),
            SettingKind.SWITCH,
            summary = getString(R.string.settings_need_restart),
        ),
        SettingItem(
            SettingId.SEND_LOG,
            SettingsPage.ADVANCED,
            getString(R.string.settings_extended_send_log),
            SettingKind.ACTION,
            summary = getString(R.string.settings_extended_send_log_ext),
            action = SettingAction.SEND_LOG,
        ),
        SettingItem(
            SettingId.GITHUB,
            SettingsPage.ADVANCED,
            getString(R.string.source_github),
            SettingKind.ACTION,
            action = SettingAction.GITHUB,
        ),
        SettingItem(
            SettingId.CHECK_SELF_UPDATE,
            SettingsPage.ADVANCED,
            getString(R.string.self_update_check),
            SettingKind.ACTION,
            enabled = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0,
            action = SettingAction.CHECK_SELF_UPDATE,
        ),
    )

private fun Context.options(titles: Int, values: Int): List<SettingOption> =
    resources.getStringArray(values).zip(resources.getStringArray(titles)) { value, title ->
        SettingOption(
            value,
            title,
        )
    }

private fun Context.updateIntervals(): List<SettingOption> =
    listOf("15", "60", "360", "1440").map { value ->
        val minutes = value.toInt()
        val hours = TimeUnit.MINUTES.toHours(minutes.toLong())
        val title =
            if (hours > 0) getString(R.string.duration_hours, hours)
            else getString(R.string.format_time_minutes, minutes)
        SettingOption(value, title)
    }
