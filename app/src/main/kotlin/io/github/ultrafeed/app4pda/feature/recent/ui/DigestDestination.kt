package io.github.ultrafeed.app4pda.feature.recent.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.app.navigation.AppDestination
import io.github.ultrafeed.app4pda.app.navigation.ScrollToTopOnReselection
import io.github.ultrafeed.app4pda.app.shell.ScreenFrame
import io.github.ultrafeed.app4pda.core.network.SourceProblem
import io.github.ultrafeed.app4pda.core.ui.components.AppErrorCard
import io.github.ultrafeed.app4pda.core.ui.components.AppLoadingIndicator
import io.github.ultrafeed.app4pda.core.ui.components.SectionSwitch
import io.github.ultrafeed.app4pda.core.ui.components.SectionSwitches
import io.github.ultrafeed.app4pda.core.ui.theme.AppLayout
import io.github.ultrafeed.app4pda.core.ui.theme.LocalAppAnimations
import io.github.ultrafeed.app4pda.feature.catalog.CatalogApp
import io.github.ultrafeed.app4pda.feature.catalog.ui.CatalogAppCard
import io.github.ultrafeed.app4pda.feature.catalog.withTopicPreviews
import io.github.ultrafeed.app4pda.feature.installed.InstalledApp
import io.github.ultrafeed.app4pda.feature.installed.InstalledCardAppearance
import io.github.ultrafeed.app4pda.feature.installed.data.InstalledAppsRepository
import io.github.ultrafeed.app4pda.feature.recent.data.DigestContentException
import io.github.ultrafeed.app4pda.feature.recent.data.DigestCoverage
import io.github.ultrafeed.app4pda.feature.recent.data.DigestEntry
import io.github.ultrafeed.app4pda.feature.recent.data.DigestGroup
import io.github.ultrafeed.app4pda.feature.recent.data.DigestIssue
import io.github.ultrafeed.app4pda.feature.recent.data.DigestKind
import io.github.ultrafeed.app4pda.feature.recent.data.ForumDigest
import io.github.ultrafeed.app4pda.feature.recent.data.forDisplay
import io.github.ultrafeed.app4pda.feature.settings.data.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

data class DigestState(
    val groups: List<DigestGroup> = emptyList(),
    val kind: DigestKind = DigestKind.UPDATED,
    val loading: Boolean = false,
    val error: Throwable? = null,
    val cards: Map<Int, CatalogApp> = emptyMap(),
    val coverage: DigestCoverage = DigestCoverage(),
    val issues: List<DigestIssue> = emptyList(),
)

