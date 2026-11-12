package io.github.ultrafeed.app4pda.feature.downloads.data

import android.content.Context
import androidx.core.net.toUri
import com.ketch.Ketch
import com.ketch.Status
import io.github.ultrafeed.app4pda.data.database.DownloadRecord
import io.github.ultrafeed.app4pda.data.database.DownloadRecordsDao
import io.github.ultrafeed.app4pda.data.forum.isForumHost
import io.github.ultrafeed.app4pda.data.googleplay.PlayDownloadFile
import io.github.ultrafeed.app4pda.data.googleplay.PlayDownloadGroup
import io.github.ultrafeed.app4pda.data.googleplay.PlayFileType
import io.github.ultrafeed.app4pda.feature.downloads.ActiveDownload
import io.github.ultrafeed.app4pda.feature.downloads.TransferCommand
import io.github.ultrafeed.app4pda.feature.downloads.TransferState
import io.github.ultrafeed.app4pda.feature.downloads.TransferTarget
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Ketch owns transfers; the app retains source resolution and integrity metadata. */
class DownloadController(
    context: Context,
    private val dao: DownloadRecordsDao,
    internal val storage: DownloadsStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    internal val locator: DownloadLocator,
    private val engine: Ketch,
) : AutoCloseable {
    val submissions = DownloadSubmissions(this, locator, ioDispatcher)

    private val lock = Mutex()
    internal val records =
        DownloadRecords(context.applicationContext, engine, dao, storage, ioDispatcher, lock)
    private val groups =
        DeliveryTransfers(
            context.applicationContext,
            engine,
            dao,
            storage,
            ioDispatcher,
            this,
        )
    internal val groupEvents
        get() = groups.changes

    suspend fun requestDownload(
        url: String,
        fileName: String,
        topicId: Int,
        postId: Int,
    ): Long =
        withContext(ioDispatcher) {
            val target =
                storage.uniqueDestination(
                    fileName.ifBlank {
                        url.toUri().lastPathSegment.orEmpty().ifBlank { "download" }
                    },
                )
            enqueue(url, target, topicId, postId)
        }

    suspend fun enqueue(
        url: String,
        destination: File,
        topicId: Int = 0,
        postId: Int = 0,
        source: String = "url",
        pack: String = "",
        version: Long = 0,
        partIndex: Int = -1,
        expectedBytes: Long = 0,
        checksum: String = "",
        partName: String = "",
    ): Long =
        withContext(ioDispatcher) {
            val resolved =
                if (isForumHost(url.toUri().host.orEmpty())) locator.publicUrl(url) else url
            require(resolved.toUri().scheme == "https") { "HTTPS download required" }
            val resumable = engine.getContentLength(resolved) > 0
            lock.withLock {
                storage.requireDestination(destination)
                val existing =
                    dao.downloadsForPath(destination.path).firstOrNull {
                        it.status != TransferStatus.COMPLETE
                    }
                if (existing != null) {
                    engine
                        .getAllDownloads()
                        .firstOrNull { it.metaData == existing.url }
                        ?.let {
                            return@withLock it.id.toLong()
                        }
                }
                val row =
                    DownloadRecord(
                        url = "transfer:${java.util.UUID.randomUUID()}",
                        path = destination.path,
                        originalName = destination.name,
                        topicId = topicId,
                        postId = postId,
                        totalBytes = 0,
                        status = TransferStatus.PENDING,
                        createdAt = System.currentTimeMillis(),
                        source = source,
                        locator = if (source == "url") url else "",
                        packageName = pack,
                        versionCode = version,
                        partIndex = partIndex,
                        partName = partName,
                        expectedBytes = expectedBytes,
                        checksum = checksum,
                    )
                dao.saveDownload(row)
                var enqueued = false
                try {
                    start(row, resolved, resumable).also { enqueued = true }
                } finally {
                    if (!enqueued) dao.deleteDownload(row.url)
                }
            }
        }

    private suspend fun start(row: DownloadRecord, url: String, resumable: Boolean): Long {
        val destination = File(row.path)
        storage.requireDestination(destination)
        val directory = checkNotNull(destination.parentFile)
        check(directory.isDirectory || directory.mkdirs())
        check(!destination.exists() || destination.delete()) {
            "Cannot replace incomplete download"
        }
        val temporary = File(destination.path + ".temp")
        check(!temporary.exists() || temporary.delete()) { "Cannot replace partial download" }
        return engine
            .downloadSync(
                url = url,
                path = directory.path,
                fileName = destination.name,
                tag = row.path,
                metaData = row.url,
                supportPauseResume = resumable,
            )
            .toLong()
    }

    suspend fun prepareDirectory(): Boolean =
        withContext(ioDispatcher) { storage.ensureDirectory() }

    suspend fun enqueuePlay(
        packageName: String,
        version: Long,
        delivery: List<PlayDownloadFile>,
        source: String = "play",
    ) =
        withContext(ioDispatcher) {
            val single =
                delivery.singleOrNull()?.takeIf {
                    source != "rustore" &&
                        (it.type == PlayFileType.BASE || it.type == PlayFileType.APK)
                }
            if (single != null) {
                enqueue(
                    single.url,
                    storage.uniqueDestination("$packageName-$version.apk"),
                    source = source,
                    pack = packageName,
                    version = version,
                    partIndex = 0,
                    expectedBytes = single.size,
                    checksum = single.sha256.ifBlank { single.sha1 },
                    partName = single.name,
                )
                return@withContext
            }
            val group =
                PlayDownloadGroup.create(
                    storage.directory,
                    packageName,
                    version,
                    delivery,
                    source,
                )
            groups.submit(group, delivery)
        }

    val events
        get() = engine.observeDownloads()

    suspend fun active(): List<ActiveDownload> =
        withContext(ioDispatcher) {
            val failed = dao.failedDownloads(TransferStatus.FAILED).map { it.url }.toSet()
            val tasks = engine.getAllDownloads()
            val (members, deliveries) = groups.active(tasks)
            deliveries +
                tasks
                    .filter {
                        it.id !in members &&
                            File(it.path).parentFile != File(storage.directory, ".play") &&
                            (it.status !in setOf(Status.SUCCESS, Status.CANCELLED) ||
                                it.metaData in failed)
                    }
                    .map {
                        it.toTransfer().let { transfer ->
                            if (it.metaData in failed)
                                transfer.copy(
                                    state = TransferState.FAILED,
                                )
                            else transfer
                        }
                    }
        }

    suspend fun command(
        target: TransferTarget,
        command: TransferCommand,
    ) =
        withContext(ioDispatcher) {
            when (target) {
                is TransferTarget.Group ->
                    withContext(ioDispatcher) { groups.command(target.path, command) }
                is TransferTarget.Single -> singleCommand(target.id, command)
            }
        }

    private suspend fun singleCommand(
        id: Int,
        command: TransferCommand,
    ) {
        when (command) {
            TransferCommand.PAUSE -> engine.pause(id)
            TransferCommand.RESUME -> engine.resume(id)
            TransferCommand.CANCEL -> engine.cancel(id)
            TransferCommand.RETRY -> {
                val model = engine.getDownloadModelById(id) ?: return
                val row = dao.download(model.metaData) ?: return
                val fresh = locator.renew(row, locator::publicUrl)
                val resumable = engine.getContentLength(fresh) > 0
                engine.removeTransfer(id)
                lock.withLock {
                    start(row, fresh, resumable)
                    dao.saveDownload(
                        row.copy(status = TransferStatus.PENDING, failureReason = 0, retryAt = 0),
                    )
                }
            }
        }
    }

    suspend fun retryFailed() {
        active()
            .filter {
                it.state == TransferState.FAILED
            }
            .forEach {
                command(
                    it.target,
                    TransferCommand.RETRY,
                )
            }
    }

    override fun close() {
        submissions.close()
        groups.close()
    }
}
