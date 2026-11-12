package io.github.ultrafeed.app4pda.feature.installed.ui

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import io.github.ultrafeed.app4pda.core.ui.theme.LocalAppAnimations
import io.github.ultrafeed.app4pda.feature.installed.AppSection
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@Composable
internal fun InstalledPagerSelector(
    ui: InstalledAppsUiState,
    tabs: List<AppSection>,
    pager: PagerState,
) {
    val scope = rememberCoroutineScope()
    val animationsEnabled = LocalAppAnimations.current
    InstalledSections(ui, tabs) { index ->
        scope.launch {
            if (animationsEnabled) pager.animateScrollToPage(index) else pager.scrollToPage(index)
        }
    }
}

@Composable
internal fun InstalledPages(
    ui: InstalledAppsUiState,
    tabs: List<AppSection>,
    pager: PagerState,
    positions: SaveableStateHolder,
    modifier: Modifier,
    content: @Composable (AppSection) -> Unit,
) {
    val pageContent: @Composable (AppSection) -> Unit = { section ->
        positions.SaveableStateProvider("${section}:${ui.sorting.storageValue}") {
            content(section)
        }
    }
    if (tabs.isNotEmpty()) HorizontalPager(pager, modifier) { page -> pageContent(tabs[page]) }
    else pageContent(ui.section)
}

@Composable
internal fun rememberInstalledPager(
    tabs: List<AppSection>,
    section: AppSection,
    onSection: (AppSection) -> Unit,
): PagerState {
    val pager =
        rememberPagerState(initialPage = tabs.indexOf(section).coerceAtLeast(0)) {
            tabs.size.coerceAtLeast(1)
        }
    LaunchedEffect(tabs, section) {
        if (tabs.isNotEmpty()) {
            val index = tabs.indexOf(section)
            if (index < 0) onSection(AppSection.APPLICATIONS)
            else if (!pager.isScrollInProgress && pager.currentPage != index)
                pager.scrollToPage(
                    index,
                )
        }
    }
    LaunchedEffect(pager, tabs, section) {
        snapshotFlow { pager.settledPage }
            .drop(1)
            .distinctUntilChanged()
            .collect { page ->
                tabs.getOrNull(page)?.let(onSection)
            }
    }
    return pager
}
