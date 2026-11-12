package io.github.ultrafeed.app4pda.feature.auth.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ultrafeed.app4pda.feature.auth.ForumAuthChange
import io.github.ultrafeed.app4pda.feature.auth.ForumCaptcha
import io.github.ultrafeed.app4pda.feature.auth.ForumSession
import io.github.ultrafeed.app4pda.feature.auth.data.ForumAuthRepository
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ForumAuthUiState(
    val username: String = "",
    val password: String = "",
    val hidden: Boolean = false,
    val answer: String = "",
    val captcha: ForumCaptcha? = null,
    val session: ForumSession? = null,
    val loadingCaptcha: Boolean = false,
    val busy: Boolean = false,
    val error: Throwable? = null,
    val change: ForumAuthChange? = null,
) {
    val canLogin: Boolean
        get() =
            session == null &&
                !busy &&
                !loadingCaptcha &&
                username.isNotBlank() &&
                password.isNotEmpty() &&
                captcha != null &&
                (captcha.imageUrl == null || answer.isNotBlank())

    override fun toString(): String = "ForumAuthUiState(busy=$busy, loadingCaptcha=$loadingCaptcha)"
}

class ForumAuthViewModel(
    private val repository: ForumAuthRepository,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(
            ForumAuthUiState(
                username = repository.loginName,
                session = repository.session,
            ),
        )
    val state: StateFlow<ForumAuthUiState> = mutableState.asStateFlow()
    private var captchaJob: Job? = null
    private var operation: Job? = null
    private var addingAccount = false

    fun addAccount() {
        if (addingAccount) return
        addingAccount = true
        mutableState.value = ForumAuthUiState()
        refreshCaptcha()
    }

    init {
        if (state.value.session == null) refreshCaptcha()
    }

    fun username(
        value: String,
    ) {
        if (!state.value.busy)
            mutableState.value =
                state.value.copy(
                    username = value,
                )
    }

    fun password(
        value: String,
    ) {
        if (!state.value.busy)
            mutableState.value =
                state.value.copy(
                    password = value,
                )
    }

    fun answer(
        value: String,
    ) {
        if (!state.value.busy)
            mutableState.value =
                state.value.copy(
                    answer = value,
                )
    }

    fun hidden(
        value: Boolean,
    ) {
        if (!state.value.busy)
            mutableState.value =
                state.value.copy(
                    hidden = value,
                )
    }

    fun refreshCaptcha() {
        if (state.value.busy || state.value.session != null) return
        captchaJob?.cancel()
        mutableState.value =
            state.value.copy(
                loadingCaptcha = true,
                captcha = null,
                answer = "",
                error = null,
            )
        captchaJob = viewModelScope.launch {
            try {
                val captcha = repository.loadCaptcha()
                coroutineContext.ensureActive()
                mutableState.value =
                    state.value.copy(
                        captcha = captcha,
                        loadingCaptcha = false,
                    )
            } catch (error: IOException) {
                coroutineContext.ensureActive()
                mutableState.value =
                    state.value.copy(
                        loadingCaptcha = false,
                        error = authFailure(error),
                    )
            } catch (error: IllegalStateException) {
                coroutineContext.ensureActive()
                mutableState.value =
                    state.value.copy(
                        loadingCaptcha = false,
                        error = authFailure(error),
                    )
            }
        }
    }

    fun login() {
        val input = state.value
        if (!input.canLogin) return
        val captcha = input.captcha ?: return
        perform(
            ForumAuthChange.SIGNED_IN,
        ) {
            val cookies =
                repository.authenticate(
                    input.username,
                    input.password,
                    input.hidden,
                    input.answer,
                    captcha,
                )
            currentCoroutineContext().ensureActive()
            repository.commit(
                cookies,
                input.username,
            )
        }
    }

    private fun perform(
        change: ForumAuthChange,
        action: suspend () -> Unit,
    ) {
        captchaJob?.cancel()
        mutableState.value =
            state.value.copy(
                busy = true,
                loadingCaptcha = false,
                error = null,
            )
        operation = viewModelScope.launch {
            try {
                action()
                coroutineContext.ensureActive()
                mutableState.value =
                    state.value.copy(
                        busy = false,
                        password = "",
                        answer = "",
                        captcha = null,
                        session = repository.session,
                        change = change,
                    )
            } catch (error: IOException) {
                operationFailed(error)
            } catch (error: IllegalStateException) {
                operationFailed(error)
            } catch (error: IllegalArgumentException) {
                operationFailed(error)
            }
        }
    }

    private suspend fun operationFailed(error: Exception) {
        currentCoroutineContext().ensureActive()
        // A failed login invalidates its captcha. The user explicitly requests a fresh one.
        mutableState.value =
            state.value.copy(
                busy = false,
                error = authFailure(error),
                captcha = null,
                answer = "",
            )
    }

    fun logout() {
        if (state.value.busy) return
        perform(
            ForumAuthChange.SIGNED_OUT,
        ) {
            repository.logout()
        }
    }

    fun cancelRequests() {
        captchaJob?.cancel()
        operation?.cancel()
        mutableState.value =
            state.value.copy(
                busy = false,
                loadingCaptcha = false,
                password = "",
                answer = "",
                captcha = null,
            )
    }
}

internal fun authFailure(failure: Throwable): Throwable {
    Log.w("ForumAuth", "Authentication operation failed", failure)
    return failure
}
