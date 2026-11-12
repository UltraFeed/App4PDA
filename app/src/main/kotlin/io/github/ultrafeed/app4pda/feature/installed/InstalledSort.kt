package io.github.ultrafeed.app4pda.feature.installed

import io.github.ultrafeed.app4pda.R

enum class InstalledSort(val storageValue: Int, val title: Int) {
    UPDATED(0, R.string.sorting_by_date_update),
    NAME(1, R.string.sorting_by_name),
    UPDATES_BY_DATE(
        2,
        R.string.sorting_by_update_date,
    ),
    UPDATES_BY_NAME(UPDATES_BY_NAME_STORAGE_CODE, R.string.sorting_by_update_name);

    companion object {
        fun fromStorage(value: Int): InstalledSort =
            entries.find { it.storageValue == value } ?: NAME
    }
}

// Persisted setting value; do not derive it from enum order.
private const val UPDATES_BY_NAME_STORAGE_CODE = 3
