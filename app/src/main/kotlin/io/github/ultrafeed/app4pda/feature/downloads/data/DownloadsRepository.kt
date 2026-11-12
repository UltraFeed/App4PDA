package io.github.ultrafeed.app4pda.feature.downloads.data

import android.app.Application
import android.content.Context
import android.database.SQLException
import android.net.Uri
import android.provider.DocumentsContract
import android.util.AtomicFile
import android.util.Log
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.AppPolicy
import io.github.ultrafeed.app4pda.core.network.SourceProblem
import io.github.ultrafeed.app4pda.core.packages.PackageInspector
import io.github.ultrafeed.app4pda.data.googleplay.PlayDownloadGroup
import io.github.ultrafeed.app4pda.data.preferences.AppSettings
import io.github.ultrafeed.app4pda.feature.downloads.ActiveDownload
import io.github.ultrafeed.app4pda.feature.downloads.DownloadEntry
import io.github.ultrafeed.app4pda.feature.downloads.DownloadsSnapshot
import io.github.ultrafeed.app4pda.feature.downloads.InstallRequest
import io.github.ultrafeed.app4pda.feature.downloads.TransferCommand
import io.github.ultrafeed.app4pda.feature.downloads.TransferState
import io.github.ultrafeed.app4pda.feature.downloads.TransferTarget
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DownloadsRepository
internal constructor(
    context: Context,
    private val storage: DownloadsStorage,
    private val settings: AppSettings,
    private val controller: DownloadController,
    private val packages: PackageInspector,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val context = context.applicationContext as Application
    private val refreshLock = Mutex()
    private val listing = ReadyDownloads()
    private val mutableState = MutableStateFlow(DownloadsSnapshot(loading = true))
    val state = mutableState.asStateFlow()

    suspend fun refresh() =
        withContext(ioDispatcher) {
            refreshLock.withLock {
                try {
                    check(storage.ensureDirectory()) {
                        context.getString(
                            R.string.downloads_cache_unavailable,
                        )
                    }
                    controller.records.reconcile()
                    val partialPaths = controller.records.unfinishedPaths()
                    val entries =
                        listing
                            .scan(
                                context,
                                storage,
                                packages,
                                partialPaths,
                                controller.records.completedRecords(),
                            )
                            .sortedWith(compareBy<DownloadEntry> { it.createdAt }.thenBy { it.id })
                    currentCoroutineContext().ensureActive()
                    val transfers = controller.active()
                    val failure = transferFailure(transfers)
                    mutableState.update {
                        it.copy(
                            transfers = transfers,
                            entries = entries,
                            loading = false,
                            error =
                                failure?.let { problem ->
                                    "${problem.code}\n${problem.description}"
                                },
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: IOException) {
                    scanFailed(error)
                } catch (error: IllegalStateException) {
                    scanFailed(error)
                } catch (error: SecurityException) {
                    scanFailed(error)
                } catch (error: SQLException) {
                    scanFailed(error)
                } finally {
                    mutableState.update { it.copy(loading = false) }
                }
            }
        }

    private fun scanFailed(failure: Throwable) {
        Log.w("Downloads", "File scan failed", failure)
        val problem = SourceProblem.from(context, "Downloads", failure)
        mutableState.update { it.copy(error = "${problem.code}\n${problem.description}") }
    }

    private suspend fun transferFailure(
        transfers: List<ActiveDownload>,
    ) =
        if (
            transfers.any {
                it.state == TransferState.FAILED
            }
        )
            controller.records.finalFailure()
                ?: SourceProblem(
                    "Downloads · TRANSFER",
                    context.getString(R.string.transfer_failed),
                )
        else null

    @OptIn(FlowPreview::class)
    suspend fun retry() {
        controller.retryFailed()
        refresh()
    }

    suspend fun transfer(
        target: TransferTarget,
        command: TransferCommand,
    ) {
        controller.command(target, command)
        refresh()
    }

    @OptIn(FlowPreview::class)
    suspend fun observeFiles() {
        try {
            coroutineScope {
                launch {
                    controller.events.collect {
                        val transfers = controller.active()
                        mutableState.update { previous -> previous.copy(transfers = transfers) }
                    }
                }
                merge(
                        storage.changes(ioDispatcher),
                        controller.events
                            .map { tasks -> tasks.map { it.id to it.status } }
                            .distinctUntilChanged()
                            .map { Unit },
                        controller.groupEvents.map { Unit },
                    )
                    .debounce(
                        AppPolicy.FILE_EVENT_DEBOUNCE_MILLIS.milliseconds,
                    )
                    .collect { refresh() }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: IOException) {
            scanFailed(failure)
        } catch (failure: IllegalStateException) {
            scanFailed(failure)
        } catch (failure: SecurityException) {
            scanFailed(failure)
        } catch (failure: SQLException) {
            scanFailed(failure)
        }
    }

    suspend fun export(
        entry: DownloadEntry,
        destination: Uri,
        progress: (Float) -> Unit,
    ) =
        withContext(ioDispatcher) {
            var completed = false
            try {
                val export = DownloadExport(context.contentResolver)
                if (entry.group) {
                    val directory = File(entry.path)
                    storage.requireDestination(directory)
                    val group = checkNotNull(PlayDownloadGroup.read(directory))
                    group.withInstallApks(context.cacheDir) { apks ->
                        export.copy(apks, destination, true, progress)
                    }
                } else {
                    export.copy(listOf(verifiedFile(entry.path)), destination, false, progress)
                }
                completed = true
            } finally {
                if (!completed && !entry.group)
                    runCatching {
                        DocumentsContract.deleteDocument(
                            context.contentResolver,
                            destination,
                        )
                    }
            }
        }

    suspend fun delete(entry: DownloadEntry) =
        withContext(ioDispatcher) {
            run {
                val path = entry.path
                if (entry.group) {
                    val group = checkNotNull(PlayDownloadGroup.read(File(path)))
                    group.files.forEach { file ->
                        val output = group.file(file)
                        controller.records.forget(output.path)
                        check(!output.exists() || output.delete())
                    }
                    check(File(group.directory, PlayDownloadGroup.MANIFEST).delete())
                    AtomicFile(File(group.directory, DELIVERY_CONTROL)).delete()
                    check(group.directory.delete())
                    return@run
                }
                controller.records.forget(path)
                storage.deleteFile(path)
            }
            refresh()
        }

    suspend fun rename(entry: DownloadEntry, name: String) =
        withContext(ioDispatcher) {
            require(
                !entry.group &&
                    name.isNotBlank() &&
                    name != "." &&
                    name != ".." &&
                    name.none { it == '/' || it == '\\' || it == '\u0000' },
            )
            val source = File(entry.path)
            val target = File(source.parentFile, name)
            check(!target.exists() && source.renameTo(target)) { "Cannot rename ${entry.fileName}" }
            controller.records.renamed(entry.path, target.path)
            refresh()
        }

    suspend fun prepareInstall(entry: DownloadEntry): InstallRequest =
        withContext(ioDispatcher) {
            val values = settings.awaitReady()
            val file = File(entry.path)
            if (entry.group) {
                val group = checkNotNull(PlayDownloadGroup.read(file))
                val base =
                    group.withInstallApks(context.cacheDir) { apks ->
                        apks.firstNotNullOfOrNull {
                            packages.artifacts.readApk(
                                it.path,
                                packages.artifacts.signatureQueryFlags,
                            )
                        }
                    }
                        ?: error(
                            context.getString(R.string.error_wrong_apk),
                        )
                val mismatch =
                    packages.isInstalled(base.packageName) &&
                        !values.getBoolean(
                            "no_calc_signatyre_app_befor_install",
                        ) &&
                        !packages.artifacts.hasSameInstalledSignatures(
                            base.packageName,
                            packages.artifacts.getSignatures(base),
                        )
                return@withContext InstallRequest(file.path, entry.packageName, mismatch)
            }
            verifiedFile(entry.path)
            if (!file.extension.equals("apk", true))
                return@withContext InstallRequest(entry.path, "", false, installable = false)
            val info =
                packages.artifacts.readApk(entry.path, packages.artifacts.signatureQueryFlags)
                    ?: return@withContext InstallRequest(entry.path, "", false, installable = false)
            val installed = packages.isInstalled(info.packageName)
            val checkSignature = !values.getBoolean("no_calc_signatyre_app_befor_install")
            val mismatch =
                installed &&
                    checkSignature &&
                    !packages.artifacts.hasSameInstalledSignatures(
                        info.packageName,
                        packages.artifacts.getSignatures(info),
                    )
            InstallRequest(entry.path, info.packageName, mismatch)
        }

    private suspend fun verifiedFile(path: String): File =
        storage.requireFile(path).also { file ->
            controller.records.completedRecords()[file.path]?.verify(file)
        }
}
