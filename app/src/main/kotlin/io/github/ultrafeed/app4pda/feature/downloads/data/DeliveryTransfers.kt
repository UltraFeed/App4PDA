package io.github.ultrafeed.app4pda.feature.downloads.data

import android.content.Context
import android.database.SQLException
import android.util.AtomicFile
import android.util.Log
import com.ketch.DownloadModel
import com.ketch.Ketch
import com.ketch.Status
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.data.database.DownloadRecordsDao
import io.github.ultrafeed.app4pda.data.googleplay.PlayDownloadFile
import io.github.ultrafeed.app4pda.data.googleplay.PlayDownloadGroup
import io.github.ultrafeed.app4pda.feature.downloads.ActiveDownload
import io.github.ultrafeed.app4pda.feature.downloads.TransferCommand
import io.github.ultrafeed.app4pda.feature.downloads.TransferState
import io.github.ultrafeed.app4pda.feature.downloads.TransferTarget
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** App-owned preparation and commands for a delivery; Ketch still owns every byte transfer. */
internal class DeliveryTransfers(
    private val context: Context,
    private val engine: Ketch,
    private val dao: DownloadRecordsDao,
    private val storage: DownloadsStorage,
    dispatcher: CoroutineDispatcher,
    private val controller: DownloadController,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val gate = Mutex()
    private val commands = Mutex()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val cancellations = ConcurrentHashMap.newKeySet<String>()
    val changes = MutableStateFlow(0L)

    init {
        scope.launch {
            deliveryGroups(storage)
                .filter { it.control() == DeliveryState.CANCELLING }
                .forEach {
                    try {
                        command(it.directory.path, TransferCommand.CANCEL)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: IOException) {
                        changes.update { value -> value + 1 }
                    } catch (_: IllegalStateException) {
                        changes.update { value -> value + 1 }
                    }
                }
        }
    }

    suspend fun submit(group: PlayDownloadGroup, delivery: List<PlayDownloadFile>) = gate.withLock {
        if (group.files.all { runCatching { group.verify(it) }.isSuccess }) return@withLock
        val state = group.control()
        if (
            jobs[group.directory.path]?.isActive == true ||
                state == DeliveryState.PAUSED ||
                state == DeliveryState.CANCELLING
        )
            return@withLock
        launch(group, delivery)
    }

    private fun launch(group: PlayDownloadGroup, delivery: List<PlayDownloadFile> = emptyList()) {
        group.control(DeliveryState.PREPARING)
        changes.update { it + 1 }
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    prepare(group, delivery)
                    gate.withLock {
                        currentCoroutineContext().ensureActive()
                        group.control(DeliveryState.ACTIVE)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: IOException) {
                    failed(group, failure)
                } catch (failure: IllegalStateException) {
                    failed(group, failure)
                } catch (failure: IllegalArgumentException) {
                    failed(group, failure)
                } catch (failure: SecurityException) {
                    failed(group, failure)
                } catch (failure: SQLException) {
                    failed(group, failure)
                } finally {
                    jobs.remove(group.directory.path, currentCoroutineContext()[Job])
                    changes.update { it + 1 }
                }
            }
        jobs[group.directory.path] = job
        job.start()
    }

    private suspend fun failed(group: PlayDownloadGroup, failure: Exception) {
        gate.withLock {
            currentCoroutineContext().ensureActive()
            if (group.control() == DeliveryState.PREPARING) group.control(DeliveryState.FAILED)
        }
        Log.e("Downloads", "Delivery preparation failed", failure)
    }

    private suspend fun prepare(group: PlayDownloadGroup, delivery: List<PlayDownloadFile>) {
        group.files.forEachIndexed { index, part ->
            currentCoroutineContext().ensureActive()
            if (runCatching { group.verify(part) }.isSuccess) return@forEachIndexed
            val task = engine.getAllDownloads().firstOrNull { it.tag == group.file(part).path }
            if (task?.status == Status.PAUSED) {
                engine.resumeTransfer(task.id)
                return@forEachIndexed
            }
            if (
                task != null &&
                    task.status !in setOf(Status.FAILED, Status.CANCELLED, Status.SUCCESS)
            )
                return@forEachIndexed
            task?.let { engine.removeTransfer(it.id) }
            dao.downloadsForPath(group.file(part).path).forEach { dao.deleteDownload(it.url) }
            val fresh =
                delivery.getOrNull(index)?.takeIf { it.url.isNotBlank() }
                    ?: part.copy(
                        url =
                            controller.locator.renew(
                                group.retryRecord(index),
                                controller.locator::publicUrl,
                            ),
                    )
            currentCoroutineContext().ensureActive()
            controller.enqueue(
                fresh.url,
                group.file(fresh),
                source = group.directory.name.substringBefore('-'),
                pack = group.packageName,
                version = group.versionCode,
                partIndex = index,
                expectedBytes = fresh.size,
                checksum = fresh.sha256.ifBlank { fresh.sha1 },
                partName = fresh.name,
            )
        }
    }

    suspend fun command(path: String, command: TransferCommand) = commands.withLock {
        val group = checkNotNull(PlayDownloadGroup.read(File(path)))
        storage.requireDestination(group.directory)
        require(
            group.directory.canonicalFile.parentFile ==
                File(storage.directory, ".play").canonicalFile,
        )
        val cancelling =
            group.control() == DeliveryState.CANCELLING || command == TransferCommand.CANCEL
        if (
            cancelling &&
                group.files.all { runCatching { group.verify(it) }.isSuccess } &&
                group.control() != DeliveryState.CANCELLING
        )
            return@withLock
        val job = gate.withLock {
            group.control(if (cancelling) DeliveryState.CANCELLING else DeliveryState.PAUSED)
            jobs.remove(path)
        }
        job?.cancelAndJoin()
        if (cancelling) {
            cancellations.add(path)
            try {
                cancel(group)
            } finally {
                cancellations.remove(path)
                changes.update { it + 1 }
            }
        } else if (command == TransferCommand.PAUSE) {
            engine
                .getAllDownloads()
                .filter { group.containsPath(it.tag) && it.status != Status.SUCCESS }
                .forEach { engine.pause(it.id) }
        } else gate.withLock { launch(group) }
        changes.update { it + 1 }
    }

    private suspend fun cancel(group: PlayDownloadGroup) {
        changes.update { it + 1 }
        val tasks = engine.getAllDownloads().filter { group.containsPath(it.tag) }
        tasks.forEach { engine.stopTransfer(context, it.id) }
        val owned =
            group.files.flatMap { listOf(group.file(it), File(group.file(it).path + ".temp")) }
        val metadata =
            listOf(
                PlayDownloadGroup.MANIFEST,
                "${PlayDownloadGroup.MANIFEST}.bak",
                "${PlayDownloadGroup.MANIFEST}.new",
                DELIVERY_CONTROL,
                "$DELIVERY_CONTROL.bak",
                "$DELIVERY_CONTROL.new",
            )
        check(group.directory.listFiles().orEmpty().all { it in owned || it.name in metadata }) {
            "Unexpected delivery file"
        }
        gate.withLock { owned.forEach { storage.deleteFile(it.path) } }
        tasks.forEach { engine.removeTransfer(it.id) }
        group.files.forEach { part ->
            dao.downloadsForPath(group.file(part).path).forEach { dao.deleteDownload(it.url) }
        }
        gate.withLock {
            AtomicFile(File(group.directory, PlayDownloadGroup.MANIFEST)).delete()
            AtomicFile(File(group.directory, DELIVERY_CONTROL)).delete()
            check(group.directory.delete()) { "Cannot remove delivery directory" }
        }
    }

    suspend fun active(tasks: List<DownloadModel>): Pair<Set<Int>, List<ActiveDownload>> =
        gate.withLock {
            val grouped = mutableSetOf<Int>()
            val rows = dao.downloads().associateBy { it.path }
            val active =
                deliveryGroups(storage).mapNotNull { group ->
                    val members = tasks.filter { group.containsPath(it.tag) }
                    grouped += members.map { it.id }
                    val control = group.control()
                    val complete =
                        group.files.all {
                            rows[group.file(it).path]?.status == TransferStatus.COMPLETE
                        }
                    if (complete && control != DeliveryState.CANCELLING) return@mapNotNull null
                    if (members.isEmpty() && control == null) return@mapNotNull null
                    val state =
                        if (group.directory.path in cancellations) TransferState.CANCELLING
                        else
                            deliveryStatus(
                                control,
                                members,
                                members.any {
                                    rows[it.tag]?.failureReason == TransferStatus.INTEGRITY_FAILURE
                                },
                                jobs[group.directory.path]?.isActive == true,
                            )
                    val totals =
                        group.files.map { part ->
                            part.size.takeIf { it > 0 }
                                ?: members
                                    .firstOrNull { it.tag == group.file(part).path }
                                    ?.total
                                    ?.takeIf { it > 0 }
                        }
                    ActiveDownload(
                        TransferTarget.Group(group.directory.path),
                        "${group.packageName} · ${context.getString(R.string.apk_group)}",
                        state,
                        group.files.sumOf { transferBytes(group.file(it)) },
                        if (totals.any { it == null }) null else totals.sumOf { checkNotNull(it) },
                    )
                }
            grouped to active
        }

    override fun close() {
        scope.cancel()
    }
}

private fun deliveryStatus(
    control: DeliveryState?,
    members: List<DownloadModel>,
    integrityFailure: Boolean,
    preparing: Boolean,
): TransferState =
    when {
        control in setOf(DeliveryState.CANCELLING, DeliveryState.FAILED) -> TransferState.FAILED
        control == DeliveryState.PAUSED -> TransferState.PAUSED
        control == DeliveryState.PREPARING ->
            if (preparing) TransferState.QUEUED else TransferState.FAILED
        integrityFailure || members.any { it.status == Status.FAILED } -> TransferState.FAILED
        members.any { it.status in setOf(Status.PROGRESS, Status.STARTED) } -> TransferState.RUNNING
        members.isNotEmpty() && members.all { it.status == Status.SUCCESS } -> TransferState.QUEUED
        members.any { it.status == Status.PAUSED } &&
            members.all { it.status in setOf(Status.PAUSED, Status.SUCCESS) } ->
            TransferState.PAUSED
        else -> TransferState.FAILED
    }
