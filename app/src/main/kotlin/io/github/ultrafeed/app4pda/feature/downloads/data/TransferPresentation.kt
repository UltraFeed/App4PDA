package io.github.ultrafeed.app4pda.feature.downloads.data

import android.content.Context
import androidx.work.WorkManager
import com.ketch.DownloadModel
import com.ketch.Ketch
import com.ketch.Status
import io.github.ultrafeed.app4pda.core.AppPolicy
import io.github.ultrafeed.app4pda.feature.downloads.ActiveDownload
import io.github.ultrafeed.app4pda.feature.downloads.TransferState
import io.github.ultrafeed.app4pda.feature.downloads.TransferTarget
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

internal fun DownloadModel.toTransfer() =
    ActiveDownload(
        TransferTarget.Single(id),
        fileName,
        when (status) {
            Status.PAUSED -> TransferState.PAUSED
            Status.FAILED -> TransferState.FAILED
            Status.STARTED,
            Status.PROGRESS -> TransferState.RUNNING
            else -> TransferState.QUEUED
        },
        transferBytes(File(path, fileName)),
        total.takeIf { it > 0 },
    )

internal fun transferBytes(file: File): Long =
    if (file.isFile) file.length() else File(file.path + ".temp").length()

internal suspend fun Ketch.resumeTransfer(id: Int) {
    val resumed =
        withTimeoutOrNull(
            AppPolicy.DOWNLOAD_COMMAND_TIMEOUT_MILLIS.milliseconds,
        ) {
            resume(id)
            observeDownloads().first { tasks ->
                tasks.any {
                    it.id == id &&
                        it.status in
                            setOf(
                                Status.QUEUED,
                                Status.STARTED,
                                Status.PROGRESS,
                                Status.SUCCESS,
                            )
                }
            }
            true
        }
    if (resumed != true) throw IOException("Transfer resume was not acknowledged")
}

internal suspend fun Ketch.removeTransfer(id: Int) {
    val removed =
        withTimeoutOrNull(
            AppPolicy.DOWNLOAD_COMMAND_TIMEOUT_MILLIS.milliseconds,
        ) {
            clearDb(id, false)
            observeDownloads().first { tasks -> tasks.none { it.id == id } }
            true
        }
    if (removed != true) throw IOException("Transfer removal was not acknowledged")
}

internal suspend fun Ketch.stopTransfer(context: Context, id: Int) {
    val stopped =
        withTimeoutOrNull(
            AppPolicy.DOWNLOAD_COMMAND_TIMEOUT_MILLIS.milliseconds,
        ) {
            cancel(id)
            observeDownloads().first { tasks ->
                tasks.none { it.id == id && it.status !in setOf(Status.CANCELLED, Status.SUCCESS) }
            }
            // Ketch 2.0.6 names each unique WorkManager request with its numeric transfer ID.
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow("$id").first { work ->
                work.all { it.state.isFinished }
            }
            true
        }
    if (stopped != true) throw IOException("Transfer stop was not acknowledged")
}
