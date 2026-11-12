package io.github.ultrafeed.app4pda.feature.installed.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.app.navigation.ScrollToTopOnReselection
import io.github.ultrafeed.app4pda.core.ui.components.AppErrorCard
import io.github.ultrafeed.app4pda.core.ui.components.AppLoadingIndicator
import io.github.ultrafeed.app4pda.core.ui.components.ConfirmActionDialog
import io.github.ultrafeed.app4pda.core.ui.components.SwitchChoiceItem
import io.github.ultrafeed.app4pda.core.ui.theme.AppLayout
import io.github.ultrafeed.app4pda.feature.installed.AppSection
import io.github.ultrafeed.app4pda.feature.installed.InstalledAction
import io.github.ultrafeed.app4pda.feature.installed.InstalledApp
import io.github.ultrafeed.app4pda.feature.installed.InstalledCardAppearance

@Composable
fun InstalledAppsScreen(
    ui: InstalledAppsUiState,
    tabs: List<AppSection>,
    onQuery: (String) -> Unit,
    onSection: (AppSection) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (InstalledApp) -> Unit,
    onAction: (InstalledApp, InstalledAction) -> Unit,
    onDismissErrors: () -> Unit,
    onCatalogSearch: (String) -> Unit = {},
    searchRequest: Int = 0,
    cardAppearance: InstalledCardAppearance = InstalledCardAppearance(),
    home: Boolean = false,
) {
    var menuKey by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmation by rememberSaveable { mutableStateOf<InstalledAction?>(null) }
    var searchVisible by rememberSaveable { mutableStateOf(ui.query.isNotEmpty()) }
    LaunchedEffect(searchRequest) { if (searchRequest > 0) searchVisible = !searchVisible }
    val current = ui.source.apps.find { it.key == menuKey }
    val positions = rememberSaveableStateHolder()
    val pager = rememberInstalledPager(tabs, ui.section, onSection)
    fun action(app: InstalledApp, operation: InstalledAction) {
        menuKey = app.key
        if (operation == InstalledAction.UNINSTALL) {
            confirmation = operation
        } else {
            onAction(app, operation)
            if (operation == InstalledAction.RUN) menuKey = null
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            InstalledPagerSelector(ui, tabs, pager)
            if (searchVisible && !home)
                InstalledSearch(
                    ui.query,
                    onQuery,
                    onCatalogSearch,
                ) {
                    searchVisible = false
                }
            InstalledErrors(ui, onRefresh, onDismissErrors)
            InstalledPages(ui, tabs, pager, positions, Modifier.weight(1f)) { pageSection ->
                InstalledPage(
                    ui,
                    pageSection,
                    home,
                    cardAppearance,
                    onOpen,
                    { menuKey = it },
                    onRefresh,
                )
            }
        }
    }
    if (current != null && confirmation == null)
        InstalledAppMenu(
            current,
            ui.actionBusy,
            { action(current, it) },
            { menuKey = null },
        )
    confirmation?.let { operation ->
        InstalledActionConfirmation(
            current,
            onConfirm = {
                current?.let { onAction(it, operation) }
                confirmation = null
                menuKey = null
            },
            onDismiss = {
                confirmation = null
                menuKey = null
            },
        )
    }
}

@Composable
private fun MenuGroupTitle(title: String) {
    HorizontalDivider(Modifier.padding(vertical = AppLayout.itemGap))
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = AppLayout.content, vertical = AppLayout.itemGap),
    )
}

