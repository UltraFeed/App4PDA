package io.github.ultrafeed.app4pda.app.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.metadata
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import io.github.ultrafeed.app4pda.app.MainActivity
import io.github.ultrafeed.app4pda.app.navigation.AppDestination
import io.github.ultrafeed.app4pda.app.navigation.AppEntry
import io.github.ultrafeed.app4pda.app.navigation.LocalCanGoBack
import io.github.ultrafeed.app4pda.app.navigation.LocalEntryActive
import io.github.ultrafeed.app4pda.app.navigation.LocalRootReselection
import io.github.ultrafeed.app4pda.app.navigation.MainNavigation
import io.github.ultrafeed.app4pda.app.navigation.MainSection
import io.github.ultrafeed.app4pda.app.navigation.MainSectionMetadata
import io.github.ultrafeed.app4pda.app.navigation.PendingScreenResult
import io.github.ultrafeed.app4pda.app.navigation.ScreenResult
import io.github.ultrafeed.app4pda.app.navigation.complete
import io.github.ultrafeed.app4pda.app.navigation.consumeResult
import io.github.ultrafeed.app4pda.app.navigation.isDialog
import io.github.ultrafeed.app4pda.feature.auth.ui.AuthDestination
import io.github.ultrafeed.app4pda.feature.catalog.ui.CatalogDestination
import io.github.ultrafeed.app4pda.feature.details.ui.DetailsDestination
import io.github.ultrafeed.app4pda.feature.downloads.ui.AttachmentDestination
import io.github.ultrafeed.app4pda.feature.downloads.ui.DownloadsDestination
import io.github.ultrafeed.app4pda.feature.forum.ui.PostDestination
import io.github.ultrafeed.app4pda.feature.gallery.ui.GalleryDestination
import io.github.ultrafeed.app4pda.feature.installed.UpdateSource
import io.github.ultrafeed.app4pda.feature.installed.data.InstalledAppsRepository
import io.github.ultrafeed.app4pda.feature.installed.ui.InstalledDestination
import io.github.ultrafeed.app4pda.feature.lookup.ui.LookupDestination
import io.github.ultrafeed.app4pda.feature.recent.ui.DigestDestination
import io.github.ultrafeed.app4pda.feature.settings.ui.SettingsDestination
import kotlinx.coroutines.flow.first

/** Decorators remain composition-owned independently for each of the five real stacks. */
@Composable
internal fun rememberAppEntries(
    activity: MainActivity,
    navigation: MainNavigation,
    installed: InstalledAppsRepository,
    goBack: () -> Unit,
    backInProgress: Boolean,
    onBackFinished: () -> Unit,
): Map<MainSection, List<NavEntry<NavKey>>> =
    navigation.stacks.mapValues { (section, stack) ->
        key(section) {
            rememberDecoratedNavEntries(
                backStack = stack,
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                entryProvider = { key ->
                    val screen = key as AppEntry
                    NavEntry(
                        key = screen,
                        contentKey = screen.id,
                        metadata =
                            metadata {
                                put(
                                    MainSectionMetadata,
                                    section,
                                )
                            } +
                                (if (screen.destination.isDialog) DialogSceneStrategy.dialog()
                                else emptyMap()),
                    ) { rawEntry ->
                        AppNavigationEntry(
                            activity,
                            rawEntry as AppEntry,
                            stack,
                            navigation,
                            installed,
                            goBack,
                            backInProgress,
                            onBackFinished,
                        )
                    }
                },
            )
        }
    }

@Composable
private fun AppNavigationEntry(
    activity: MainActivity,
    screen: AppEntry,
    stack: NavBackStack<NavKey>,
    navigation: MainNavigation,
    installed: InstalledAppsRepository,
    goBack: () -> Unit,
    backInProgress: Boolean,
    onBackFinished: () -> Unit,
) {
    val visibleEntry = navigation.current
    val entry = visibleEntry.takeIf { it.id == screen.id } ?: screen
    val entryTransition = LocalNavAnimatedContentScope.current.transition
    LaunchedEffect(visibleEntry.id, backInProgress) {
        if (visibleEntry.id == entry.id && backInProgress) {
            withFrameNanos {}
            snapshotFlow { !entryTransition.isRunning }.first { it }
            onBackFinished()
        }
    }
    CompositionLocalProvider(
        LocalEntryActive provides (visibleEntry.id == entry.id),
        LocalCanGoBack provides (entry.id != (stack.first() as AppEntry).id),
        LocalRootReselection provides navigation.reselection(entry, stack.first() as AppEntry),
    ) {
        val back: () -> Unit = {
            if (navigation.current.id == entry.id) goBack()
        }
        val open: (AppDestination) -> Unit = { target ->
            if (navigation.current.id == entry.id) {
                navigation.open(target)
            }
        }
        PendingScreenResult(entry) {
            when (stack.consumeResult(entry.id)) {
                ScreenResult.AuthChanged -> {
                    installed.refresh(forceSources = setOf(UpdateSource.FORUM))
                }

                ScreenResult.DownloadStarted -> if (entry.destination is AppDestination.Post) back()

                null -> Unit
            }
        }
        val result: (ScreenResult) -> Unit = completion@{
            if (navigation.current.id != entry.id) return@completion
            if (
                !stack.complete(
                    entry.id,
                    it,
                ) && (stack.lastOrNull() as? AppEntry)?.id == entry.id
            )
                back()
        }
        val replace: (AppDestination) -> Unit = { target ->
            if (navigation.current.id == entry.id)
                stack[stack.lastIndex] = AppEntry(target, callerId = entry.callerId)
        }
        Box(
            Modifier,
        ) {
            AppDestinationContent(activity, entry, open, back, replace, result)
        }
    }
}

@Composable
private fun AppDestinationContent(
    activity: MainActivity,
    entry: AppEntry,
    open: (AppDestination) -> Unit,
    back: () -> Unit,
    replace: (AppDestination) -> Unit,
    result: (ScreenResult) -> Unit,
) {
    when (val destination = entry.destination) {
        AppDestination.Home,
        is AppDestination.Apps ->
            InstalledDestination(
                activity,
                entry,
                open,
                back,
                destination == AppDestination.Home,
            )

        AppDestination.Empty -> ScreenFrame(back, "") {}
        AppDestination.Categories,
        is AppDestination.Catalog ->
            CatalogDestination(
                activity,
                entry,
                open,
                back,
            )

        is AppDestination.Details -> DetailsDestination(activity, entry, open, back)
        AppDestination.Downloads -> DownloadsDestination(activity, back)
        AppDestination.Digest -> DigestDestination(open, back)
        is AppDestination.Settings -> SettingsDestination(activity, entry, open, back)
        is AppDestination.Lookup -> LookupDestination(activity, entry, replace, back)
        is AppDestination.Auth -> AuthDestination(entry, back, result)
        is AppDestination.Post -> PostDestination(activity, entry, open, back)
        is AppDestination.Attachment -> AttachmentDestination(activity, entry, back, result)
        is AppDestination.Gallery ->
            GalleryDestination(
                activity,
                destination,
                back,
            )
    }
}
