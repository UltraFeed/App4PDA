package io.github.ultrafeed.app4pda.feature.settings.data

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.core.graphics.toColorInt
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.AppPolicy
import io.github.ultrafeed.app4pda.core.formatting.DisplayFormats
import io.github.ultrafeed.app4pda.core.images.ImageOrigin
import io.github.ultrafeed.app4pda.data.preferences.AppSettings
import io.github.ultrafeed.app4pda.data.preferences.SettingsDefaults
import io.github.ultrafeed.app4pda.data.preferences.SettingsValues
import io.github.ultrafeed.app4pda.feature.installed.InstalledCardAppearance
import io.github.ultrafeed.app4pda.feature.installed.data.UpdateSchedule
import io.github.ultrafeed.app4pda.feature.reports.data.CrashReports
import io.github.ultrafeed.app4pda.feature.settings.SettingId
import io.github.ultrafeed.app4pda.feature.settings.SettingItem
import io.github.ultrafeed.app4pda.feature.settings.SettingKind
import io.github.ultrafeed.app4pda.feature.settings.SettingsSnapshot
import java.io.File
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SettingsRepository(
    context: Context,
    private val settings: AppSettings,
    private val cache: AppCacheMaintenance,
    private val crashReports: CrashReports,
) : AutoCloseable {
    private val context = context.applicationContext
    private val definitions
        get() = SettingsSchema.create(this.context)

    private var cacheBytes = 0L
    private var imageBytes: Map<ImageOrigin, Long?>? = null
    private val mutableSnapshots: MutableStateFlow<SettingsSnapshot>
    val snapshots: StateFlow<SettingsSnapshot>
        get() = mutableSnapshots.asStateFlow()

    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Main.immediate,
        )

    private var systemConfiguration = Configuration(this.context.resources.configuration)
    private val configurationListener =
        object : ComponentCallbacks {
            override fun onConfigurationChanged(configuration: Configuration) {
                systemConfiguration = Configuration(configuration)
                refresh()
            }

            override fun onLowMemory() = Unit
        }

    init {
        mutableSnapshots = MutableStateFlow(snapshot())
        scope.launch { settings.changes.collect { mutableSnapshots.value = snapshot(it) } }
        this.context.registerComponentCallbacks(configurationListener)
    }

    fun refresh() {
        mutableSnapshots.value = snapshot()
    }

    internal fun snapshot(values: SettingsValues = settings.current): SettingsSnapshot {
        fun checked(key: SettingId) = values.getBoolean(key.storageKey)
        val themeMode = values.themeMode
        val items = definitions.map { resolveItem(it, values) }
        // Apply the same availability rules to dependent settings.
        val byKey = items.associateBy { it.key }
        fun available(item: SettingItem): Boolean =
            item.enabled &&
                (item.dependency?.let {
                    byKey[it]?.let { parent -> parent.value.toBoolean() && available(parent) }
                } ?: true)
        return SettingsSnapshot(
            context.getString(R.string.settings),
            items.map { it.copy(enabled = available(it)) },
            themeMode = themeMode,
            darkTheme = values.isDarkTheme(systemConfiguration),
            accentColor = values.getInt("ui_theme_color"),
            animationsEnabled = checked(SettingId.UI_ANIMATION),
            useSystemAccent = checked(SettingId.USE_SYSTEM_ACCENT),
            installedCardAppearance =
                InstalledCardAppearance(
                    showPlayUpdates = values.isGooglePlayCheckEnabled,
                    showRuStoreUpdates = values.isRuStoreCheckEnabled,
                ),
        )
    }

    private fun resolveItem(item: SettingItem, values: SettingsValues): SettingItem {
        fun checked(key: SettingId) = values.getBoolean(key.storageKey)

        val current = item.currentValue(values)
        var summary = imageCacheSummary(item, item.summaryFor(current))
        var enabled = item.dependency?.let { checked(it) } ?: true
        when (item.key) {
            SettingId.LAUNCHER_ICON -> enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            SettingId.UI_THEME_COLOR ->
                enabled =
                    !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        checked(SettingId.USE_SYSTEM_ACCENT))

            SettingId.SEND_LOG -> enabled = values.isExtendedLoggingEnabled
            SettingId.CLEAR_CACHE -> {
                val bytes = cacheBytes
                summary =
                    context.getString(
                        R.string.settings_extended_service_clear_cache_ext,
                        DisplayFormats.fileSize(context, bytes),
                    )
                enabled = bytes > 0
            }

            else -> Unit
        }
        if (!enabled) summary = unavailableSummary(item, summary)
        val confirmation =
            when (item.key) {
                SettingId.CLEAR_CACHE -> context.getString(R.string.clear_app_cache_confirmation)

                else -> null
            }
        return item.copy(
            value = current,
            summary = summary,
            enabled = enabled,
            visible = true,
            confirmation = confirmation,
        )
    }

    private fun imageCacheSummary(item: SettingItem, summary: String): String {
        val origin = item.imageOrigin() ?: return summary
        val sizes = imageBytes
        val bytes = sizes?.get(origin)
        val usage =
            when {
                sizes == null -> context.getString(R.string.cache_size_loading)
                bytes == null -> context.getString(R.string.cache_size_unavailable)
                else ->
                    context.getString(
                        R.string.image_cache_usage,
                        DisplayFormats.fileSize(context, bytes),
                    )
            }
        return "$summary · $usage"
    }

    private fun unavailableSummary(item: SettingItem, fallback: String): String {
        val required =
            item.dependency ?: if (item.key == SettingId.SEND_LOG) SettingId.EXTENDED_LOG else null
        return when {
            item.key == SettingId.LAUNCHER_ICON ->
                context.getString(R.string.launcher_icon_unavailable)
            item.key == SettingId.UI_THEME_COLOR ->
                context.getString(R.string.custom_accent_disabled)
            required != null ->
                context.getString(
                    R.string.setting_requires,
                    definitions.first { it.key == required }.title,
                )

            else -> fallback
        }
    }

    suspend fun setValue(key: SettingId, value: String): String? {
        val item =
            snapshots.value.items.find { it.key == key && it.visible && it.enabled }
                ?: return context.getString(R.string.action_failed)
        val invalid =
            when {
                !item.accepts(value) -> R.string.settings_invalid_value
                item.kind == SettingKind.COLOR && parseColor(value) == null ->
                    R.string.settings_color_input_hint
                else -> null
            }
        return if (invalid != null) context.getString(invalid)
        else {
            settings.writeValue(key.storageKey, item.storedInput(value))
            if (key == SettingId.BACKGROUND_UPDATES || key == SettingId.BACKGROUND_UPDATE_INTERVAL)
                UpdateSchedule.configure(
                    context,
                    settings.current,
                )
            refresh()
            if (item.imageOrigin() != null) refreshCacheSizes()
            null
        }
    }

    suspend fun refreshCacheSizes() {
        imageBytes = cache.imageSizes()
        refresh()
        cacheBytes = cache.size()
        refresh()
    }

    suspend fun clearCache() {
        val completed =
            withTimeoutOrNull(
                AppPolicy.CACHE_CLEAR_TIMEOUT_MILLIS.milliseconds,
            ) {
                cache.clear()
                refreshCacheSizes()
                true
            }
        check(completed == true) { context.getString(R.string.cache_clear_incomplete) }
    }

    suspend fun prepareSessionLog(): File = crashReports.prepareSessionLog()

    override fun close() {
        scope.cancel()
        context.unregisterComponentCallbacks(configurationListener)
    }

    companion object {
        internal fun parseColor(value: String): Int? =
            try {
                value.trim().let { if (it.startsWith('#')) it else "#$it" }.toColorInt()
            } catch (_: IllegalArgumentException) {
                null
            }
    }
}

