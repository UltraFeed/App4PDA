package io.github.ultrafeed.app4pda.feature.downloads.data

import android.content.Context
import com.ketch.Ketch
import com.ketch.Status
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.network.SourceProblem
import io.github.ultrafeed.app4pda.data.database.DownloadRecordsDao
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Completed-file metadata is independent of a transfer engine's historical jobs. */
internal class DownloadRecords(
    private val context: Context,
    private val engine: Ketch,
    private val dao: DownloadRecordsDao,
    private val storage: DownloadsStorage,
    private val ioDispatcher: CoroutineDispatcher,
    private val lock: Mutex,
) {
    suspend fun reconcile() =
        withContext(ioDispatcher) {
            lock.withLock {
                val tasks = engine.getAllDownloads().associateBy { it.metaData }
                for (row in dao.downloads()) {
                    currentCoroutineContext().ensureActive()
                    val task = tasks[row.url] ?: continue
                    val file = File(row.path)
                    storage.requireDestination(file)
                    var status = task.status.recordStatus()
                    var reason = 0
                    if (task.status == Status.SUCCESS && row.status != TransferStatus.COMPLETE) {
                        try {
                            row.verify(storage.requireFile(row.path))
                        } catch (_: IllegalStateException) {
                            status = TransferStatus.FAILED
                            reason = TransferStatus.INTEGRITY_FAILURE
                        } catch (_: IOException) {
                            status = TransferStatus.FAILED
                            reason = TransferStatus.INTEGRITY_FAILURE
                        }
                    }
                    val updated =
                        row.copy(
                            status = status,
                            totalBytes = if (file.isFile) file.length() else task.total,
                            createdAt =
                                if (status == TransferStatus.COMPLETE)
                                    task.lastModified.takeIf { it > 0 } ?: file.lastModified()
                                else row.createdAt,
                            failureReason = reason,
                            retryAt = 0,
                        )
                    if (updated != row) dao.saveDownload(updated)
                }
            }
        }

    suspend fun unfinishedPaths(): Set<String> =
        dao.unfinishedDownloadPaths(TransferStatus.COMPLETE)
            .flatMap { listOf(it, "$it.temp") }
            .toSet()

    suspend fun finalFailure(): SourceProblem? =
        dao.failedDownloads(TransferStatus.FAILED).firstOrNull()?.let {
            SourceProblem(
                "Downloads · ${if (it.failureReason == TransferStatus.INTEGRITY_FAILURE) "INTEGRITY" else "TRANSFER"}",
                context.getString(
                    if (it.failureReason == TransferStatus.INTEGRITY_FAILURE)
                        R.string.transfer_integrity_failure
                    else R.string.transfer_failed,
                ),
            )
        }

    suspend fun completedRecords() =
        dao.completedDownloads(TransferStatus.COMPLETE).associateBy { it.path }

    suspend fun forget(path: String) =
        withContext(ioDispatcher) {
            lock.withLock {
                val tasks = engine.getAllDownloads().associateBy { it.metaData }
                dao.downloadsForPath(path).forEach { row ->
                    tasks[row.url]?.let { engine.clearDb(it.id, false) }
                    dao.deleteDownload(row.url)
                }
            }
        }

    suspend fun renamed(oldPath: String, newPath: String) =
        withContext(ioDispatcher) {
            lock.withLock {
                val tasks = engine.getAllDownloads().associateBy { it.metaData }
                dao.downloadsForPath(oldPath).forEach { row ->
                    tasks[row.url]?.let { engine.clearDb(it.id, false) }
                    dao.saveDownload(row.copy(path = newPath))
                }
            }
        }
}

/** Stable persisted task states; completed metadata remains readable without engine jobs. */
internal object TransferStatus {
    const val PENDING = 1
    const val RUNNING = 2
    const val PAUSED = 4
    const val COMPLETE = 8
    const val FAILED = 16
    const val CANCELLED = 32
    const val INTEGRITY_FAILURE = 1
}

internal fun Status.recordStatus(): Int =
    when (this) {
        Status.SUCCESS -> TransferStatus.COMPLETE
        Status.FAILED -> TransferStatus.FAILED
        Status.CANCELLED -> TransferStatus.CANCELLED
        Status.PAUSED -> TransferStatus.PAUSED
        Status.PROGRESS,
        Status.STARTED -> TransferStatus.RUNNING
        else -> TransferStatus.PENDING
    }
