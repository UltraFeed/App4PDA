package io.github.ultrafeed.app4pda.feature.details.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.images.ImageOrigin
import io.github.ultrafeed.app4pda.core.network.SourceProblem
import io.github.ultrafeed.app4pda.core.ui.components.AppErrorCard
import io.github.ultrafeed.app4pda.core.ui.components.ConfirmActionDialog
import io.github.ultrafeed.app4pda.core.ui.theme.LocalAppAnimations
import io.github.ultrafeed.app4pda.feature.details.AppDetails
import io.github.ultrafeed.app4pda.feature.details.AppDetailsUiState
import io.github.ultrafeed.app4pda.feature.details.DetailsFile
import io.github.ultrafeed.app4pda.feature.details.DetailsTab
import io.github.ultrafeed.app4pda.feature.details.availableTabs
import io.github.ultrafeed.app4pda.feature.installed.InstalledApp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

internal fun detailsBindingAction(packageName: String?, boundToTopic: Boolean): DetailsAction? =
    if (packageName.isNullOrBlank()) null
    else if (boundToTopic) DetailsAction.UNLINK else DetailsAction.BIND

@Composable
fun AppDetailsScreen(
    state: AppDetailsUiState,
    onRefresh: () -> Unit,
    onTab: (DetailsTab) -> Unit,
    onAction: (DetailsAction) -> Unit,
    onLink: (String, ImageOrigin) -> Unit,
    onImages: (ImageOrigin, List<String>, Int) -> Unit,
    onDownload: (DetailsFile) -> Unit,
    onErrorDismiss: () -> Unit,
    onActionRetry: () -> Unit,
    onFileConsumed: () -> Unit = {},
    initialSource: DetailsTab? = null,
) {
    var fileId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.selectedFileId) {
        state.selectedFileId?.let {
            fileId = it
            onFileConsumed()
        }
    }
    val data = state.data
    val available = data?.availableTabs().orEmpty()
    val awaitingSource =
        initialSource == state.tab &&
            initialSource !in available &&
            (state.loading || state.playLoading)
    val tabs = available.takeUnless { awaitingSource }.orEmpty()
    val pager = rememberDetailsPager(tabs, state.tab, data != null, onTab)
    val scope = rememberCoroutineScope()
    val animationsEnabled = LocalAppAnimations.current
    Surface(Modifier.fillMaxSize()) {
        Column {
            DetailsRequestState(state, onRefresh, onActionRetry, onErrorDismiss)
            if (data != null) {
                DetailsMetadata(data, onRefresh)
                if (tabs.isNotEmpty())
                    DetailsSelector(tabs, pager.currentPage) { index ->
                        scope.launch {
                            if (animationsEnabled) pager.animateScrollToPage(index)
                            else pager.scrollToPage(index)
                        }
                    }
                if (tabs.isNotEmpty())
                    HorizontalPager(
                        pager,
                        Modifier.weight(1f),
                        key = { tabs[it] },
                    ) { page ->
                        DetailsPage(
                            data,
                            tabs[page],
                            state.actionBusy,
                            onRefresh,
                            onAction,
                            onLink,
                            onImages,
                        ) {
                            fileId = it
                        }
                    }
                if (tabs.isEmpty()) EmptyDetailsState(state, onRefresh)
            } else if (!state.loading && !state.error)
                TextButton(onClick = onRefresh) { Text(stringResource(R.string.action_retry)) }
        }
    }
    data
        ?.files
        ?.find { it.id == fileId }
        ?.let {
            DetailsDownloadDialog(it, data, state.actionBusy, { fileId = null }, onDownload)
        }
}

@Composable
private fun EmptyDetailsState(state: AppDetailsUiState, onRetry: () -> Unit) {
    if (state.loading || state.playLoading || state.error) return
    val problems = listOfNotNull(state.data?.market?.problem, state.data?.ruStore?.problem)
    if (problems.isEmpty())
        AppErrorCard(
            SourceProblem(
                "APP · UNAVAILABLE",
                stringResource(R.string.details_no_sources),
            ),
            onRetry = onRetry,
        )
}

@Composable
private fun rememberDetailsPager(
    tabs: List<DetailsTab>,
    requested: DetailsTab,
    ready: Boolean,
    onTab: (DetailsTab) -> Unit,
): PagerState {
    val selected = requested.takeIf { it in tabs } ?: tabs.firstOrNull()
    val pager =
        rememberPagerState(initialPage = tabs.indexOf(selected).coerceAtLeast(0)) {
            tabs.size.coerceAtLeast(1)
        }
    LaunchedEffect(tabs, selected) {
        if (selected == null) return@LaunchedEffect
        if (ready && selected != requested) onTab(selected)
        val index = tabs.indexOf(selected)
        if (!pager.isScrollInProgress && pager.currentPage != index) pager.scrollToPage(index)
    }
    LaunchedEffect(pager, tabs) {
        snapshotFlow { pager.settledPage }
            .drop(1)
            .distinctUntilChanged()
            .collect { index ->
                tabs.getOrNull(index)?.let(onTab)
            }
    }
    return pager
}

