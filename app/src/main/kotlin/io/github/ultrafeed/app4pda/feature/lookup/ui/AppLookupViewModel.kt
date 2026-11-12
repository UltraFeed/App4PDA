package io.github.ultrafeed.app4pda.feature.lookup.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ultrafeed.app4pda.feature.lookup.data.AppLookupMatch
import io.github.ultrafeed.app4pda.feature.lookup.data.AppLookupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AppLookupUiState {
    data object Loading : AppLookupUiState

    data class Found(val app: AppLookupMatch) : AppLookupUiState

    data object NotFound : AppLookupUiState

    data object Error : AppLookupUiState
}

class AppLookupViewModel(
    private val repository: AppLookupRepository,
    private val saved: SavedStateHandle,
    private val packageName: String,
) : ViewModel() {
    private val restoredMatch =
        saved
            .get<Int>(FOUND_TOPIC)
            ?.takeIf { it >= 0 }
            ?.let {
                AppLookupMatch(it, saved.get<String>(FOUND_PACKAGE).orEmpty())
            }
    private val mutableState =
        MutableStateFlow<AppLookupUiState>(
            restoredMatch?.let { AppLookupUiState.Found(it) } ?: AppLookupUiState.NotFound,
        )
    val state = mutableState.asStateFlow()

    init {
        if (restoredMatch == null && packageName.isNotBlank()) load()
    }

    fun retry() {
        if (state.value == AppLookupUiState.Error) load()
    }

    fun consumeResult(): AppLookupMatch? {
        val found =
            (state.value as? AppLookupUiState.Found)?.takeUnless {
                saved.get<Boolean>(RESULT_CONSUMED) == true
            } ?: return null
        saved[RESULT_CONSUMED] = true
        return found.app
    }

    fun hasPendingResult() =
        state.value is AppLookupUiState.Found && saved.get<Boolean>(RESULT_CONSUMED) != true

    private fun load() {
        mutableState.value = AppLookupUiState.Loading
        viewModelScope.launch {
            try {
                val match = repository.find(packageName)
                ensureActive()
                if (match != null) {
                    saved[FOUND_TOPIC] = match.topicId
                    saved[FOUND_PACKAGE] = match.packageName
                }
                mutableState.value =
                    match?.let { AppLookupUiState.Found(it) } ?: AppLookupUiState.NotFound
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureActive()
                mutableState.value = AppLookupUiState.Error
            }
        }
    }

    companion object {
        private const val RESULT_CONSUMED = "lookup_result_consumed"
        private const val FOUND_TOPIC = "lookup_found_topic"
        private const val FOUND_PACKAGE = "lookup_found_package"
    }
}
