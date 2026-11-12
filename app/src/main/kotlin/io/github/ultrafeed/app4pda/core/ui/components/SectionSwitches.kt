package io.github.ultrafeed.app4pda.core.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.ultrafeed.app4pda.core.ui.theme.AppLayout

/** One shared single-choice surface for page selectors; page owners retain pager state. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SectionSwitches(
    items: List<SectionSwitch>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        Row(
            Modifier.selectableGroup()
                .horizontalScroll(rememberScrollState())
                .widthIn(min = maxWidth),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEachIndexed { index, item ->
                val visibility = remember { BringIntoViewRequester() }
                LaunchedEffect(selected == index, item.title) {
                    if (selected == index) visibility.bringIntoView()
                }
                ToggleButton(
                    checked = selected == index,
                    onCheckedChange = { onSelect(index) },
                    modifier =
                        Modifier.padding(horizontal = AppLayout.itemGap / 2)
                            .width(IntrinsicSize.Max)
                            .bringIntoViewRequester(visibility)
                            .semantics { role = Role.RadioButton },
                ) {
                    item.count?.let { count ->
                        Badge(
                            Modifier.padding(end = 8.dp),
                            containerColor =
                                if (item.alert) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor =
                                if (item.alert) MaterialTheme.colorScheme.onError
                                else MaterialTheme.colorScheme.onSecondaryContainer,
                        ) {
                            Text(
                                "$count",
                            )
                        }
                    }
                    Text(item.title, maxLines = 1)
                }
            }
        }
    }
}
