package io.github.ultrafeed.app4pda.feature.settings.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ultrafeed.app4pda.feature.settings.SettingAction
import io.github.ultrafeed.app4pda.feature.settings.SettingId
import io.github.ultrafeed.app4pda.feature.settings.SettingKind
import io.github.ultrafeed.app4pda.feature.settings.SettingsSnapshot
import io.github.ultrafeed.app4pda.feature.settings.data.SettingsRepository
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsDialog(val key: SettingId, val draft: String, val error: String? = null)

data class SettingsUiState(
    val snapshot: SettingsSnapshot,
    val dialog: SettingsDialog? = null,
    val busy: Boolean = false,
    val actionFailed: Boolean = false,
    val cacheClearFailed: Boolean = false,
    val confirmLog: Boolean = false,
    val preparingLog: Boolean = false,
    val preparedLog: String? = null,
)

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val savedState: SavedStateHandle,
    prepareLogFile: suspend () -> File = repository::prepareSessionLog,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(
            SettingsUiState(
                repository.snapshots.value,
                savedState
                    .get<String>("dialog_key")
                    ?.let { name -> SettingId.entries.find { it.name == name } }
                    ?.let {
                        SettingsDialog(it, savedState["dialog_draft"] ?: "")
                    },
            ),
        )
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    val log: SettingsLog = SettingsLog(viewModelScope, savedState, mutableState, prepareLogFile)

    init {
        viewModelScope.launch {
            repository.snapshots.collect {
                mutableState.value = mutableState.value.copy(snapshot = it)
            }
        }
        refreshCaches()
        if (savedState.get<Boolean>("confirm_log") == true) log.confirm()
    }

    fun toggle(key: SettingId) {
        val item = state.value.snapshot.items.find { it.key == key && it.enabled } ?: return
        if (item.confirmation != null) edit(key, "false")
        else saveValue(key, (!item.value.toBoolean()).toString())
    }

    fun edit(key: SettingId, draft: String? = null) {
        if (state.value.busy) return
        val item =
            state.value.snapshot.items.find { it.key == key && it.enabled && it.visible } ?: return
        val initial = if (item.kind == SettingKind.COLOR) item.summary else item.value
        setDialog(SettingsDialog(key, draft ?: initial))
    }

    fun changeDraft(value: String) {
        if (state.value.busy) return
        setDialog(state.value.dialog?.copy(draft = value, error = null))
    }

    fun dismissDialog() {
        if (state.value.busy) return
        setDialog(null)
        mutableState.value = state.value.copy(actionFailed = false)
    }

    fun saveDialog() {
        if (state.value.busy) return
        val dialog = state.value.dialog ?: return
        val action = state.value.snapshot.items.find { it.key == dialog.key }?.action
        if (action == SettingAction.CLEAR_CACHE) {
            dismissDialog()
            mutableState.value = state.value.copy(busy = true, cacheClearFailed = false)
            viewModelScope.launch {
                try {
                    repository.clearCache()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    mutableState.value =
                        state.value.copy(actionFailed = true, cacheClearFailed = true)
                } finally {
                    mutableState.value = state.value.copy(busy = false)
                }
            }
        } else saveValue(dialog.key, dialog.draft)
    }

    private fun saveValue(key: SettingId, value: String) {
        if (state.value.busy) return
        mutableState.value = state.value.copy(busy = true, actionFailed = false)
        viewModelScope.launch {
            try {
                val error = repository.setValue(key, value)
                currentCoroutineContext().ensureActive()
                if (error == null) {
                    setDialog(null)
                    mutableState.value = state.value.copy(snapshot = repository.snapshots.value)
                } else setDialog(state.value.dialog?.copy(error = error))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.value = state.value.copy(actionFailed = true)
            } finally {
                mutableState.value = state.value.copy(busy = false)
            }
        }
    }

    private fun setDialog(dialog: SettingsDialog?) {
        savedState["dialog_key"] = dialog?.key?.name
        savedState["dialog_draft"] = dialog?.draft
        mutableState.value = state.value.copy(dialog = dialog)
    }

    fun dismissError() {
        mutableState.value = state.value.copy(actionFailed = false)
    }

    private fun refreshCaches() {
        viewModelScope.launch {
            try {
                repository.refreshCacheSizes()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                /* Cache statistics are optional; settings remain usable. */
            }
        }
    }

    fun refresh() {
        repository.refresh()
        refreshCaches()
    }
}
