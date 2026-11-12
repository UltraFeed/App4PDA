package io.github.ultrafeed.app4pda.feature.settings.ui

import android.R as AndroidR
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.ui.components.AppLoadingIndicator

@Composable
fun SettingsExtraDialogs(
    state: SettingsUiState,
    onLogDismiss: () -> Unit,
    onPrepareLog: () -> Unit,
) {
    if (state.confirmLog)
        AlertDialog(
            onDismissRequest = onLogDismiss,
            title = {
                Text(
                    stringResource(
                        R.string.settings_extended_send_log,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        R.string.settings_other_send_log_message,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onPrepareLog,
                ) {
                    Text(
                        stringResource(
                            AndroidR.string.ok,
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onLogDismiss,
                ) {
                    Text(
                        stringResource(
                            AndroidR.string.cancel,
                        ),
                    )
                }
            },
        )
    if (state.preparingLog)
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    stringResource(
                        R.string.settings_extended_send_log,
                    ),
                )
            },
            text = { AppLoadingIndicator() },
            confirmButton = {},
        )
}
