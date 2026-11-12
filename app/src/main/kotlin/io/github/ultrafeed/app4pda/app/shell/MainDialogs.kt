package io.github.ultrafeed.app4pda.app.shell

import android.R as AndroidR
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.ui.components.ConfirmActionDialog
import io.github.ultrafeed.app4pda.data.forum.ForumUrls

@Composable
fun HtmlDialogText(html: String) {
    SelectionContainer {
        Text(
            AnnotatedString.fromHtml(ForumUrls.normalizeHtml(html)),
            Modifier.verticalScroll(rememberScrollState()),
        )
    }
}

@Composable
fun MainDialogs(
    state: MainDialogsState,
    onDownload: (Boolean) -> Unit,
    onOpenDownloads: (Boolean?) -> Unit,
    onWebView: (Boolean) -> Unit,
    onPermissionsDismiss: () -> Unit,
) {
    when {
        state.webViewRequired -> WebViewRequiredDialog(onWebView)

        state.downloadUrl != null ->
            ConfirmActionDialog(
                title = stringResource(R.string.downloads_start),
                text = state.downloadUrl,
                onConfirm = { onDownload(true) },
                onDismiss = { onDownload(false) },
            )

        state.openDownloads ->
            AlertDialog(
                onDismissRequest = { onOpenDownloads(null) },
                title = { Text(stringResource(R.string.open_downloads)) },
                confirmButton = {
                    TextButton(onClick = { onOpenDownloads(true) }) {
                        Text(stringResource(AndroidR.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onOpenDownloads(false) }) {
                        Text(stringResource(AndroidR.string.cancel))
                    }
                },
            )

        state.permissionsDenied ->
            AlertDialog(
                onDismissRequest = onPermissionsDismiss,
                title = { Text(stringResource(R.string.warning)) },
                text = { HtmlDialogText(stringResource(R.string.permissions_denied_message)) },
                confirmButton = {
                    TextButton(onClick = onPermissionsDismiss) {
                        Text(stringResource(R.string.action_close))
                    }
                },
            )
    }
}

@Composable
private fun WebViewRequiredDialog(onChoice: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onChoice(false) },
        title = { Text(stringResource(R.string.warning)) },
        text = { Text(stringResource(R.string.warning_webview_not_installed)) },
        confirmButton = {
            TextButton(onClick = { onChoice(true) }) {
                Text(stringResource(R.string.action_install))
            }
        },
        dismissButton = {
            TextButton(onClick = { onChoice(false) }) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}
