package io.github.ultrafeed.app4pda.feature.updates

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import com.ketch.Status
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.AppPolicy
import io.github.ultrafeed.app4pda.core.files.verifyFile
import io.github.ultrafeed.app4pda.core.packages.PackageInspector
import io.github.ultrafeed.app4pda.data.preferences.AppSettings
import io.github.ultrafeed.app4pda.feature.downloads.data.DownloadController
import java.io.Closeable
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

internal class SelfUpdate(
    private val app: Application,
    private val settings: AppSettings,
    private val downloads: DownloadController,
    private val packages: PackageInspector,
    client: OkHttpClient,
    private val io: CoroutineDispatcher,
) : Closeable {
    private val releases = GitHubReleases(client)
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state = mutableState.asStateFlow()
    val enabled = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0
    private var work: Job? = null

    @Synchronized
    fun check(manual: Boolean) {
        if (!enabled || mutableState.value is UpdateState.Ready || work?.isActive == true) return
        start(manual) {
            val values = settings.awaitReady().values
            val pending = values[PENDING] as? String
            if (pending != null) {
                restoreDownload(Json.decodeFromString(pending))
            } else {
                val now = System.currentTimeMillis()
                val last = values[LAST_CHECK] as? Long ?: 0
                if (manual || now - last !in 0 until AppPolicy.SELF_UPDATE_INTERVAL_MILLIS) {
                    settings.writeValue(LAST_CHECK, now)
                    val release =
                        releases.latest()?.takeIf {
                            newerVersion(it.version, app.installed().versionName)
                        }
                    mutableState.value =
                        if (release != null) UpdateState.Offer(release)
                        else if (manual) UpdateState.Message(R.string.self_update_current)
                        else UpdateState.Idle
                }
            }
        }
    }

    @Synchronized
    fun download(release: GitHubRelease) {
        if (mutableState.value != UpdateState.Offer(release)) return
        mutableState.value = UpdateState.Idle
        start(reportFailure = true) {
            val checksum = releases.checksum(release)
            val file = downloads.storage.uniqueDestination(release.apkName)
            val id = downloads.enqueue(release.asset(release.apkName), file, checksum = checksum)
            val pending = PendingUpdate(release.version, file.path, checksum, id)
            settings.writeValue(PENDING, Json.encodeToString(pending))
            restoreDownload(pending)
        }
    }

    fun dismiss() {
        mutableState.value = UpdateState.Idle
    }

    fun finishInstall(ready: UpdateState.Ready) {
        if (mutableState.value != ready) return
        start(reportFailure = true) {
            settings.writeValue(PENDING, null)
            mutableState.compareAndSet(ready, UpdateState.Idle)
        }
    }

    suspend fun verifyReady(ready: UpdateState.Ready): Boolean =
        withContext(io) {
            try {
                verifyDownloadedApk(ready)
                true
            } catch (failure: IOException) {
                report(failure, false)
                false
            } catch (failure: IllegalStateException) {
                if (failure is CancellationException) throw failure
                report(failure, false)
                false
            }
        }

    private fun verifyDownloadedApk(ready: UpdateState.Ready) {
        val file = downloads.storage.requireFile(ready.path)
        verifyFile(file, 0, ready.checksum)
        verifyApk(file.path, ready.version)
    }

    private fun start(reportFailure: Boolean, operation: suspend () -> Unit) {
        work = scope.launch {
            try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IOException) {
                report(failure, reportFailure)
            } catch (failure: IllegalArgumentException) {
                report(failure, reportFailure)
            } catch (failure: IllegalStateException) {
                report(failure, reportFailure)
            }
        }
    }

    private fun report(failure: Exception, visible: Boolean) {
        Log.w("SelfUpdate", "GitHub update failed", failure)
        if (visible) mutableState.value = UpdateState.Message(R.string.self_update_failed)
    }

    private suspend fun restoreDownload(pending: PendingUpdate) {
        try {
            val task =
                downloads.events
                    .first { tasks ->
                        tasks.none {
                            it.id.toLong() == pending.id && it.status !in terminalStatuses
                        }
                    }
                    .firstOrNull { it.id.toLong() == pending.id }
            val result = UpdateState.Ready(pending.path, pending.version, pending.checksum)
            if (task?.status == Status.SUCCESS && verifyReady(result)) {
                mutableState.value = result
            } else {
                settings.writeValue(PENDING, null)
                if (task?.status != Status.CANCELLED)
                    mutableState.value = UpdateState.Message(R.string.self_update_failed)
            }
        } catch (failure: IOException) {
            report(failure, true)
        }
    }

    private fun verifyApk(path: String, version: String) {
        val artifacts = packages.artifacts
        val apk = checkNotNull(artifacts.readApk(path, artifacts.signatureQueryFlags))
        check(apk.packageName == app.packageName && apk.versionName == version)
        check(
            PackageInfoCompat.getLongVersionCode(apk) >
                PackageInfoCompat.getLongVersionCode(app.installed()),
        )
        check(artifacts.hasSameInstalledSignatures(app.packageName, artifacts.getSignatures(apk)))
    }

    override fun close() = scope.cancel()

    private companion object {
        const val LAST_CHECK = "self_update_checked_at"
        const val PENDING = "self_update_pending"
        val terminalStatuses = setOf(Status.SUCCESS, Status.FAILED, Status.CANCELLED)
    }
}

internal fun Application.installed() =
    try {
        packageManager.getPackageInfo(packageName, 0)
    } catch (failure: PackageManager.NameNotFoundException) {
        throw IllegalStateException("Installed App4PDA package is unavailable", failure)
    }

internal fun newerVersion(candidate: String, installed: String?): Boolean {
    val latest = stableVersion(candidate)
    val current = installed?.let(::stableVersion)
    return !(latest == null || current == null) &&
        latest
            .zip(current)
            .firstOrNull { (left, right) -> left != right }
            ?.let { (left, right) -> left > right } ?: false
}

@Serializable
private data class PendingUpdate(
    val version: String,
    val path: String,
    val checksum: String,
    val id: Long,
)

internal sealed interface UpdateState {
    data object Idle : UpdateState

    data class Offer(val release: GitHubRelease) : UpdateState

    data class Ready(val path: String, val version: String, val checksum: String) : UpdateState

    data class Message(val text: Int) : UpdateState
}
