package io.github.ultrafeed.app4pda.feature.installed.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.ui.components.SectionSwitch
import io.github.ultrafeed.app4pda.core.ui.components.SectionSwitches
import io.github.ultrafeed.app4pda.core.ui.theme.AppLayout
import io.github.ultrafeed.app4pda.feature.installed.AppSection

fun sectionTitle(section: AppSection): Int =
    when (section) {
        AppSection.APPLICATIONS -> R.string.page_apps
        AppSection.UNLINKED -> R.string.page_no_links
        AppSection.PENDING_LINKING -> R.string.page_pending_linking
        AppSection.FAVORITES -> R.string.favorites
        AppSection.UPDATES -> R.string.available_updates
        AppSection.HIDDEN -> R.string.hidden
    }

@Composable
internal fun InstalledSections(
    ui: InstalledAppsUiState,
    tabs: List<AppSection>,
    onSelect: (Int) -> Unit,
) {
    if (tabs.isNotEmpty())
        SectionSwitches(
            tabs.map { section ->
                SectionSwitch(
                    stringResource(sectionTitle(section)),
                    ui.source.counts[section] ?: 0,
                    section == AppSection.UPDATES,
                )
            },
            tabs.indexOf(ui.section).coerceAtLeast(0),
            modifier =
                Modifier.padding(
                    horizontal = AppLayout.content,
                    vertical = AppLayout.itemGap,
                ),
        ) { index ->
            onSelect(index)
        }
}
