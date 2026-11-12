package io.github.ultrafeed.app4pda.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import kotlinx.serialization.Serializable

@Serializable
internal enum class MainSection(val root: AppDestination) {
    HOME(AppDestination.Home),
    DIGEST(AppDestination.Digest),
    CATALOG(AppDestination.Categories),
    DOWNLOADS(AppDestination.Downloads),
    SETTINGS(AppDestination.Settings());

    companion object {
        fun forExternal(destination: AppDestination): MainSection =
            when (destination) {
                AppDestination.Home,
                AppDestination.Empty,
                is AppDestination.Apps -> HOME

                AppDestination.Digest -> DIGEST
                AppDestination.Downloads -> DOWNLOADS
                is AppDestination.Settings,
                is AppDestination.Auth -> SETTINGS

                else -> CATALOG
            }
    }
}

/** Owns the five real NavBackStacks, never a mirrored list of navigation entries. */
internal class MainNavigation(
    selected: MutableState<MainSection>,
    val stacks: Map<MainSection, NavBackStack<NavKey>>,
) {
    var selected by selected
        private set

    private val reselections = mutableStateMapOf<MainSection, Long>()

    fun reselection(section: MainSection): Long = reselections[section] ?: 0L

    fun reselection(entry: AppEntry, root: AppEntry): Long =
        if (entry.id == root.id) reselection(MainSection.forExternal(root.destination)) else 0L

    val stack: NavBackStack<NavKey>
        get() = stacks.getValue(selected)

    val current: AppEntry
        get() = stack.last() as AppEntry

    val atRoot: Boolean
        get() = stack.size == 1

    fun select(section: MainSection) {
        if (selected == section) {
            reset(section)
            reselections[section] = reselection(section) + 1
        } else selected = section
    }

    private fun reset(section: MainSection) {
        val entries = stacks.getValue(section)
        while (entries.size > 1) entries.removeAt(entries.lastIndex)
    }

    /** False means Back at Home's root should finish the Activity. */
    fun back(): Boolean {
        val popped = stack.pop()
        val returnHome = !popped && selected != MainSection.HOME
        if (returnHome) {
            reset(MainSection.HOME)
            selected = MainSection.HOME
        }
        return popped || returnHome
    }

    fun open(destination: AppDestination) {
        // A manual-link search stays with its caller. A normal Home search belongs to Catalog.
        if (
            (destination is AppDestination.Catalog) &&
                (destination.linked == null) &&
                (selected == MainSection.HOME)
        )
            selected = MainSection.CATALOG
        if (destination == AppDestination.Downloads) {
            selected = MainSection.DOWNLOADS
            reset(selected)
            return
        }
        if (destination is AppDestination.Attachment && current.destination == destination) return
        stack.push(destination)
    }

    fun external(destination: AppDestination) {
        selected = MainSection.forExternal(destination)
        if (destination == selected.root) reset(selected) else open(destination)
    }
}

@Composable
internal fun rememberMainNavigation(initial: AppDestination): MainNavigation {
    val initialSection = MainSection.forExternal(initial)
    val selected = rememberSaveable { mutableStateOf(initialSection) }
    val stacks =
        MainSection.entries.associateWith { section ->
            val root = remember { AppEntry(section.root) }
            if (section == initialSection && initial != section.root) {
                val entry = remember { AppEntry(initial, callerId = root.id) }
                rememberNavBackStack(root, entry)
            } else rememberNavBackStack(root)
        }
    return remember { MainNavigation(selected, stacks) }
}
