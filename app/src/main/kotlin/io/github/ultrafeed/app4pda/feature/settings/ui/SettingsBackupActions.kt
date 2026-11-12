package io.github.ultrafeed.app4pda.feature.settings.ui

import android.R as AndroidR
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.ui.components.AppLoadingIndicator
import io.github.ultrafeed.app4pda.core.ui.components.ConfirmActionDialog
import io.github.ultrafeed.app4pda.feature.settings.SettingAction
import io.github.ultrafeed.app4pda.feature.settings.data.BackupImport
import io.github.ultrafeed.app4pda.feature.settings.data.BackupResult
import io.github.ultrafeed.app4pda.feature.settings.data.SettingsBackupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

internal data class BackupUiState(
    val busy: Boolean = false,
    val plan: BackupImport? = null,
    val result: BackupResult? = null,
    val failed: Boolean = false,
    val exported: Boolean = false,
) {
    val hasResult: Boolean
        get() = result != null || exported
}

internal class SettingsBackupViewModel(private val repository: SettingsBackupRepository) :
    ViewModel() {
    private val mutable = MutableStateFlow(BackupUiState())
    val state = mutable.asStateFlow()

    private fun run(operation: suspend () -> BackupUiState) {
        if (state.value.busy) return
        mutable.value = BackupUiState(busy = true)
        viewModelScope.launch {
            try {
                mutable.value = operation()
            } catch (cancelled: CancellationException) {
                mutable.value = BackupUiState()
                throw cancelled
            } catch (_: Exception) {
                mutable.value = BackupUiState(failed = true)
            }
        }
    }

    fun export(uri: Uri) = run {
        repository.export(uri)
        BackupUiState(exported = true)
    }

    fun prepare(uri: Uri) = run { BackupUiState(plan = repository.prepare(uri)) }

    fun apply() {
        val plan = state.value.plan ?: return
        run { BackupUiState(result = repository.apply(plan)) }
    }

    fun dismiss() {
        if (!state.value.busy) mutable.value = BackupUiState()
    }
}

/** SAF launchers belong to UI; validated data survives recomposition in the entry's ViewModel. */
@Composable
internal fun SettingsBackupActions(action: SettingAction?, onConsumed: () -> Unit) {
    val model: SettingsBackupViewModel = koinViewModel()
    val state by model.state.collectAsStateWithLifecycle()
    var exportConfirmation by remember { mutableStateOf(false) }
    val create =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) {
            it?.let(model::export)
        }
    val open =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            it?.let(model::prepare)
        }
    LaunchedEffect(action) {
        when (action) {
            SettingAction.EXPORT_BACKUP -> exportConfirmation = true
            SettingAction.IMPORT_BACKUP -> open.launch(arrayOf("application/json", "text/plain"))
            else -> Unit
        }
        if (action != null) onConsumed()
    }
    if (exportConfirmation)
        ConfirmActionDialog(
            title = stringResource(R.string.export_backup),
            text = stringResource(R.string.backup_sensitive),
            onConfirm = {
                exportConfirmation = false
                create.launch("app4pda-settings.json")
            },
            onDismiss = { exportConfirmation = false },
        )
    state.plan?.let { plan ->
        ConfirmActionDialog(
            title = stringResource(R.string.import_backup),
            text = stringResource(R.string.backup_confirm, plan.count, plan.skipped),
            onConfirm = model::apply,
            onDismiss = model::dismiss,
        )
    }
    BackupStatusDialog(state, model::dismiss)
}

@Composable
private fun BackupStatusDialog(state: BackupUiState, onDismiss: () -> Unit) {
    if (state.busy || state.failed || state.hasResult)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.settings)) },
            text = {
                when {
                    state.busy -> AppLoadingIndicator()

                    state.exported -> Text(stringResource(R.string.backup_exported))
                    state.failed -> Text(stringResource(R.string.backup_failed))
                    state.result?.partial == true ->
                        Text(
                            stringResource(
                                R.string.backup_partial,
                                state.result.applied,
                                state.result.skipped,
                            ),
                        )

                    else ->
                        Text(
                            stringResource(
                                R.string.backup_done,
                                state.result?.applied ?: 0,
                                state.result?.skipped ?: 0,
                            ),
                        )
                }
            },
            confirmButton = {
                TextButton(onDismiss, enabled = !state.busy) {
                    Text(stringResource(AndroidR.string.ok))
                }
            },
        )
}