internal fun SettingItem.currentValue(values: SettingsValues): String {
    val fallback = SettingsDefaults.text(key.storageKey)
    return when (kind) {
        SettingKind.SWITCH -> values.getBoolean(key.storageKey).toString()
        SettingKind.COLOR -> values.getInt(key.storageKey).toString()
        SettingKind.CHOICE ->
            (if (key.storageKey in SettingsDefaults.integers)
                    values.getInt(key.storageKey).toString()
                else values.getString(key.storageKey))
                .takeIf { value ->
                    options.any { it.value == value }
                } ?: fallback

        SettingKind.NUMBER ->
            values.getString(key.storageKey).takeIf { it.toIntOrNull() != null } ?: fallback

        else -> ""
    }
}

internal fun SettingItem.imageOrigin(): ImageOrigin? =
    when (key) {
        SettingId.FORUM_CACHE_SIZE -> ImageOrigin.FORUM
        SettingId.PLAY_IMAGE_CACHE_SIZE -> ImageOrigin.GOOGLE_PLAY
        SettingId.RUSTORE_IMAGE_CACHE_SIZE -> ImageOrigin.RUSTORE
        else -> null
    }

internal fun SettingItem.summaryFor(current: String): String =
    when (kind) {
        SettingKind.NUMBER ->
            listOf(current, summary).filter(String::isNotBlank).joinToString(" · ")
        SettingKind.CHOICE -> options.find { it.value == current }?.title.orEmpty()
        SettingKind.COLOR ->
            current.toIntOrNull()?.let { String.format(Locale.ROOT, "#%08X", it) }.orEmpty()

        else -> summary
    }
