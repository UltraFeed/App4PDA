package io.github.ultrafeed.app4pda.feature.settings.ui

import android.R as AndroidR
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardType
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.app.navigation.ScrollToTopOnReselection
import io.github.ultrafeed.app4pda.core.network.SourceProblem
import io.github.ultrafeed.app4pda.core.ui.components.AppErrorCard
import io.github.ultrafeed.app4pda.core.ui.components.AppLoadingIndicator
import io.github.ultrafeed.app4pda.core.ui.components.RadioChoiceItem
import io.github.ultrafeed.app4pda.core.ui.theme.AppLayout
import io.github.ultrafeed.app4pda.feature.settings.SettingAction
import io.github.ultrafeed.app4pda.feature.settings.SettingItem
import io.github.ultrafeed.app4pda.feature.settings.SettingKind
import io.github.ultrafeed.app4pda.feature.settings.SettingsPage

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    screen: SettingsPage,
    onClick: (SettingItem) -> Unit,
    onDraftChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onErrorDismiss: () -> Unit = {},
    onErrorRetry: () -> Unit = {},
    hiddenCount: Int = 0,
) {
    val list = rememberLazyListState()
    ScrollToTopOnReselection(list)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column {
            if (state.busy && state.dialog == null)
                AppLoadingIndicator(Modifier.padding(AppLayout.content))
            if (state.actionFailed && state.dialog == null)
                AppErrorCard(
                    problem =
                        SourceProblem(
                            if (state.cacheClearFailed) "Settings · CACHE_CLEAR"
                            else "Settings · OPERATION_FAILED",
                            stringResource(
                                if (state.cacheClearFailed) R.string.cache_clear_incomplete
                                else R.string.action_failed,
                            ),
                        ),
                    onRetry = onErrorRetry,
                    onDismiss = onErrorDismiss,
                    modifier = Modifier.padding(AppLayout.content),
                )
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f),
                contentPadding = AppLayout.listPadding,
            ) {
                val settings = state.snapshot.screen(screen)
                itemsIndexed(settings, key = { _, item -> item.key }) { index, _ ->
                    SettingsRow(settings, index, state.busy, hiddenCount, onClick)
                }
            }
        }
        state.dialog?.let { dialog ->
            state.snapshot.items
                .find { it.key == dialog.key }
                ?.let { item ->
                    SettingsValueDialog(
                        item,
                        dialog,
                        state.busy,
                        state.actionFailed,
                        onDraftChanged,
                        onSave,
                        onDismiss,
                    )
                }
        }
    }
}

private fun settingsPageIcon(page: SettingsPage): ImageVector =
    when (page) {
        SettingsPage.ROOT -> Icons.Rounded.Settings
        SettingsPage.APPEARANCE -> Icons.Rounded.Palette
        SettingsPage.SOURCES -> Icons.Rounded.SystemUpdate
        SettingsPage.DOWNLOADS -> Icons.Rounded.Download
        SettingsPage.NOTIFICATIONS -> Icons.Rounded.Notifications
        SettingsPage.CACHE -> Icons.Rounded.Storage
        SettingsPage.ADVANCED -> Icons.Rounded.Tune
        SettingsPage.ACCOUNTS -> Icons.Rounded.AccountCircle
    }

@Composable
private fun SettingsValueDialog(
    item: SettingItem,
    dialog: SettingsDialog,
    busy: Boolean,
    saveFailed: Boolean,
    onDraft: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(item.title) },
        text = {
            Column {
                SettingEditor(item, dialog, busy, onDraft, onSave)
                val error =
                    dialog.error ?: if (saveFailed) stringResource(R.string.action_failed) else null
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (busy) AppLoadingIndicator(Modifier)
            }
        },
        confirmButton = {
            if (item.kind != SettingKind.CHOICE)
                TextButton(onClick = onSave, enabled = !busy) {
                    Text(stringResource(AndroidR.string.ok))
                }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(AndroidR.string.cancel))
            }
        },
    )
}

@Composable
private fun SettingsRow(
    settings: List<SettingItem>,
    index: Int,
    busy: Boolean,
    hiddenCount: Int,
    onClick: (SettingItem) -> Unit,
) {
    val item = settings[index]

    if (item.kind == SettingKind.CATEGORY) {
        Text(
            item.title,
            Modifier.padding(vertical = AppLayout.content),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium,
        )
    } else {
        val enabled = item.enabled && !busy
        val start =
            (0 until index).lastOrNull { settings[it].kind == SettingKind.CATEGORY }?.plus(1) ?: 0
        val end =
            (index + 1..settings.lastIndex).firstOrNull {
                settings[it].kind == SettingKind.CATEGORY
            } ?: settings.size
        SegmentedListItem(
            onClick = { onClick(item) },
            enabled = enabled,
            shapes = ListItemDefaults.segmentedShapes(index - start, end - start),
            modifier =
                Modifier.padding(vertical = ListItemDefaults.SegmentedGap / 2).semantics {
                    if (item.kind == SettingKind.SWITCH) {
                        role = Role.Switch
                        toggleableState = ToggleableState(item.value.toBoolean())
                    }
                },
            colors =
                ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            content = { Text(item.title) },
            leadingContent =
                item.destination?.let { page ->
                    { Icon(settingsPageIcon(page), contentDescription = null) }
                },
            supportingContent =
                if (item.summary.isNotEmpty())
                    ({
                        Text(item.summary)
                    })
                else null,
            trailingContent = settingsTrailing(item, enabled, hiddenCount),
        )
    }
}

private fun settingsTrailing(
    item: SettingItem,
    enabled: Boolean,
    hiddenCount: Int,
): (@Composable () -> Unit)? =
    when {
        item.action == SettingAction.HIDDEN -> ({
                Badge(Modifier) {
                    Text(
                        "$hiddenCount",
                    )
                }
            })

        item.kind == SettingKind.SWITCH -> ({
                Switch(
                    item.value.toBoolean(),
                    onCheckedChange = null,
                    enabled = enabled,
                )
            })

        item.kind == SettingKind.SCREEN -> ({
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    null,
                )
            })

        else -> null
    }

@Composable
private fun ColumnScope.SettingEditor(
    item: SettingItem,
    dialog: SettingsDialog,
    busy: Boolean,
    onDraft: (String) -> Unit,
    onSave: () -> Unit,
) {
    when {
        item.confirmation != null -> Text(item.confirmation)
        item.kind == SettingKind.CHOICE ->
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(item.options, key = { it.value }) { option ->
                    val selected = dialog.draft == option.value
                    RadioChoiceItem(
                        title = option.title,
                        selected = selected,
                        enabled = !busy,
                        onClick = {
                            onDraft(option.value)
                            onSave()
                        },
                    )
                }
            }

        else ->
            OutlinedTextField(
                value = dialog.draft,
                onValueChange = onDraft,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !busy,
                isError = dialog.error != null,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            if (item.kind == SettingKind.NUMBER) KeyboardType.Number
                            else KeyboardType.Text,
                    ),
                label = {
                    Text(
                        if (item.kind == SettingKind.COLOR) stringResource(R.string.color_argb_hint)
                        else item.title,
                    )
                },
            )
    }
}
