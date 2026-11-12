package io.github.ultrafeed.app4pda.feature.settings.ui

import androidx.lifecycle.SavedStateHandle
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Owns confirmation and one-shot delivery of the diagnostic file. */
class SettingsLog
internal constructor(
    private val scope: CoroutineScope,
    private val savedState: SavedStateHandle,
    private val mutableState: MutableStateFlow<SettingsUiState>,
    private val prepareLogFile: suspend () -> File,
) {
    private val state
        get() = mutableState

    fun confirm() {
        if (state.value.preparingLog || state.value.preparedLog != null) return
        savedState["confirm_log"] = true
        mutableState.value = state.value.copy(confirmLog = true)
    }

    fun dismiss() {
        savedState["confirm_log"] = false
        mutableState.value = state.value.copy(confirmLog = false)
    }

    fun prepare() {
        if (state.value.preparingLog || state.value.preparedLog != null) return
        dismiss()
        mutableState.value = state.value.copy(preparingLog = true)
        scope.launch {
            try {
                val path = prepareLogFile().absolutePath
                currentCoroutineContext().ensureActive()
                mutableState.value = state.value.copy(preparedLog = path)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.value = state.value.copy(actionFailed = true)
            } finally {
                mutableState.value = state.value.copy(preparingLog = false)
            }
        }
    }

    fun takePrepared(): File? {
        val path = state.value.preparedLog ?: return null
        mutableState.value = state.value.copy(preparedLog = null)
        return File(path)
    }
}
