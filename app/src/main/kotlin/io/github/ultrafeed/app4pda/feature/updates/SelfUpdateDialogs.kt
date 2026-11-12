package io.github.ultrafeed.app4pda.feature.updates

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.app.MainActivity
import io.github.ultrafeed.app4pda.feature.downloads.data.ReadyDownloadFile

@Composable
internal fun SelfUpdateDialogs(activity: MainActivity, update: SelfUpdate) {
    val state by update.state.collectAsStateWithLifecycle()
    var waitingForPermission by rememberSaveable { mutableStateOf(false) }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (!activity.canInstallUpdate())
                (update.state.value as? UpdateState.Ready)?.let(update::finishInstall)
            waitingForPermission = false
        }
    LaunchedEffect(update) {
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            update.check(manual = false)
            update.state.collect { current ->
                if (current is UpdateState.Message) {
                    Toast.makeText(activity, current.text, Toast.LENGTH_LONG).show()
                    update.dismiss()
                }
            }
        }
    }
    LaunchedEffect(state, waitingForPermission) {
        val ready = state as? UpdateState.Ready ?: return@LaunchedEffect
        if (waitingForPermission) return@LaunchedEffect
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                if (activity.canInstallUpdate()) {
                    if (update.verifyReady(ready)) activity.openUpdate(ready.path)
                    else
                        Toast.makeText(activity, R.string.self_update_failed, Toast.LENGTH_LONG)
                            .show()
                    update.finishInstall(ready)
                } else {
                    waitingForPermission = true
                    permission.launch(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            ("package:" + activity.packageName).toUri(),
                        ),
                    )
                }
            } catch (failure: ActivityNotFoundException) {
                waitingForPermission = false
                Log.w(
                    "SelfUpdate",
                    "Unable to open Android installer or permission screen",
                    failure,
                )
                update.finishInstall(ready)
                Toast.makeText(activity, R.string.self_update_failed, Toast.LENGTH_LONG).show()
            } catch (failure: SecurityException) {
                waitingForPermission = false
                Log.w("SelfUpdate", "Android rejected the installer or permission screen", failure)
                update.finishInstall(ready)
                Toast.makeText(activity, R.string.self_update_failed, Toast.LENGTH_LONG).show()
            }
        }
    }
    val offer = state as? UpdateState.Offer ?: return
    OfferUpdate(offer.release, { update.download(offer.release) }, update::dismiss)
}

@Composable
private fun OfferUpdate(release: GitHubRelease, download: () -> Unit, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.self_update_check)) },
        text = { Text(stringResource(R.string.self_update_available, release.version)) },
        confirmButton = {
            TextButton(onClick = download) {
                Text(stringResource(R.string.action_download))
            }
        },
        dismissButton = {
            TextButton(onClick = dismiss) {
                Text(stringResource(R.string.self_update_later))
            }
        },
    )
}

private fun MainActivity.canInstallUpdate(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()

private fun MainActivity.openUpdate(path: String) {
    val file = ReadyDownloadFile(this, path, installable = true)
    startActivity(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(file.uri(this), file.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
    )
}
