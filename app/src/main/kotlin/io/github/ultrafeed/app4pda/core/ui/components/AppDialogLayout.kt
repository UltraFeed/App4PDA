package io.github.ultrafeed.app4pda.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import io.github.ultrafeed.app4pda.core.ui.theme.AppLayout

/**
 * Form content scrolls independently of its title and actions. The navigation scene owns the
 * dialog.
 */
@Composable
internal fun AppDialogLayout(
    title: String,
    actions: @Composable FlowRowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val available =
        with(
            LocalDensity.current,
        ) {
            LocalWindowInfo.current.containerSize.height.toDp()
        }
    Surface(
        shape = AlertDialogDefaults.shape,
        color = AlertDialogDefaults.containerColor,
        contentColor = AlertDialogDefaults.textContentColor,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .imePadding()
                .heightIn(max = available * AppLayout.DIALOG_WINDOW_FRACTION)
                .padding(AppLayout.section),
            verticalArrangement =
                Arrangement.spacedBy(
                    AppLayout.content,
                ),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = AlertDialogDefaults.titleContentColor,
            )
            Column(
                Modifier.weight(
                        1f,
                        fill = false,
                    )
                    .fillMaxWidth()
                    .verticalScroll(
                        rememberScrollState(),
                    ),
                verticalArrangement = Arrangement.spacedBy(AppLayout.content),
                content = content,
            )
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        AppLayout.itemGap,
                        Alignment.End,
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        AppLayout.itemGap,
                    ),
                content = actions,
            )
        }
    }
}
