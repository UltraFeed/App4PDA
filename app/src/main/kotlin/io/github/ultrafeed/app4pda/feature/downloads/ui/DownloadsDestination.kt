package io.github.ultrafeed.app4pda.feature.downloads.ui

import android.R as AndroidR
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.app.MainActivity
import io.github.ultrafeed.app4pda.app.shell.ScreenFrame
import io.github.ultrafeed.app4pda.core.network.SourceProblem
import io.github.ultrafeed.app4pda.data.googleplay.PlayPackageInstaller
import io.github.ultrafeed.app4pda.feature.downloads.DownloadEntry
import io.github.ultrafeed.app4pda.feature.downloads.DownloadsUiState
import io.github.ultrafeed.app4pda.feature.downloads.InstallRequest
import io.github.ultrafeed.app4pda.feature.downloads.data.ReadyDownloadFile
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.qualifier.named

@Composable
internal fun DownloadsDestination(
    activity: MainActivity,
    onBack: () -> Unit,
) {
    val model: DownloadsViewModel = koinViewModel()
    val installer: PlayPackageInstaller = koinInject()
    val ui by model.state.collectAsStateWithLifecycle()
    var installAfterUninstall by rememberSaveable { mutableStateOf<String?>(null) }
    var uninstallPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var openRequest by remember { mutableStateOf<String?>(null) }

    val exports = rememberDownloadExports(model)
    val uninstall =
        rememberUninstallResult(
            activity,
            { installAfterUninstall.also { installAfterUninstall = null } },
            { uninstallPackage.also { uninstallPackage = null } },
            { openRequest = it },
            { model.refresh() },
        )

    val openFile = rememberPermittedFileOpener(activity, installer) { openRequest = it }
    LaunchedEffect(openRequest) {
        openRequest?.let { path ->
            openRequest = null
            openFile(InstallRequest(path, "", false))
        }
    }
    val launchers =
        DownloadLaunchers(
            { exports.file.launch(it) },
            { exports.directory.launch(null) },
        )
    ObserveDownloads(model)
    DownloadsPage(
        activity,
        ui,
        model,
        onBack,
        { item, action -> performDownloadAction(activity, model, launchers, item, action) },
        openFile,
    ) { request ->
        installAfterUninstall = request.path
        uninstallPackage = request.packageName
        model.consume(DownloadResult.INSTALL)
        activity.downloadAction {
            uninstall.launch(
                Intent(Intent.ACTION_DELETE, Uri.fromParts("package", request.packageName, null)),
            )
        }
    }
}

@Composable
private fun rememberPermittedFileOpener(
    activity: MainActivity,
    installer: PlayPackageInstaller,
    onOpen: (String) -> Unit,
): (InstallRequest) -> Unit {
    var installAfterPermission by rememberSaveable { mutableStateOf<String?>(null) }
    val permission =
        rememberInstallPermission(
            activity,
            { installAfterPermission.also { installAfterPermission = null } },
        ) {
            onOpen(it)
        }

    return rememberReadyFileOpener(activity, installer) { path ->
        installAfterPermission = path
        permission.launch(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                ("package:" + activity.packageName).toUri(),
            ),
        )
    }
}

@Composable
private fun rememberUninstallResult(
    activity: MainActivity,
    takePath: () -> String?,
    takePackage: () -> String?,
    onOpen: (String) -> Unit,
    onRefresh: () -> Unit,
) =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val path = takePath()
        val pack = takePackage()
        if (
            path != null &&
                pack != null &&
                runCatching {
                    activity.packageManager.getPackageInfo(
                        pack,
                        0,
                    )
                }
                    .isFailure
        )
            onOpen(path)
        onRefresh()
    }

private class DownloadLaunchers(
    val document: (String) -> Unit,
    val directory: () -> Unit,
)

@Composable
private fun rememberReadyFileOpener(
    activity: MainActivity,
    installer: PlayPackageInstaller,
    onPermissionRequired: (String) -> Unit,
): (InstallRequest) -> Unit {
    val scope = rememberCoroutineScope()
    val io: CoroutineDispatcher = koinInject(named("io"))
    return { request ->
        activity.downloadAction {
            val ready = ReadyDownloadFile(activity, request.path, request.installable)
            if (
                ready.installable &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    !activity.packageManager.canRequestPackageInstalls()
            )
                onPermissionRequired(
                    request.path,
                )
            else if (ready.split)
                scope.launch {
                    activity.downloadAction {
                        withContext(io) {
                            installer.install(activity.applicationContext, ready.file)
                        }
                    }
                }
            else
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(ready.uri(activity), ready.mimeType)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
        }
    }
}

