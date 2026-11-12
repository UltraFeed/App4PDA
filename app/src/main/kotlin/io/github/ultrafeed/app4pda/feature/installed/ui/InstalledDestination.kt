package io.github.ultrafeed.app4pda.feature.installed.ui

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.app.MainActivity
import io.github.ultrafeed.app4pda.app.navigation.AppDestination
import io.github.ultrafeed.app4pda.app.navigation.AppEntry
import io.github.ultrafeed.app4pda.app.navigation.LocalEntryActive
import io.github.ultrafeed.app4pda.app.navigation.OnScreenResume
import io.github.ultrafeed.app4pda.app.shell.ScreenFrame
import io.github.ultrafeed.app4pda.feature.installed.AppSection
import io.github.ultrafeed.app4pda.feature.installed.InstalledAction
import io.github.ultrafeed.app4pda.feature.installed.InstalledApp
import io.github.ultrafeed.app4pda.feature.installed.UpdateProvider
import io.github.ultrafeed.app4pda.feature.installed.data.InstalledAppsRepository
import io.github.ultrafeed.app4pda.feature.installed.data.LocalUpdateNotifications
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun InstalledDestination(
    activity: MainActivity,
    entry: AppEntry,
    onOpen: (AppDestination) -> Unit,
    onBack: () -> Unit,
    home: Boolean,
) {
    val repository: InstalledAppsRepository = koinInject()
    val section =
        if (home) AppSection.APPLICATIONS else (entry.destination as AppDestination.Apps).section
    val model: InstalledAppsViewModel = koinViewModel() { parametersOf(section) }
    val ui by model.state.collectAsStateWithLifecycle()
    val appearance by activity.appearanceSettings.snapshots.collectAsStateWithLifecycle()
    val searchRequest = 0
    val systemAction =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            repository.packageChanged()
        }
    ObserveInstalledSettings(activity, model, repository, home)
    val openApp = rememberAppOpen(model, onOpen)

    val title = activity.getString(installedScreenTitle(section))
    ScreenFrame(
        onBack,
        title,
        topBar =
            if (home)
                ({
                    HomeToolbar(
                        ui.query,
                        model::search,
                    ) { query ->
                        if (query.isNotBlank())
                            onOpen(AppDestination.Catalog(filter = query.trim()))
                    }
                })
            else null,
    ) {
        InstalledAppsScreen(
            ui,
            if (home) installedSections(ui) else emptyList(),
            model::search,
            model::section,
            model::refresh,
            openApp,
            { app, operation ->
                performInstalledAction(activity, model, app, operation, systemAction::launch)
            },
            model::dismissErrors,
            { query -> onOpen(AppDestination.Catalog(filter = query)) },
            searchRequest,
            appearance.installedCardAppearance,
            home,
        )
    }
}

private fun performInstalledAction(
    activity: MainActivity,
    model: InstalledAppsViewModel,
    app: InstalledApp,
    operation: InstalledAction,
    launch: (Intent) -> Unit,
) {
    if (!operation.isAvailableFor(app)) return
    try {
        when (operation) {
            InstalledAction.RUN ->
                activity.packageManager.getLaunchIntentForPackage(app.packageName)?.let(launch)

            InstalledAction.SYSTEM_INFO ->
                launch(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        "package:${app.packageName}".toUri(),
                    ),
                )

            InstalledAction.UNINSTALL ->
                launch(
                    Intent(Intent.ACTION_DELETE, "package:${app.packageName}".toUri()),
                )

            InstalledAction.HIDE -> model.hidden(app, !app.hidden)
            InstalledAction.IGNORE_FORUM ->
                model.ignoreSource(
                    app,
                    UpdateProvider.FORUM,
                )
            InstalledAction.IGNORE_MARKET ->
                model.ignoreSource(
                    app,
                    UpdateProvider.PLAY,
                )
            InstalledAction.IGNORE_RUSTORE ->
                model.ignoreSource(
                    app,
                    UpdateProvider.RUSTORE,
                )
        }
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun rememberAppOpen(
    model: InstalledAppsViewModel,
    onOpen: (AppDestination) -> Unit,
): (InstalledApp) -> Unit {
    val screenScope = rememberCoroutineScope()
    val bindingSearch by model.bindingSearch.collectAsStateWithLifecycle()
    val active = LocalEntryActive.current
    val latestActive by rememberUpdatedState(active)
    fun searchBinding(app: InstalledApp) {
        if (bindingSearch != null) return
        screenScope.launch {
            val query = model.bindingQuery(app) ?: return@launch
            if (latestActive)
                onOpen(AppDestination.Catalog(filter = query, linked = app.packageName))
        }
    }

    fun openApp(app: InstalledApp) {
        val knownStore =
            listOf(app.market, app.ruStore).any { it != null } ||
                app.playDiscovery.found ||
                app.ruStoreDiscovery.found
        if (app.forum.topicId <= 0 && !knownStore) {
            searchBinding(app)
            return
        }
        onOpen(
            AppDestination.Details(app.forum.topicId, app.packageName, linked = app.packageName),
        )
    }

    return ::openApp
}

@Composable
private fun ObserveInstalledSettings(
    activity: MainActivity,
    model: InstalledAppsViewModel,
    repository: InstalledAppsRepository,
    home: Boolean,
) {
    LaunchedEffect(model) {
        activity.appSettings.changes.collect {
            model.sort(it.appsSortOrder)
        }
    }
    OnScreenResume {
        if (!repository.state.value.initialized) repository.refresh()
        model.sort(activity.appSettings.current.appsSortOrder)
        if (home) {
            (activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(
                LocalUpdateNotifications.ID,
            )
        }
    }
}

private fun installedScreenTitle(section: AppSection): Int =
    when (section) {
        AppSection.UPDATES -> R.string.available_updates
        AppSection.HIDDEN -> R.string.hidden
        else -> R.string.app_name
    }

private fun installedSections(ui: InstalledAppsUiState): List<AppSection> =
    listOf(
            AppSection.FAVORITES,
            AppSection.APPLICATIONS,
            AppSection.UPDATES,
            AppSection.UNLINKED,
            AppSection.PENDING_LINKING,
        )
        .filter { section ->
            section == AppSection.APPLICATIONS ||
                (ui.source.counts[section] ?: 0) > 0 ||
                (!ui.source.initialized && ui.section == section)
        }