@Composable
internal fun AppDetailsToolbarActions(
    state: AppDetailsUiState,
    target: InstalledApp?,
    requestedTopic: Int,
    onFavorite: () -> Unit,
    onAction: (DetailsAction) -> Unit,
) {
    val linkedPackage = target?.packageName
    val userChoicesEditable = target?.userChoicesEditable ?: true
    val boundToTopic =
        target?.forum?.topicId?.let { it > 0 && (requestedTopic <= 0 || it == requestedTopic) } ==
            true
    var menu by rememberSaveable { mutableStateOf(false) }
    var confirmation by rememberSaveable { mutableStateOf<DetailsAction?>(null) }
    state.data?.let { data ->
        IconToggleButton(
            checked = data.favorite,
            onCheckedChange = { onFavorite() },
            enabled = !state.actionBusy && userChoicesEditable,
        ) {
            Icon(
                if (data.favorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                stringResource(R.string.favorites),
            )
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier) {
                Icon(Icons.Default.MoreVert, stringResource(R.string.details_actions))
            }
            DetailsOverflowMenu(
                expanded = menu,
                onDismiss = { menu = false },
                actions =
                    detailsActions(
                        data,
                        linkedPackage.takeIf { userChoicesEditable },
                        boundToTopic,
                    ),
                enabled = !state.actionBusy,
            ) { action ->
                menu = false
                if (
                    (action == DetailsAction.BIND && data.topicId > 0) ||
                        action == DetailsAction.UNLINK
                )
                    confirmation = action
                else onAction(action)
            }
        }
        confirmation?.let { action ->
            ConfirmActionDialog(
                title = data.title,
                text = stringResource(R.string.catalog_actions_confirm),
                onConfirm = {
                    confirmation = null
                    onAction(action)
                },
                onDismiss = { confirmation = null },
            )
        }
    }
}

@Composable
private fun DetailsOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: List<Pair<DetailsAction, Int>>,
    enabled: Boolean,
    onAction: (DetailsAction) -> Unit,
) {
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismiss) {
        val groups =
            actions
                .groupBy { (action, _) ->
                    when (action) {
                        DetailsAction.BIND,
                        DetailsAction.UNLINK -> DetailsAction.BIND
                        DetailsAction.DEBUG -> DetailsAction.DEBUG
                        else -> DetailsAction.FORUM
                    }
                }
                .values
                .toList()
        groups.forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(groupIndex, groups.size)) {
                group.forEachIndexed { index, (action, title) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(title)) },
                        shape = MenuDefaults.itemShape(index, group.size).shape,
                        enabled = enabled,
                        onClick = { onAction(action) },
                    )
                }
            }
        }
    }
}

private fun detailsActions(
    data: AppDetails,
    linkedPackage: String?,
    boundToTopic: Boolean,
): List<Pair<DetailsAction, Int>> = buildList {
    if (data.topicId > 0) add(DetailsAction.FORUM to R.string.menu_open_forum)
    if (data.market?.error == false) add(DetailsAction.MARKET to R.string.menu_app_market)
    if (data.ruStore?.error == false) add(DetailsAction.RUSTORE to R.string.open_rustore)
    detailsBindingAction(linkedPackage, boundToTopic)?.let { action ->
        add(
            action to
                if (action == DetailsAction.UNLINK) R.string.menu_app_unlink
                else R.string.menu_link,
        )
    }
    add(DetailsAction.DEBUG to R.string.menu_debug)
}

@Composable
private fun DetailsPage(
    data: AppDetails,
    tab: DetailsTab,
    busy: Boolean,
    onRefresh: () -> Unit,
    onAction: (DetailsAction) -> Unit,
    onLink: (String, ImageOrigin) -> Unit,
    onImages: (ImageOrigin, List<String>, Int) -> Unit,
    onFile: (String) -> Unit,
) {
    val origin =
        when (tab) {
            DetailsTab.GOOGLE_PLAY -> ImageOrigin.GOOGLE_PLAY
            DetailsTab.RUSTORE -> ImageOrigin.RUSTORE
            else -> ImageOrigin.FORUM
        }
    if (tab != DetailsTab.FORUM) {
        StoreDocument(
            if (tab == DetailsTab.GOOGLE_PLAY) data.market else data.ruStore,
            data.files.firstOrNull { if (tab == DetailsTab.GOOGLE_PLAY) it.market else it.ruStore },
            origin,
            busy,
            onRefresh,
            { images, index -> onImages(origin, images, index) },
            { onLink(it, origin) },
            { onFile(it.id) },
        )
    } else
        DetailsDocument(
            when (tab) {
                DetailsTab.GOOGLE_PLAY -> data.market
                DetailsTab.RUSTORE -> data.ruStore
                else -> data.forum
            },
            origin,
            onRefresh,
            if (tab == DetailsTab.FORUM) ({ onAction(DetailsAction.FORUM) }) else null,
            { images, index -> onImages(origin, images, index) },
            { onLink(it, origin) },
            Modifier.fillMaxSize(),
        )
}
