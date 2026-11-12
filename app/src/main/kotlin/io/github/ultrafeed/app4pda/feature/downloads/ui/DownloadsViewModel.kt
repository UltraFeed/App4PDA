package io.github.ultrafeed.app4pda.feature.downloads.ui

import android.database.SQLException
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ultrafeed.app4pda.feature.downloads.DownloadEntry
import io.github.ultrafeed.app4pda.feature.downloads.DownloadsSnapshot
import io.github.ultrafeed.app4pda.feature.downloads.DownloadsUiState
import io.github.ultrafeed.app4pda.feature.downloads.InstallRequest
import io.github.ultrafeed.app4pda.feature.downloads.TransferCommand
import io.github.ultrafeed.app4pda.feature.downloads.TransferTarget
import io.github.ultrafeed.app4pda.feature.downloads.data.DownloadsRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DownloadsViewModel(
    private val source: StateFlow<DownloadsSnapshot>,
    private val saved: SavedStateHandle,
    private val operations: DownloadOperations,
) : ViewModel() {

    constructor(
        repository: DownloadsRepository,
        saved: SavedStateHandle,
    ) : this(
        repository.state,
        saved,
        DownloadOperations(
            { retry -> if (retry) repository.retry() else repository.refresh() },
            repository::delete,
            repository::rename,
            repository::prepareInstall,
            repository::export,
            repository::transfer,
        ),
    )

    private val mutableState =
        MutableStateFlow(
            DownloadsUiState(),
        )
    private var exportWaiting = false
    private var dismissedListError: String? = null
    val state: StateFlow<DownloadsUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            source.collect { snapshot ->
                mutableState.update {
                    it.copy(
                        entries = snapshot.entries,
                        transfers = snapshot.transfers,
                        loading = snapshot.loading,
                        listError =
                            snapshot.error?.takeUnless { error -> error == dismissedListError },
                    )
                }
            }
        }
        refresh()
    }

    fun refresh(retryFailed: Boolean = false) {
        dismissedListError = null
        action { operations.reload(retryFailed) }
    }

    fun delete(entry: DownloadEntry): Unit = action {
        operations.deleteEntry(entry)
    }

    fun rename(entry: DownloadEntry, name: String): Unit = action {
        operations.renameEntry(entry, name)
    }

    fun transfer(
        target: TransferTarget,
        command: TransferCommand,
    ): Unit = action {
        operations.transfer(target, command)
    }

    fun install(entry: DownloadEntry): Unit = action {
        val request = operations.installEntry(entry)
        currentCoroutineContext().ensureActive()
        mutableState.update { it.copy(pendingInstall = request) }
    }

    fun beginExport(entry: DownloadEntry): Boolean {
        if (state.value.busy || exportWaiting || saved.get<String>("export_id") != null)
            return false
        saved["export_id"] = entry.id
        saved["export_path"] = entry.path
        saved["export_name"] = entry.fileName
        saved["export_group"] = entry.group
        return true
    }

    fun exportTo(destination: Uri?) {
        val id = saved.get<String>("export_id") ?: return
        saved.remove<String>("export_id")
        val entry =
            DownloadEntry(
                id,
                checkNotNull(saved.remove<String>("export_path")),
                checkNotNull(saved.remove<String>("export_name")),
                "",
                group = saved.remove<Boolean>("export_group") ?: false,
            )
        if (destination == null) return
        exportWaiting = true
        viewModelScope.launch {
            try {
                state.first { !it.busy }
                action {
                    mutableState.update { it.copy(exportProgress = 0f, exportComplete = false) }
                    try {
                        operations.exportEntry(entry, destination) { value ->
                            if (viewModelScope.isActive)
                                mutableState.update {
                                    it.copy(
                                        exportProgress = value,
                                    )
                                }
                        }
                        currentCoroutineContext().ensureActive()
                        mutableState.update { it.copy(exportComplete = true) }
                    } finally {
                        mutableState.update { it.copy(exportProgress = null) }
                    }
                }
            } finally {
                exportWaiting = false
            }
        }
    }

    fun consume(result: DownloadResult) {
        if (result == DownloadResult.ERROR) dismissedListError = state.value.listError
        mutableState.update { value ->
            when (result) {
                DownloadResult.INSTALL -> value.copy(pendingInstall = null)
                DownloadResult.EXPORT -> value.copy(exportComplete = false)
                DownloadResult.ERROR -> value.copy(error = null, listError = null)
            }
        }
    }

    private fun action(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                Log.w("Downloads", "File operation failed", error)
                mutableState.update { it.copy(error = "OPERATION_FAILED") }
            } catch (error: IllegalStateException) {
                currentCoroutineContext().ensureActive()
                Log.w("Downloads", "File operation failed", error)
                mutableState.update { it.copy(error = "OPERATION_FAILED") }
            } catch (error: SecurityException) {
                currentCoroutineContext().ensureActive()
                Log.w("Downloads", "File operation failed", error)
                mutableState.update { it.copy(error = "OPERATION_FAILED") }
            } catch (error: SQLException) {
                currentCoroutineContext().ensureActive()
                Log.w("Downloads", "File operation failed", error)
                mutableState.update { it.copy(error = "OPERATION_FAILED") }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }
}

enum class DownloadResult {
    INSTALL,
    EXPORT,
    ERROR,
}

/** The screen's file-operation port; tests can supply controlled file outcomes. */
class DownloadOperations(
    val reload: suspend (Boolean) -> Unit,
    val deleteEntry: suspend (DownloadEntry) -> Unit,
    val renameEntry: suspend (DownloadEntry, String) -> Unit,
    val installEntry: suspend (DownloadEntry) -> InstallRequest,
    val exportEntry: suspend (DownloadEntry, Uri, (Float) -> Unit) -> Unit,
    val transfer:
        suspend (
            TransferTarget,
            TransferCommand,
        ) -> Unit,
)
