package io.github.ultrafeed.app4pda.feature.downloads.ui

import android.R as AndroidR
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.app.navigation.ScrollToTopOnReselection
import io.github.ultrafeed.app4pda.core.formatting.DisplayFormats
import io.github.ultrafeed.app4pda.core.network.SourceProblem
import io.github.ultrafeed.app4pda.core.ui.components.AppErrorCard
import io.github.ultrafeed.app4pda.core.ui.components.ConfirmActionDialog
import io.github.ultrafeed.app4pda.core.ui.theme.AppLayout
import io.github.ultrafeed.app4pda.feature.downloads.ActiveDownload
import io.github.ultrafeed.app4pda.feature.downloads.DownloadEntry
import io.github.ultrafeed.app4pda.feature.downloads.DownloadsUiState
import io.github.ultrafeed.app4pda.feature.downloads.TransferCommand
import io.github.ultrafeed.app4pda.feature.downloads.TransferTarget
import io.github.ultrafeed.app4pda.feature.downloads.overallProgress

@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    onRefresh: () -> Unit,
    onDelete: (DownloadEntry) -> Unit,
    onRename: (DownloadEntry, String) -> Unit,
    onAction: (DownloadEntry, DownloadAction) -> Unit,
    onDismissError: () -> Unit,
    onTransfer:
        (
            TransferTarget,
            TransferCommand,
        ) -> Unit,
) {
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    var infoId by rememberSaveable { mutableStateOf<String?>(null) }
    val entries = state.entries
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column {
            DownloadStatus(state, onRefresh, onDismissError)
            if (entries.isEmpty() && state.transfers.isEmpty() && !state.loading)
                Text(
                    stringResource(R.string.downloads_empty),
                    Modifier.padding(AppLayout.section),
                )
            ReadyFilesList(
                entries,
                state.transfers,
                onTransfer,
                state.busy,
                onAction,
                onRename = {
                    renameText = it.fileName
                    renameId = it.id
                },
                onInfo = { infoId = it.id },
                onDelete = { deleteId = it.id },
                modifier = Modifier.weight(1f),
            )
        }
    }
    state.entries
        .find { it.id == deleteId }
        ?.let { entry ->
            ConfirmActionDialog(
                title = stringResource(R.string.menu_delete),
                text = entry.fileName,
                onConfirm = {
                    deleteId = null
                    onDelete(entry)
                },
                onDismiss = { deleteId = null },
            )
        }

    state.entries
        .find { it.id == renameId }
        ?.let { entry ->
            RenameDownloadDialog(
                entry,
                renameText,
                { renameText = it },
                onRename,
                { renameId = null },
            )
        }
    state.entries
        .find { it.id == infoId }
        ?.let { entry ->
            DownloadInfoDialog(entry) { infoId = null }
        }
}

@Composable
private fun DownloadCard(
    entry: DownloadEntry,
    busy: Boolean,
    onAction: (DownloadEntry, DownloadAction) -> Unit,
    onRename: (DownloadEntry) -> Unit,
    onInfo: (DownloadEntry) -> Unit,
    onDelete: (DownloadEntry) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = { onAction(entry, DownloadAction.OPEN) },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Column(Modifier.padding(AppLayout.content)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppLayout.itemGap),
                ) {
                    DownloadDescription(entry, enabled = !busy)
                }
                Box {
                    IconButton(
                        onClick = { menu = true },
                    ) {
                        Icon(Icons.Default.MoreVert, null)
                    }
                    DownloadMenu(
                        entry,
                        busy,
                        menu,
                        { menu = false },
                        onAction,
                        onRename,
                        onInfo,
                        onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadDescription(entry: DownloadEntry, enabled: Boolean) {
    val secondaryColor =
        if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else LocalContentColor.current
    Text(entry.title, style = MaterialTheme.typography.titleMediumEmphasized)
    if (entry.packageName.isNotBlank())
        Text(
            entry.packageName,
            style = MaterialTheme.typography.bodySmall,
            color = secondaryColor,
        )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AppLayout.content),
        verticalArrangement = Arrangement.spacedBy(AppLayout.itemGap),
    ) {
        if (entry.version.isNotBlank())
            Text(
                entry.version,
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryColor,
            )
        if (entry.totalBytes > 0)
            Text(
                DisplayFormats.fileSize(LocalContext.current, entry.totalBytes),
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryColor,
            )
    }
    if (!entry.group && entry.fileName != entry.title)
        Text(
            entry.fileName,
            style = MaterialTheme.typography.bodySmall,
            color = secondaryColor,
        )
}

@Composable
private fun RenameDownloadDialog(
    entry: DownloadEntry,
    renameText: String,
    onTextChanged: (String) -> Unit,
    onRename: (DownloadEntry, String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text(stringResource(R.string.menu_downloads_rename)) },
        text = {
            OutlinedTextField(
                renameText,
                onTextChanged,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onRename(entry, renameText)
                    onDismiss()
                },
                enabled = renameText.isNotBlank(),
            ) {
                Text(stringResource(AndroidR.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text(stringResource(AndroidR.string.cancel))
            }
        },
    )
}

@Composable
private fun DownloadInfoDialog(entry: DownloadEntry, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_info)) },
        text = {
            Text(
                listOf(
                        entry.fileName,
                        entry.path,
                        entry.packageName,
                        entry.version,
                        DisplayFormats.fileSize(
                            LocalContext.current,
                            entry.totalBytes,
                        ),
                        entry.note.orEmpty(),
                    )
                    .filter { it.isNotBlank() }
                    .joinToString("\n"),
                Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(AndroidR.string.ok))
            }
        },
    )
}

@Composable
private fun DownloadStatus(
    state: DownloadsUiState,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
) {
    (state.error ?: state.listError)?.let { error ->
        AppErrorCard(
            problem =
                SourceProblem(
                    if (error.contains(" · ")) error.substringBefore('\n')
                    else "Downloads · OPERATION_FAILED",
                    if (error.contains('\n')) error.substringAfter('\n')
                    else stringResource(R.string.source_problem_unknown),
                ),
            onRetry = {
                onDismissError()
                onRefresh()
            },
            onDismiss = onDismissError,
            modifier = Modifier.padding(AppLayout.content),
        )
    }
    state.transfers.overallProgress()?.let { progress ->
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
    state.exportProgress?.let { progress ->
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReadyFilesList(
    entries: List<DownloadEntry>,
    transfers: List<ActiveDownload>,
    onTransfer:
        (
            TransferTarget,
            TransferCommand,
        ) -> Unit,
    busy: Boolean,
    onAction: (DownloadEntry, DownloadAction) -> Unit,
    onRename: (DownloadEntry) -> Unit,
    onInfo: (DownloadEntry) -> Unit,
    onDelete: (DownloadEntry) -> Unit,
    modifier: Modifier,
) {
    val list = rememberLazyListState()
    ScrollToTopOnReselection(list)
    LazyColumn(
        state = list,
        modifier = modifier,
        contentPadding = AppLayout.listPadding,
        verticalArrangement = Arrangement.spacedBy(AppLayout.itemGap),
    ) {
        items(transfers, key = { "transfer:${it.target}" }) { transfer ->
            TransferCard(transfer, onTransfer)
        }
        items(entries, key = { it.id }) { entry ->
            DownloadCard(
                entry = entry,
                busy = busy,
                onAction = onAction,
                onRename = onRename,
                onInfo = onInfo,
                onDelete = onDelete,
            )
        }
    }
}
