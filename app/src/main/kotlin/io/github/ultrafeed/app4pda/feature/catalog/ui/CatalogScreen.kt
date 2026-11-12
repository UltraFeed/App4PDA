package io.github.ultrafeed.app4pda.feature.catalog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.app.navigation.ScrollToTopOnReselection
import io.github.ultrafeed.app4pda.core.network.SourceProblem
import io.github.ultrafeed.app4pda.core.ui.components.AppErrorCard
import io.github.ultrafeed.app4pda.core.ui.components.AppLoadingIndicator
import io.github.ultrafeed.app4pda.core.ui.theme.AppLayout
import io.github.ultrafeed.app4pda.feature.catalog.CatalogApp
import io.github.ultrafeed.app4pda.feature.catalog.CatalogCategory
import io.github.ultrafeed.app4pda.feature.catalog.CatalogHeader
import io.github.ultrafeed.app4pda.feature.catalog.CatalogRow
import io.github.ultrafeed.app4pda.feature.catalog.CatalogUiState
import io.github.ultrafeed.app4pda.feature.installed.InstalledApp
import io.github.ultrafeed.app4pda.feature.installed.InstalledCardAppearance
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun CatalogScreen(
    state: CatalogUiState,
    linkedPackage: String?,
    onDraft: (String) -> Unit,
    onSearch: () -> Unit,
    onTopic: (Int) -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (CatalogApp) -> Unit,
    installedCards: List<InstalledApp>? = null,
    onVisibleTopics: suspend (List<Int>) -> Unit = {},
    cardAppearance: InstalledCardAppearance = InstalledCardAppearance(),
    onCategory: (CatalogCategory) -> Unit = {},
) {
    val list = rememberLazyListState()
    ScrollToTopOnReselection(list)
    val currentRows by rememberUpdatedState(state.rows)
    val visibleTopics by rememberUpdatedState(onVisibleTopics)
    LaunchedEffect(list) {
        snapshotFlow {
            list.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                (currentRows.getOrNull(item.index) as? CatalogApp)?.topicId
            }
        }
            .distinctUntilChanged()
            .collectLatest { visibleTopics(it) }
    }
    var topicDialog by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        CatalogSearchField(state.draft, onDraft) { onSearch() }
        if (linkedPackage != null)
            FilledTonalButton(
                { topicDialog = true },
                Modifier.fillMaxWidth().padding(AppLayout.content),
            ) {
                Text(stringResource(R.string.bind_topic_id))
            }
        if (state.loading) AppLoadingIndicator(Modifier.padding(AppLayout.content))
        CatalogError(state, onRetry)
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f),
            contentPadding = AppLayout.listPadding,
            verticalArrangement = Arrangement.spacedBy(AppLayout.itemGap),
        ) {
            items(state.rows, key = { it.key }) { row ->
                CatalogRowContent(row, installedCards.orEmpty(), cardAppearance, onOpen, onCategory)
            }
            if (state.hasMore)
                item {
                    LaunchedEffect(state.page, list.isScrollInProgress) {
                        if (list.isScrollInProgress) onNext()
                    }
                    TextButton(onNext, enabled = !state.loading) {
                        Text(stringResource(R.string.catalog_more))
                    }
                }
        }
    }
    if (topicDialog) CatalogTopicDialog({ topicDialog = false }, onTopic)
}

@Composable
private fun CatalogError(state: CatalogUiState, onRetry: () -> Unit) {
    if (state.error)
        AppErrorCard(
            problem =
                state.failure?.let { SourceProblem.from(LocalContext.current, "4PDA", it) }
                    ?: SourceProblem(
                        if (state.searchLimited) "4PDA · SEARCH_LIMIT" else "4PDA · REQUEST_FAILED",
                        stringResource(
                            if (state.searchLimited) R.string.forum_search_limited
                            else if (state.query.search != null) R.string.forum_search_unavailable
                            else if (state.rows.isEmpty()) R.string.catalog_not_loaded
                            else R.string.error_no_connection,
                        ),
                    ),
            onRetry = onRetry,
            modifier = Modifier.padding(AppLayout.content),
        )
}

@Composable
private fun CatalogRowContent(
    row: CatalogRow,
    installedCards: List<InstalledApp>,
    cardAppearance: InstalledCardAppearance,
    onOpen: (CatalogApp) -> Unit,
    onCategory: (CatalogCategory) -> Unit,
) {
    if (row is CatalogCategory) {
        CategoryItem(row) { onCategory(row) }
    } else if (row is CatalogApp) {
        if (row.requiresAndroidConfirmation || row.requiresAppConfirmation) {
            Text(
                stringResource(R.string.forum_check_android),
                Modifier.padding(AppLayout.content),
            )
            return
        }
        CatalogAppCard(
            row,
            installedCards,
            { onOpen(row) },
            cardAppearance,
        )
    } else if (row is CatalogHeader)
        Text(
            row.title,
            Modifier.padding(AppLayout.content),
            style = MaterialTheme.typography.titleMedium,
        )
}
