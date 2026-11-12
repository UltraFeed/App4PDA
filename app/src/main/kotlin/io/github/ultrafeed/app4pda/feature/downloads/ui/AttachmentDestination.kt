package io.github.ultrafeed.app4pda.feature.downloads.ui

import android.R as AndroidR
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.app.MainActivity
import io.github.ultrafeed.app4pda.app.navigation.AppDestination
import io.github.ultrafeed.app4pda.app.navigation.AppEntry
import io.github.ultrafeed.app4pda.app.navigation.LocalEntryActive
import io.github.ultrafeed.app4pda.app.navigation.ScreenResult
import io.github.ultrafeed.app4pda.core.ui.components.AppDialogLayout
import io.github.ultrafeed.app4pda.core.ui.components.AppLoadingIndicator
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun AttachmentDestination(
    activity: MainActivity,
    entry: AppEntry,
    onBack: () -> Unit,
    onResult: (ScreenResult) -> Unit,
) {
    val args = entry.destination as AppDestination.Attachment
    val model: AttachmentViewModel = koinViewModel { parametersOf(args.url, args.topic) }
    val state by model.state.collectAsStateWithLifecycle()
    val active = LocalEntryActive.current
    val allowed = !state.preparing && state.directoryReady && state.authenticated
    val description =
        remember(model.file) { DownloadFileFormatter.describe(model.file, activity).toString() }
    LaunchedEffect(state.completed, active) {
        if (state.completed && active) {
            model.consumeResult()
            onResult(ScreenResult.DownloadStarted)
        }
    }
    AppDialogLayout(
        title = activity.getString(if (allowed) R.string.downloads_start else R.string.warning),
        actions = {
            TextButton(onBack) { Text(activity.getString(AndroidR.string.cancel)) }
            if (allowed)
                TextButton(model::start, enabled = !state.submitting) {
                    Text(activity.getString(AndroidR.string.ok))
                }
        },
    ) {
        Text(
            when {
                state.preparing -> ""
                !state.directoryReady -> activity.getString(R.string.downloads_cache_unavailable)
                !allowed -> activity.getString(R.string.warning_download_no_login)
                else -> description
            },
        )
        if (state.preparing || state.submitting) AppLoadingIndicator()
        if (state.failed)
            Text(
                activity.getString(R.string.download_request_failed),
                color = MaterialTheme.colorScheme.error,
            )
    }
}
