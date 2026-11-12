package io.github.ultrafeed.app4pda.feature.auth.ui

import android.R as AndroidR
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.images.AppImageSource
import io.github.ultrafeed.app4pda.core.images.AppImages
import io.github.ultrafeed.app4pda.core.images.ImageOrigin
import io.github.ultrafeed.app4pda.core.images.captchaImageRequest
import io.github.ultrafeed.app4pda.core.network.SourceProblem
import io.github.ultrafeed.app4pda.core.ui.components.AppDialogLayout
import io.github.ultrafeed.app4pda.core.ui.components.AppErrorCard
import io.github.ultrafeed.app4pda.core.ui.components.AppImage
import io.github.ultrafeed.app4pda.core.ui.components.AppLoadingIndicator
import io.github.ultrafeed.app4pda.core.ui.components.SwitchChoiceItem
import io.github.ultrafeed.app4pda.feature.auth.ForumCaptcha
import io.github.ultrafeed.app4pda.feature.auth.data.ForumAuthRejected
import org.koin.compose.koinInject

@Composable
internal fun ForumAuthScreen(
    state: ForumAuthUiState,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onAnswer: (String) -> Unit,
    onHidden: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onClose: () -> Unit,
) {
    AppDialogLayout(
        title = stringResource(R.string.auth),
        actions = {
            TextButton(onClose, Modifier) {
                Text(stringResource(AndroidR.string.cancel))
            }
            if (state.session == null)
                Button(
                    onLogin,
                    enabled = state.canLogin,
                ) {
                    Text(stringResource(R.string.auth_login))
                }
        },
    ) {
        if (state.session != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppImage(
                    state.session.avatarUrl?.let {
                        AppImageSource.Url(
                            it,
                            ImageOrigin.FORUM,
                        )
                    },
                    state.session.avatarUrl,
                    state.session.username,
                    Modifier.size(48.dp),
                )
                Text(
                    state.session.username,
                    Modifier.padding(start = 12.dp),
                )
            }
            OutlinedButton(onClick = onLogout, enabled = !state.busy) {
                Text(stringResource(R.string.auth_logout))
            }
        } else {
            AuthCredentialFields(state, onUsername, onPassword, onHidden)
            AuthCaptchaChallenge(state, onAnswer, onRefresh)
        }
        if (state.busy || state.loadingCaptcha) AppLoadingIndicator()
        state.error?.let {
            if (it is ForumAuthRejected) {
                Text(
                    it.message.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                AppErrorCard(
                    SourceProblem.from(LocalContext.current, "4PDA", it),
                    onRetry = onRefresh.takeUnless { state.busy || state.loadingCaptcha },
                )
            }
        }
    }
}

@Composable
private fun CaptchaImage(captcha: ForumCaptcha) {
    val images: AppImages = koinInject()
    val url = checkNotNull(captcha.imageUrl)
    var failed by remember(url) { mutableStateOf(false) }
    var loading by remember(url) { mutableStateOf(true) }
    var attempt by remember(url) { mutableIntStateOf(0) }
    val context = LocalContext.current
    val request =
        remember(context, url, attempt) {
            captchaImageRequest(context, captcha)
        }
    if (failed)
        TextButton(
            {
                failed = false
                loading = true
                attempt++
            },
        ) {
            Text(stringResource(R.string.forum_auth_retry_image))
        }
    Box(Modifier.fillMaxWidth().height(96.dp)) {
        key(url, attempt) {
            AsyncImage(
                request,
                null,
                imageLoader = images.loader,
                modifier = Modifier.fillMaxSize(),
                onSuccess = {
                    loading = false
                    failed = false
                },
                onError = {
                    loading = false
                    failed = true
                },
            )
        }
        if (loading) AppLoadingIndicator(Modifier.align(Alignment.Center))
    }
}

@Composable
private fun AuthCredentialFields(
    state: ForumAuthUiState,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onHidden: (Boolean) -> Unit,
) {
    OutlinedTextField(
        state.username,
        onUsername,
        Modifier.fillMaxWidth(),
        enabled = !state.busy,
        singleLine = true,
        label = { Text(stringResource(R.string.auth_username)) },
    )
    OutlinedTextField(
        state.password,
        onPassword,
        Modifier.fillMaxWidth(),
        enabled = !state.busy,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        label = { Text(stringResource(R.string.auth_password)) },
    )
    SwitchChoiceItem(
        stringResource(R.string.auth_privacy),
        state.hidden,
        onHidden,
        enabled = !state.busy,
    )
}

@Composable
private fun AuthCaptchaChallenge(
    state: ForumAuthUiState,
    onAnswer: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    state.captcha?.imageUrl?.let { url ->
        key(url, state.captcha) { CaptchaImage(checkNotNull(state.captcha)) }
        OutlinedTextField(
            state.answer,
            onAnswer,
            Modifier.fillMaxWidth(),
            enabled = !state.busy,
            singleLine = true,
            label = { Text(stringResource(R.string.auth_captcha)) },
        )
    }
    FilledTonalButton(
        onRefresh,
        enabled = !state.busy && !state.loadingCaptcha,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.forum_auth_refresh_captcha))
    }
}
