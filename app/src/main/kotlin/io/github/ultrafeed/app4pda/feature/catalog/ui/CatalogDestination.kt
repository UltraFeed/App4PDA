package io.github.ultrafeed.app4pda.feature.catalog.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.app.MainActivity
import io.github.ultrafeed.app4pda.app.navigation.AppDestination
import io.github.ultrafeed.app4pda.app.navigation.AppEntry
import io.github.ultrafeed.app4pda.app.shell.ScreenFrame
import io.github.ultrafeed.app4pda.core.ui.components.AppLoadingIndicator
import io.github.ultrafeed.app4pda.core.ui.components.SectionSwitch
import io.github.ultrafeed.app4pda.core.ui.components.SectionSwitches
import io.github.ultrafeed.app4pda.core.ui.theme.AppLayout
import io.github.ultrafeed.app4pda.core.ui.theme.LocalAppAnimations
import io.github.ultrafeed.app4pda.data.forum.ForumSections
import io.github.ultrafeed.app4pda.feature.catalog.CatalogQuery
import io.github.ultrafeed.app4pda.feature.catalog.CatalogSort
import io.github.ultrafeed.app4pda.feature.catalog.CatalogUiState
import io.github.ultrafeed.app4pda.feature.installed.InstalledApp
import io.github.ultrafeed.app4pda.feature.installed.InstalledCardAppearance
import io.github.ultrafeed.app4pda.feature.installed.data.InstalledAppsRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun CatalogDestination(
    activity: MainActivity,
    entry: AppEntry,
    onOpen: (AppDestination) -> Unit,
    onBack: () -> Unit,
) {
    val appearance by activity.appearanceSettings.snapshots.collectAsStateWithLifecycle()
    val settings = activity.appSettings
    val root = entry.destination == AppDestination.Categories
    val args =
        (entry.destination as? AppDestination.Catalog)
            ?: AppDestination.Catalog(
                category = 212,
            )
    val linkedPackage = args.linked
    val installedRepository: InstalledAppsRepository = koinInject()
    val installed by installedRepository.state.collectAsStateWithLifecycle()
    val linkedApp = installed.apps.firstOrNull { it.key == linkedPackage }
    val search = args.filter
    val model: CatalogViewModel = koinViewModel {
        parametersOf(
            CatalogQuery(
                args.topic,
                search,
                args.category,
                linkedPackage,
                linkedApp?.name,
                (linkedApp?.forum?.topicId ?: 0) > 0,
                if (search != null) CatalogSort.RECENTLY_ADDED
                else if (linkedApp != null) CatalogSort.NAME else settings.current.catalogSortOrder,
            ),
        )
    }
    val ui by model.state.collectAsStateWithLifecycle()
    val categoryTitle =
        if (search == null && linkedPackage == null && args.topic <= 0)
            args.name?.let { name ->
                ForumSections.titleResource(args.category)?.let { stringResource(it) } ?: name
            }
        else args.name

    ScreenFrame(
        onBack,
        if (root) null else categoryTitle ?: linkedApp?.name ?: stringResource(R.string.catalog),
    ) {
        Column {
            CatalogPages(ui, model, root) {
                CatalogResults(
                    ui,
                    model,
                    linkedPackage,
                    installed.apps,
                    appearance.installedCardAppearance,
                    onOpen,
                )
            }
        }
    }
}

@Composable
private fun CatalogPages(
    ui: CatalogUiState,
    model: CatalogViewModel,
    root: Boolean,
    content: @Composable () -> Unit,
) {
    val roots =
        listOf(
            212,
            213,
            529,
        )
    val pager =
        rememberPagerState(
            initialPage =
                roots
                    .indexOf(
                        ui.query.groupId,
                    )
                    .coerceAtLeast(
                        0,
                    ),
        ) {
            roots.size
        }
    val scope = rememberCoroutineScope()
    val animationsEnabled = LocalAppAnimations.current
    LaunchedEffect(
        pager,
        root,
    ) {
        if (root)
            snapshotFlow { pager.settledPage }
                .distinctUntilChanged()
                .collect { page ->
                    if (roots[page] != model.state.value.query.groupId)
                        model.section(
                            roots[page],
                        )
                }
    }

    Column {
        if (root)
            CatalogRootSwitches(roots.indexOf(ui.query.groupId).coerceAtLeast(0)) { index ->
                scope.launch {
                    if (animationsEnabled) pager.animateScrollToPage(index)
                    else pager.scrollToPage(index)
                }
            }

        if (root)
            HorizontalPager(
                pager,
                Modifier.weight(
                    1f,
                ),
            ) { page ->
                if (roots[page] == ui.query.groupId) content() else AppLoadingIndicator()
            }
        else content()
    }
}

@Composable
private fun CatalogRootSwitches(selected: Int, onSelect: (Int) -> Unit) {

    SectionSwitches(
        listOf(
                R.string.page_apps,
                R.string.page_games,
                R.string.page_emulators,
            )
            .map {
                SectionSwitch(
                    stringResource(
                        it,
                    ),
                )
            },
        selected,
        modifier =
            Modifier.padding(
                horizontal = AppLayout.content,
                vertical = AppLayout.itemGap,
            ),
    ) { index ->
        onSelect(index)
    }
}

@Composable
private fun CatalogResults(
    ui: CatalogUiState,
    model: CatalogViewModel,
    linkedPackage: String?,
    apps: List<InstalledApp>,
    cardAppearance: InstalledCardAppearance,
    onOpen: (AppDestination) -> Unit,
) {
    val resources = LocalResources.current
    CatalogScreen(
        ui,
        linkedPackage,
        model::changeDraft,
        model::search,
        { topic ->
            onOpen(
                AppDestination.Details(
                    topic,
                    null,
                    linkedPackage,
                ),
            )
        },
        model::nextPage,
        model::retry,
        { app ->
            onOpen(
                AppDestination.Details(
                    app.topicId,
                    app.packageName,
                    linkedPackage,
                ),
            )
        },
        installedCards = apps,
        onVisibleTopics = model::visibleTopics,
        cardAppearance = cardAppearance,
        onCategory = { category ->
            onOpen(
                AppDestination.Catalog(
                    category = category.id,
                    name =
                        ForumSections.titleResource(category.id)?.let { resources.getString(it) }
                            ?: category.name,
                ),
            )
        },
    )
}
