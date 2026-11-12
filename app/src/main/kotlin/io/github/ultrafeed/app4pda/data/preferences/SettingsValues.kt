package io.github.ultrafeed.app4pda.data.preferences

import android.content.Context
import android.content.res.Configuration
import io.github.ultrafeed.app4pda.core.appearance.ThemeMode
import io.github.ultrafeed.app4pda.core.images.ImageCacheSettings
import io.github.ultrafeed.app4pda.core.images.ImageOrigin
import io.github.ultrafeed.app4pda.feature.catalog.CatalogSort
import io.github.ultrafeed.app4pda.feature.installed.InstalledSort
import io.github.ultrafeed.app4pda.feature.recent.data.DigestCoverage

class SettingsValues
internal constructor(private val context: Context, internal val values: Map<String, Any>) {
    val rules: ParsingRules = ParsingRules(values)
    val session: ForumSessionSettings = ForumSessionSettings(values, rules)
    val autoSwitchForumAccount: Boolean
        get() = getBoolean("forum_auto_switch")

    internal fun getBoolean(key: String): Boolean =
        values[key] as? Boolean ?: SettingsDefaults.booleans.getValue(key)

    internal fun getInt(key: String): Int =
        values[key] as? Int ?: SettingsDefaults.integers.getValue(key)

    internal fun getString(key: String): String =
        values[key] as? String ?: SettingsDefaults.strings.getValue(key)

    val themeMode: ThemeMode
        get() = ThemeMode.parse(getString("theme_mode"))

    fun isDarkTheme(
        configuration: Configuration = context.resources.configuration,
    ): Boolean =
        themeMode.isDark(
            configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES,
        )

    val language: String
        get() = getString("language")

    val usesAccentIcon: Boolean
        get() = getString("launcher_icon") == "accent"

    val catalogSortOrder: CatalogSort
        get() =
            CatalogSort.fromStorage(
                getInt("catalog_sorting"),
            )

    val appsSortOrder: InstalledSort
        get() =
            InstalledSort.fromStorage(
                getInt("app_sorting"),
            )

    val isGooglePlayCheckEnabled: Boolean
        get() = getBoolean("google_play_check_ver")

    val isRuStoreCheckEnabled: Boolean
        get() = getBoolean("rustore_check_ver")

    val checksVersionCode: Boolean
        get() = getBoolean("version_code_check")

    val isExtendedLoggingEnabled: Boolean
        get() = getBoolean("extended_log")

    val digestNewIssues: Int
        get() =
            getString("digest_new_issues").toIntOrNull()?.takeIf {
                it in DigestCoverage.ISSUE_COUNTS
            } ?: 1

    val digestUpdatedIssues: Int
        get() =
            getString("digest_updated_issues").toIntOrNull()?.takeIf {
                it in DigestCoverage.ISSUE_COUNTS
            } ?: 1

    fun imageCache(
        origin: ImageOrigin,
    ): ImageCacheSettings {
        val keys =
            when (origin) {
                ImageOrigin.FORUM -> "forum_cache_size"
                ImageOrigin.GOOGLE_PLAY -> "google_play_image_cache_size"
                ImageOrigin.RUSTORE -> "rustore_image_cache_size"
            }
        val limit =
            getString(keys).toIntOrNull()?.takeIf { it in ImageCacheSettings.LIMITS_MIB }
                ?: ImageCacheSettings.DEFAULT_LIMIT_MIB
        return ImageCacheSettings(
            limit > 0,
            limit.toLong() * MEBIBYTE_BYTES,
        )
    }

    val downloadsOpenMode: String
        get() = getString("open_downloads")
}

private const val MEBIBYTE_BYTES = 1_048_576L
