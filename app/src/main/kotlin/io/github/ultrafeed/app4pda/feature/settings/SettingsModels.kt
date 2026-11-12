package io.github.ultrafeed.app4pda.feature.settings

import androidx.annotation.StringRes
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.appearance.AppearanceDefaults
import io.github.ultrafeed.app4pda.core.appearance.ThemeMode
import io.github.ultrafeed.app4pda.feature.installed.InstalledCardAppearance
import kotlinx.serialization.Serializable

@Serializable
enum class SettingsPage(@StringRes val title: Int) {
    ROOT(R.string.settings),
    APPEARANCE(R.string.settings_ui),
    SOURCES(R.string.settings_sources),
    DOWNLOADS(R.string.settings_download_install),
    NOTIFICATIONS(R.string.settings_notify),
    CACHE(R.string.settings_data_cache),
    ADVANCED(R.string.settings_diagnostics),
    ACCOUNTS(R.string.forum_accounts),
}

enum class SettingAction {
    HIDDEN,
    NOTIFICATION_SETTINGS,
    GITHUB,
    CHECK_SELF_UPDATE,
    SEND_LOG,
    CLEAR_CACHE,
    EXPORT_BACKUP,
    IMPORT_BACKUP,
}

/** Stable storage names are retained; UI code uses the enum identity. */
enum class SettingId(val storageKey: String) {
    ACCOUNTS("accounts"),
    HIDDEN("hidden"),
    APP_SORTING("app_sorting"),
    LANGUAGE("language"),
    LAUNCHER_ICON("launcher_icon"),
    UI("ui"),
    THEME_MODE("theme_mode"),
    USE_SYSTEM_ACCENT("use_system_accent"),
    HIDE_NAVIGATION_LABELS("hide_navigation_labels"),
    UI_THEME_COLOR("ui_theme_color"),
    UI_ANIMATION("ui_animation"),
    VERSION_CODE_CHECK("version_code_check"),
    RUSTORE_CHECK_VER("rustore_check_ver"),
    GOOGLE_PLAY_CHECK_VER("google_play_check_ver"),
    NOTIFY("notify"),
    DIGEST_NEW_ISSUES("digest_new_issues"),
    DIGEST_UPDATED_ISSUES("digest_updated_issues"),
    BACKGROUND_UPDATES("background_updates"),
    BACKGROUND_UPDATE_INTERVAL("background_update_interval"),
    NOTIFY_ENABLE("notify_enable"),
    NOTIFICATION_SETTINGS("notification_settings"),
    DOWNLOAD("download"),
    OPEN_DOWNLOADS("open_downloads"),
    NO_CALC_SIGNATYRE_APP_BEFOR_INSTALL("no_calc_signatyre_app_befor_install"),
    EXTENDED_LOG("extended_log"),
    SEND_LOG("send_log"),
    EXPORT_BACKUP("export_backup"),
    IMPORT_BACKUP("import_backup"),
    FORUM_CACHE_SIZE("forum_cache_size"),
    PLAY_IMAGE_CACHE_SIZE("google_play_image_cache_size"),
    RUSTORE_IMAGE_CACHE_SIZE("rustore_image_cache_size"),
    CLEAR_CACHE("clear_cache"),
    GITHUB("github"),
    CHECK_SELF_UPDATE("check_self_update"),
    SOURCES("sources"),
    CACHE("cache"),
    ADVANCED("diagnostics");

    override fun toString(): String = storageKey
}

enum class SettingKind {
    SCREEN,
    CATEGORY,
    SWITCH,
    CHOICE,
    TEXT,
    NUMBER,
    COLOR,
    ACTION,
}

data class SettingOption(val value: String, val title: String)

data class SettingItem(
    val key: SettingId,
    val parent: SettingsPage,
    val title: String,
    val kind: SettingKind,
    val summary: String = "",
    val value: String = "",
    val options: List<SettingOption> = emptyList(),
    val dependency: SettingId? = null,
    val enabled: Boolean = true,
    val visible: Boolean = true,
    val confirmation: String? = null,
    val destination: SettingsPage? = null,
    val action: SettingAction? = null,
    val numberRange: IntRange = 1..Int.MAX_VALUE,
)

data class SettingsSnapshot(
    val title: String,
    val items: List<SettingItem>,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val darkTheme: Boolean = false,
    val accentColor: Int = AppearanceDefaults.ACCENT,
    val animationsEnabled: Boolean = true,
    val useSystemAccent: Boolean = true,
    val installedCardAppearance: InstalledCardAppearance = InstalledCardAppearance(),
) {
    fun screen(key: SettingsPage): List<SettingItem> = items.filter {
        it.parent == key && it.visible
    }
}
