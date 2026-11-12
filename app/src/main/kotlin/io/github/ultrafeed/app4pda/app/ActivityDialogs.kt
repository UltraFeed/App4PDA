package io.github.ultrafeed.app4pda.app

import android.webkit.CookieManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.app.navigation.AppDestination
import io.github.ultrafeed.app4pda.app.navigation.AppEntry
import io.github.ultrafeed.app4pda.app.shell.MainDialogs
import io.github.ultrafeed.app4pda.app.shell.MainDialogsState
import io.github.ultrafeed.app4pda.data.preferences.AppSettings
import io.github.ultrafeed.app4pda.feature.downloads.data.DownloadController
import io.github.ultrafeed.app4pda.feature.downloads.data.DownloadLinks
import io.github.ultrafeed.app4pda.feature.lookup.data.AppLinks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Activity-owned confirmations; their state is persisted with the platform saved instance. */
internal class ActivityDialogs(
    private val activity: MainActivity,
    private val settings: AppSettings,
    private val downloads: DownloadController,
) {
    var state by mutableStateOf(MainDialogsState())

    @Composable
    internal fun Render() {
        MainDialogs(
            state,
            ::resolveDownloadConfirmation,
            ::resolveOpenDownloads,
            ::resolveWebViewWarning,
        ) {
            state = state.copy(permissionsDenied = false)
        }
    }

    private fun resolveDownloadConfirmation(accepted: Boolean) {
        val url = state.downloadUrl ?: return
        state = state.copy(downloadUrl = null)
        if (accepted) {
            downloads.submissions.submitUrl(
                url,
                DownloadLinks.decodeWindows1251(url.substring(url.lastIndexOf('/'))),
            )
            return
        }
        finishDownloadEntry()
    }

    internal fun finishDownloadEntry() {
        if (
            (activity.navigation.stack?.lastOrNull() as? AppEntry)?.destination ==
                AppDestination.Empty &&
                !state.openDownloads &&
                !activity.navigation.downloadsRequested
        )
            activity.finish()
    }

    internal fun downloadQueued() {
        when (settings.current.downloadsOpenMode) {
            "open" -> openDownloads()
            "ask" -> requestOpenDownloadsConfirmation()
        }
    }

    private fun requestOpenDownloadsConfirmation() {
        activity.runOnUiThread {
            state = state.copy(openDownloads = true)
        }
    }

    private fun openDownloads() {
        activity.runOnUiThread { activity.navigation.downloadsRequested = true }
    }

    private fun resolveOpenDownloads(accepted: Boolean?) {
        if (!state.openDownloads) return
        state = state.copy(openDownloads = false)
        activity.lifecycleScope.launch {
            try {
                if (accepted == true) openDownloads()
                else if (
                    (activity.navigation.stack?.lastOrNull() as? AppEntry)?.destination ==
                        AppDestination.Empty
                )
                    activity.finish()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toast.makeText(
                        activity,
                        R.string.action_failed,
                        Toast.LENGTH_LONG,
                    )
                    .show()
            }
        }
    }

    internal fun checkWebViewAvailable() {
        val available =
            try {
                CookieManager.getInstance()
                true
            } catch (_: Exception) {
                false
            }
        if (!available || !activity.packageManager.hasSystemFeature("android.software.webview"))
            state = state.copy(webViewRequired = true)
    }

    private fun resolveWebViewWarning(install: Boolean) {
        state = state.copy(webViewRequired = false)
        if (install)
            runCatching {
                activity.startActivity(
                    AppLinks.playStoreIntent(
                        "com.google.android.webview",
                    ),
                )
            }
        activity.finish()
    }
}