class DigestViewModel(
    private val repository: ForumDigest,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(DigestState(kind = saved.get<DigestKind>("kind") ?: DigestKind.UPDATED))
    val state: StateFlow<DigestState> = mutableState.asStateFlow()
    private var refreshJob: Job? = null
    private val dismissed = mutableSetOf<String>()

    fun dismissError() {
        state.value.error?.let { dismissed += "${it.javaClass.name}:${it.message}" }
        mutableState.update { it.copy(error = null) }
    }

    private fun failed(failure: Throwable) {
        Log.w("ForumDigest", "Digest operation failed", failure)
        if ("${failure.javaClass.name}:${failure.message}" !in dismissed)
            mutableState.update {
                it.copy(
                    error = failure,
                )
            }
    }

    init {
        viewModelScope.launch {
            repository.content.collect { snapshot ->
                val groups = snapshot.groups
                val previews = snapshot.previews
                val rows =
                    groups
                        .flatMap { it.entries }
                        .distinctBy { it.topicId }
                        .map { entry ->
                            CatalogApp(
                                "topic:${entry.topicId}",
                                entry.topicId,
                                "",
                                entry.title,
                                entry.description,
                                "",
                            )
                        }
                        .withTopicPreviews(previews)
                        .filterIsInstance<CatalogApp>()
                        .associateBy { it.topicId }
                mutableState.update {
                    it.copy(groups = groups, cards = rows, issues = snapshot.issues)
                }
            }
        }
        viewModelScope.launch {
            repository.coverageChanges.collect { coverage ->
                mutableState.update { it.copy(coverage = coverage) }
                refresh()
            }
        }
    }

    fun select(kind: DigestKind) {
        saved["kind"] = kind
        mutableState.update { it.copy(kind = kind) }
    }

    fun refresh() {
        dismissed.clear()
        refreshJob?.cancel()
        mutableState.update { it.copy(loading = true, error = null) }
        refreshJob = viewModelScope.launch {
            try {
                val cached = repository.cached()
                val groups = repository.load(userInitiated = cached.isEmpty())
                mutableState.update { it.copy(loading = false) }
                for ((topicId) in groups.flatMap { it.entries }.distinctBy { it.topicId }) {
                    try {
                        repository.preview(
                            topicId,
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: IOException) {
                        failed(failure)
                    } catch (failure: IllegalArgumentException) {
                        failed(failure)
                    } catch (failure: IllegalStateException) {
                        failed(failure)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IOException) {
                failed(failure)
            } catch (failure: IllegalArgumentException) {
                failed(failure)
            } catch (failure: IllegalStateException) {
                failed(failure)
            } finally {
                if (currentCoroutineContext().isActive)
                    mutableState.update { it.copy(loading = false) }
            }
        }
    }
}

@Composable
fun DigestDestination(
    onOpen: (AppDestination) -> Unit,
    onBack: () -> Unit,
) {
    val model: DigestViewModel = koinViewModel()
    val state by model.state.collectAsStateWithLifecycle()
    val installed: InstalledAppsRepository = koinInject()
    val apps by installed.state.collectAsStateWithLifecycle()
    val appearance: SettingsRepository = koinInject()
    val theme by appearance.snapshots.collectAsStateWithLifecycle()
    ScreenFrame(onBack, null) {
        DigestScreen(
            state,
            model::select,
            model::refresh,
            model::dismissError,
            apps.apps,
            theme.installedCardAppearance,
        ) {
            onOpen(AppDestination.Details(it, null))
        }
    }
}

@Composable
fun DigestScreen(
    state: DigestState,
    onSelect: (DigestKind) -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    installed: List<InstalledApp> = emptyList(),
    appearance: InstalledCardAppearance = InstalledCardAppearance(),
    onTopic: (Int) -> Unit,
) {
    val kinds = listOf(DigestKind.UPDATED, DigestKind.NEW)
    val pager = rememberPagerState(initialPage = kinds.indexOf(state.kind)) { kinds.size }
    val scope = rememberCoroutineScope()
    val animationsEnabled = LocalAppAnimations.current
    LaunchedEffect(state.kind) {
        if (!pager.isScrollInProgress) pager.scrollToPage(kinds.indexOf(state.kind))
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { onSelect(kinds[it]) }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column {
            SectionSwitches(
                listOf(R.string.recent_updates, R.string.new_apps).map {
                    SectionSwitch(stringResource(it))
                },
                if (state.kind == DigestKind.UPDATED) 0 else 1,
                modifier =
                    Modifier.padding(
                        horizontal = AppLayout.content,
                        vertical = AppLayout.itemGap,
                    ),
            ) { index ->
                scope.launch {
                    if (animationsEnabled) pager.animateScrollToPage(index)
                    else pager.scrollToPage(index)
                }
            }
            if (state.loading) AppLoadingIndicator(Modifier.padding(AppLayout.content))
            if (state.error != null)
                AppErrorCard(
                    problem =
                        if (state.error is DigestContentException)
                            SourceProblem(
                                "4PDA · DIGEST_SECTION_MISSING",
                                stringResource(R.string.digest_sections_missing),
                            )
                        else
                            SourceProblem.from(
                                LocalContext.current,
                                "4PDA",
                                state.error,
                            ),
                    onRetry = onRetry,
                    onDismiss = onDismissError,
                    modifier = Modifier.padding(AppLayout.content),
                )
            HorizontalPager(pager, Modifier.weight(1f)) { page ->
                DigestPage(state, kinds[page], installed, appearance, onTopic)
            }
        }
    }
}

private fun digestCard(
    entry: DigestEntry,
    cards: Map<Int, CatalogApp>,
    installed: List<InstalledApp>,
): CatalogApp {
    val row =
        cards[entry.topicId]
            ?: CatalogApp(
                "topic:${entry.topicId}",
                entry.topicId,
                "",
                entry.title,
                entry.description,
                "",
            )
    val linked = installed.firstOrNull { it.forum.topicId == entry.topicId && it.installed }
    return row.copy(
        packageName = linked?.packageName ?: row.packageName,
        favorite = installed.any { it.forum.topicId == entry.topicId && it.preferences.favorite },
    )
}

@Composable
private fun DigestPage(
    state: DigestState,
    kind: DigestKind,
    installed: List<InstalledApp>,
    appearance: InstalledCardAppearance,
    onTopic: (Int) -> Unit,
) {
    val list = rememberLazyListState()
    ScrollToTopOnReselection(list, kind == state.kind)
    LazyColumn(
        state = list,
        contentPadding = AppLayout.listPadding,
        verticalArrangement = Arrangement.spacedBy(AppLayout.itemGap),
    ) {
        val requested = state.coverage.requested(kind)
        if (state.issues.size in 1 until requested)
            item {
                Text(
                    stringResource(
                        R.string.digest_available_issues,
                        requested,
                        state.issues.size,
                    ),
                )
            }
        val groups = state.groups.forDisplay(kind)
        if (state.issues.isEmpty() && !state.loading)
            item { Text(stringResource(R.string.catalog_empty)) }
        state.issues.take(requested).forEach { issue ->
            item("period:${issue.key}") {
                Text(
                    issue.period,
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            val periodGroups = groups.filter { it.period == issue.period }
            if (periodGroups.isEmpty())
                item("empty:${issue.key}") {
                    Text(stringResource(R.string.digest_interval_empty), Modifier.padding(12.dp))
                }
            periodGroups.forEach { group ->
                item("group:${issue.key}:${group.category}") {
                    Text(
                        group.category,
                        Modifier.padding(12.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                itemsIndexed(
                    group.entries,
                    key = { _, entry -> "topic:${entry.topicId}" },
                ) { _, entry ->
                    val card = digestCard(entry, state.cards, installed)
                    CatalogAppCard(
                        card,
                        installed,
                        { onTopic(entry.topicId) },
                        appearance,
                    )
                }
            }
        }
    }
}