@Composable
private fun InstalledAppMenu(
    current: InstalledApp,
    busy: Boolean,
    onAction: (InstalledAction) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text(current.name) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                if (!current.hidden) InstalledMenuActions(current, busy, onAction)
                if (current.hidden) {
                    SegmentedListItem(
                        onClick = { onAction(InstalledAction.HIDE) },
                        enabled = !busy,
                        shapes = ListItemDefaults.segmentedShapes(0, 1),
                        colors =
                            ListItemDefaults.segmentedColors(
                                containerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                            ),
                        content = { Text(stringResource(R.string.app_visibility_show)) },
                    )
                } else if (current.userChoicesEditable) {
                    MenuGroupTitle(stringResource(R.string.app_menu_display))
                    SwitchChoiceItem(
                        stringResource(R.string.app_visibility_hide),
                        current.hidden,
                        { onAction(InstalledAction.HIDE) },
                        enabled = !busy,
                    )
                }
                if (current.installed && current.userChoicesEditable)
                    InstalledSourceSwitches(
                        current,
                        busy,
                        onAction,
                    )
            }
        },
        confirmButton = {
            TextButton({ onDismiss() }) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun InstalledCheckProgress(ui: InstalledAppsUiState) {
    if (ui.source.loading) {
        Column(
            Modifier.fillMaxWidth().padding(AppLayout.content),
            verticalArrangement = Arrangement.spacedBy(AppLayout.itemGap),
        ) {
            Text(
                stringResource(R.string.checking_updates),
                style = MaterialTheme.typography.labelMedium,
            )
            AppLoadingIndicator(
                progress =
                    if (ui.source.total > 0) ui.source.completed.toFloat() / ui.source.total
                    else null,
            )
        }
    }
}

@Composable
private fun InstalledPage(
    ui: InstalledAppsUiState,
    pageSection: AppSection,
    home: Boolean,
    cardAppearance: InstalledCardAppearance,
    onOpen: (InstalledApp) -> Unit,
    onMenu: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val listState = rememberLazyListState()
    ScrollToTopOnReselection(
        listState,
        pageSection == ui.section,
    )
    val list: @Composable () -> Unit = {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = AppLayout.listPadding,
            verticalArrangement = Arrangement.spacedBy(AppLayout.itemGap),
        ) {
            if (ui.source.loading && !(home && ui.source.userRefreshing))
                item(key = "updateProgress") {
                    InstalledCheckProgress(
                        ui,
                    )
                }
            val pageRows =
                if (pageSection == ui.section) ui.rows
                else
                    InstalledAppsViewModel.filterApps(
                        ui.source.apps,
                        pageSection,
                        ui.sorting,
                    )
            if (pageRows.isEmpty() && !ui.source.loading)
                item {
                    Text(
                        stringResource(R.string.catalog_empty),
                        Modifier.padding(AppLayout.section),
                    )
                }
            items(pageRows, key = { it.key }) { app ->
                InstalledAppCard(
                    app,
                    app.packageName in ui.source.unavailableOnPlay,
                    { onOpen(app) },
                    { onMenu(app.key) },
                    cardAppearance,
                    section = pageSection,
                )
            }
        }
    }
    if (home)
        HomePullToRefresh(
            ui.source.userRefreshing,
            !ui.source.loading && !ui.source.userRefreshing && pageSection == ui.section,
            onRefresh,
            list,
        )
    else list()
}

@Composable
private fun InstalledSearch(
    query: String,
    onQuery: (String) -> Unit,
    onCatalogSearch: (String) -> Unit,
    onClose: () -> Unit,
) {
    OutlinedTextField(
        query,
        onQuery,
        label = { Text(stringResource(R.string.search)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        trailingIcon = {
            TextButton(
                {
                    onQuery("")
                    onClose()
                },
            ) {
                Icon(Icons.Default.Close, null)
            }
        },
    )
    TextButton({ onCatalogSearch(query) }) {
        Text(stringResource(R.string.search_in_catalog))
    }
}

@Composable
private fun InstalledMenuActions(
    current: InstalledApp,
    busy: Boolean,
    onAction: (InstalledAction) -> Unit,
) {
    val actions = buildList {
        if (current.installed) {
            add(InstalledAction.RUN to stringResource(R.string.menu_app_run))
            add(
                InstalledAction.SYSTEM_INFO to stringResource(R.string.installed_system_info),
            )
            if (current.userChoicesEditable && current.local?.system != true)
                add(
                    InstalledAction.UNINSTALL to
                        stringResource(
                            R.string.menu_uninstall,
                        ),
                )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        actions.forEachIndexed { index, (operation, title) ->
            SegmentedListItem(
                onClick = { onAction(operation) },
                shapes = ListItemDefaults.segmentedShapes(index, actions.size),
                colors =
                    ListItemDefaults.segmentedColors(
                        containerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                    ),
                enabled = !busy,
                content = { Text(title) },
            )
        }
    }
}

@Composable
private fun InstalledSourceSwitches(
    current: InstalledApp,
    busy: Boolean,
    onAction: (InstalledAction) -> Unit,
) {
    MenuGroupTitle(stringResource(R.string.app_menu_updates))
    val switches =
        listOf(
            Triple(
                InstalledAction.IGNORE_FORUM,
                R.string.app_check_forum,
                !current.preferences.ignoreForum,
            ),
            Triple(
                InstalledAction.IGNORE_MARKET,
                R.string.app_check_play,
                !current.preferences.ignoreMarket,
            ),
            Triple(
                InstalledAction.IGNORE_RUSTORE,
                R.string.app_check_rustore,
                !current.preferences.ignoreRuStore,
            ),
        )
    switches.forEachIndexed { index, (operation, title, checked) ->
        SwitchChoiceItem(
            stringResource(title),
            checked,
            { onAction(operation) },
            enabled = !busy,
            index = index,
            count = switches.size,
        )
    }
}

@Composable
private fun InstalledErrors(
    ui: InstalledAppsUiState,
    onRefresh: () -> Unit,
    onDismissErrors: () -> Unit,
) {
    ui.source.errors.values.forEach { problem ->
        AppErrorCard(
            problem = problem,
            onRetry = onRefresh,
            onDismiss = onDismissErrors,
            modifier = Modifier.padding(AppLayout.content),
        )
    }
}

@Composable
private fun InstalledActionConfirmation(
    current: InstalledApp?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmActionDialog(
        title = stringResource(R.string.warning),
        text = stringResource(R.string.installed_confirm_action, current?.name.orEmpty()),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