private fun performDownloadAction(
    activity: MainActivity,
    model: DownloadsViewModel,
    launchers: DownloadLaunchers,
    item: DownloadEntry,
    action: DownloadAction,
) {
    activity.downloadAction {
        when (action) {
            DownloadAction.SAVE ->
                if (model.beginExport(item)) {
                    var launched = false
                    try {
                        if (item.group) launchers.directory() else launchers.document(item.fileName)
                        launched = true
                    } finally {
                        if (!launched) model.exportTo(null)
                    }
                }

            DownloadAction.OPEN -> model.install(item)
            DownloadAction.SHARE ->
                activity.downloadAction {
                    val ready =
                        ReadyDownloadFile(
                            activity,
                            item.path,
                            item.group || item.packageName.isNotBlank(),
                        )
                    val uri = ready.uri(activity)
                    activity.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND)
                                .setType(ready.mimeType)
                                .putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            activity.getString(R.string.action_share_file),
                        ),
                    )
                }
        }
    }
}

private inline fun MainActivity.downloadAction(action: () -> Unit) {
    try {
        action()
    } catch (failure: IOException) {
        downloadFailure(failure)
    } catch (failure: ActivityNotFoundException) {
        downloadFailure(failure)
    } catch (failure: SecurityException) {
        downloadFailure(failure)
    } catch (failure: IllegalArgumentException) {
        downloadFailure(failure)
    } catch (failure: IllegalStateException) {
        downloadFailure(failure)
    }
}

private fun MainActivity.downloadFailure(failure: Throwable) {
    Log.w("Downloads", "External file action failed", failure)
    Toast.makeText(
            this,
            SourceProblem.from(this, "Downloads", failure).description,
            Toast.LENGTH_LONG,
        )
        .show()
}

@Composable
private fun DownloadsPage(
    activity: MainActivity,
    ui: DownloadsUiState,
    model: DownloadsViewModel,
    onBack: () -> Unit,
    onAction: (DownloadEntry, DownloadAction) -> Unit,
    onOpen: (InstallRequest) -> Unit,
    onUninstall: (InstallRequest) -> Unit,
) {
    ScreenFrame(onBack, null) {
        DownloadsScreen(
            ui,
            { model.refresh(retryFailed = true) },
            model::delete,
            model::rename,
            onAction,
            { model.consume(DownloadResult.ERROR) },
            model::transfer,
        )
        DownloadsDialogs(activity, ui, model, onOpen, onUninstall)
    }
}

@Composable
private fun DownloadsDialogs(
    activity: MainActivity,
    ui: DownloadsUiState,
    model: DownloadsViewModel,
    onOpen: (InstallRequest) -> Unit,
    onUninstall: (InstallRequest) -> Unit,
) {
    if (ui.exportComplete)
        AlertDialog(
            onDismissRequest = { model.consume(DownloadResult.EXPORT) },
            text = { Text(activity.getString(R.string.downloads_saved_copy)) },
            confirmButton = {
                TextButton({ model.consume(DownloadResult.EXPORT) }) {
                    Text(activity.getString(AndroidR.string.ok))
                }
            },
        )
    ui.pendingInstall?.let { request ->
        if (request.requiresUninstall)
            AlertDialog(
                onDismissRequest = { model.consume(DownloadResult.INSTALL) },
                title = { Text(activity.getString(R.string.warning)) },
                text = { Text(activity.getString(R.string.downloads_signature_no_match)) },
                confirmButton = {
                    TextButton({ onUninstall(request) }) {
                        Text(activity.getString(AndroidR.string.ok))
                    }
                },
                dismissButton = {
                    TextButton({ model.consume(DownloadResult.INSTALL) }) {
                        Text(activity.getString(AndroidR.string.cancel))
                    }
                },
            )
        else
            LaunchedEffect(request) {
                model.consume(DownloadResult.INSTALL)
                onOpen(request)
            }
    }
}
