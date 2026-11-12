package io.github.ultrafeed.app4pda.feature.downloads.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ultrafeed.app4pda.data.forum.ForumFileInfo
import io.github.ultrafeed.app4pda.data.preferences.AppSettings
import io.github.ultrafeed.app4pda.feature.downloads.data.DownloadController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class AttachmentState(
    val preparing: Boolean = true,
    val directoryReady: Boolean = false,
    val authenticated: Boolean = false,
    val submitting: Boolean = false,
    val failed: Boolean = false,
    val completed: Boolean = false,
) {
    val ready: Boolean
        get() = !preparing && directoryReady && authenticated

    val consumed: Boolean
        get() = submitting || completed
}

internal class AttachmentViewModel(
    private val downloads: DownloadController,
    private val settings: AppSettings,
    url: String,
    topic: Int,
) : ViewModel() {
    val file = ForumFileInfo(url, topic)
    private val mutableState = MutableStateFlow(AttachmentState())
    val state = mutableState.asStateFlow()
    private var accepted = false

    init {
        viewModelScope.launch {
            try {
                val values = settings.awaitReady()
                val ready = downloads.prepareDirectory()
                currentCoroutineContext().ensureActive()
                mutableState.value =
                    AttachmentState(
                        preparing = false,
                        directoryReady = ready,
                        authenticated = values.session.authenticated,
                    )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.value = AttachmentState(preparing = false, failed = true)
            }
        }
    }

    fun start() {
        val current = state.value
        if (accepted || !current.ready || current.consumed) return
        mutableState.value = current.copy(submitting = true, failed = false)
        viewModelScope.launch {
            try {
                check(settings.awaitReady().session.authenticated)
                downloads.submissions.submitUrl(
                    file.downloadUrl,
                    file.fileName,
                    file.topicId,
                    file.postId,
                )
                accepted = true
                currentCoroutineContext().ensureActive()
                mutableState.value = state.value.copy(submitting = false, completed = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.value = state.value.copy(submitting = false, failed = true)
            }
        }
    }

    fun consumeResult() {
        mutableState.value = state.value.copy(completed = false)
    }
}
