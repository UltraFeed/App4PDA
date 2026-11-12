package io.github.ultrafeed.app4pda.core.ui

import androidx.lifecycle.SavedStateHandle

/** Only a successful, complete source may discard keys missing from the current page. */
internal class SavedSelection(private val saved: SavedStateHandle, private val key: String) {
    val changes = saved.getStateFlow(key, arrayListOf<String>())
    val value: Set<String>
        get() = changes.value.toSet()

    fun replace(keys: Collection<String>): Set<String> =
        keys.toSet().also {
            saved[key] = ArrayList(it)
        }

    fun toggle(key: String): Set<String> = replace(if (key in value) value - key else value + key)

    fun retain(keys: Set<String>): Set<String> = replace(value intersect keys)
}
