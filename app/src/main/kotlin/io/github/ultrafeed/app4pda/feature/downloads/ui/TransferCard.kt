package io.github.ultrafeed.app4pda.feature.downloads.ui

import android.R as AndroidR
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.ui.theme.AppLayout
import io.github.ultrafeed.app4pda.feature.downloads.ActiveDownload
import io.github.ultrafeed.app4pda.feature.downloads.TransferCommand
import io.github.ultrafeed.app4pda.feature.downloads.TransferState
import io.github.ultrafeed.app4pda.feature.downloads.TransferTarget

@Composable
internal fun TransferCard(
    transfer: ActiveDownload,
    onCommand: (TransferTarget, TransferCommand) -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Column(
            Modifier.padding(AppLayout.content),
            verticalArrangement = Arrangement.spacedBy(AppLayout.itemGap),
        ) {
            Text(transfer.name, style = MaterialTheme.typography.titleMediumEmphasized)
            Text(
                stringResource(
                    when (transfer.state) {
                        TransferState.CANCELLING -> R.string.transfer_cancelling
                        TransferState.QUEUED -> R.string.transfer_queued
                        TransferState.RUNNING -> R.string.transfer_running
                        TransferState.PAUSED -> R.string.transfer_paused
                        TransferState.FAILED -> R.string.transfer_failed
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            transfer.progress?.let { progress ->
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            if (transfer.state != TransferState.CANCELLING)
                FlowRow {
                    val command =
                        when (transfer.state) {
                            TransferState.PAUSED -> TransferCommand.RESUME
                            TransferState.FAILED -> TransferCommand.RETRY
                            else -> TransferCommand.PAUSE
                        }
                    TextButton({ onCommand(transfer.target, command) }) {
                        Text(
                            stringResource(
                                when (command) {
                                    TransferCommand.RESUME -> R.string.transfer_resume
                                    TransferCommand.RETRY -> R.string.action_retry
                                    else -> R.string.transfer_pause
                                },
                            ),
                        )
                    }
                    TextButton({ onCommand(transfer.target, TransferCommand.CANCEL) }) {
                        Text(stringResource(AndroidR.string.cancel))
                    }
                }
        }
    }
}
