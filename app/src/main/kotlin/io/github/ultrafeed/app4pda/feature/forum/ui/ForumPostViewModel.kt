package io.github.ultrafeed.app4pda.feature.forum.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ultrafeed.app4pda.data.forum.ForumPost
import io.github.ultrafeed.app4pda.data.forum.ForumPostRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ForumPostUiState(
    val loading: Boolean = true,
    val post: ForumPost? = null,
    val error: Throwable? = null,
)

class ForumPostViewModel(
    private val repository: ForumPostRepository,
    private val topicId: Int,
    private val postId: Int,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ForumPostUiState())
    val state = mutableState.asStateFlow()
    private var request: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        request?.cancel()
        mutableState.value = state.value.copy(loading = true, error = null)
        request = viewModelScope.launch {
            try {
                val requestContext = currentCoroutineContext()
                val post =
                    repository.loadPost(topicId, postId) { cached ->
                        withContext(Dispatchers.Main.immediate) {
                            requestContext.ensureActive()
                            mutableState.value = state.value.copy(post = cached, loading = false)
                        }
                    }
                currentCoroutineContext().ensureActive()
                mutableState.value = ForumPostUiState(loading = false, post = post)
            } catch (failure: IOException) {
                failed(failure)
            } catch (failure: IllegalArgumentException) {
                failed(failure)
            } catch (failure: IllegalStateException) {
                failed(failure)
            }
        }
    }

    private suspend fun failed(failure: Exception) {
        currentCoroutineContext().ensureActive()
        mutableState.value = state.value.copy(loading = false, error = failure)
    }
}
