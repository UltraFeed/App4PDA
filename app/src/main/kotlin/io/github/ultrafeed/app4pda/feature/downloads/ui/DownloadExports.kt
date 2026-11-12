package io.github.ultrafeed.app4pda.feature.downloads.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.ultrafeed.app4pda.app.MainActivity
import io.github.ultrafeed.app4pda.app.navigation.LocalEntryActive
import io.github.ultrafeed.app4pda.app.navigation.OnScreenResume
import io.github.ultrafeed.app4pda.feature.downloads.data.DownloadsRepository
import org.koin.compose.koinInject

/** Native launchers for selecting an export file or split-group directory. */
internal class DownloadExports(
    val file: ActivityResultLauncher<String>,
    val directory: ActivityResultLauncher<Uri?>,
)

@Composable
internal fun rememberDownloadExports(model: DownloadsViewModel): DownloadExports {
    val file =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream"),
            model::exportTo,
        )
    val directory =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
            model::exportTo,
        )
    return DownloadExports(file, directory)
}

@Composable
internal fun rememberInstallPermission(
    activity: MainActivity,
    takePending: () -> String?,
    onOpen: (String) -> Unit,
): ActivityResultLauncher<Intent> =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val path = takePending()
        if (
            path != null &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    activity.packageManager.canRequestPackageInstalls())
        )
            onOpen(
                path,
            )
    }

@Composable
internal fun ObserveDownloads(model: DownloadsViewModel) {
    OnScreenResume { model.refresh() }
    val repository: DownloadsRepository = koinInject()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val active = LocalEntryActive.current
    LaunchedEffect(repository, lifecycle, active) {
        if (active)
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeFiles()
            }
    }
}
