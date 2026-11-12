package io.github.ultrafeed.app4pda.feature.settings.data

import io.github.ultrafeed.app4pda.data.preferences.SettingsDefaults
import io.github.ultrafeed.app4pda.feature.settings.SettingItem
import io.github.ultrafeed.app4pda.feature.settings.SettingKind

/** The editor and backup use the same choices and numeric bounds. */
internal fun SettingItem.accepts(value: String): Boolean =
    when (kind) {
        SettingKind.CHOICE -> options.any { it.value == value }
        SettingKind.NUMBER -> value.toIntOrNull()?.let { it in numberRange } == true
        SettingKind.SWITCH -> value.toBooleanStrictOrNull() != null
        else -> true
    }

internal fun SettingItem.storedInput(value: String): Any =
    when (kind) {
        SettingKind.SWITCH -> value.toBooleanStrict()
        SettingKind.COLOR -> requireNotNull(SettingsRepository.parseColor(value))
        SettingKind.CHOICE ->
            if (key.storageKey in SettingsDefaults.integers) value.toInt() else value
        else -> value
    }
