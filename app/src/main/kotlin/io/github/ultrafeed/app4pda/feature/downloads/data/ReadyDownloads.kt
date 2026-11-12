package io.github.ultrafeed.app4pda.feature.downloads.data

import android.content.Context
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.packages.PackageInspector
import io.github.ultrafeed.app4pda.data.database.DownloadRecord
import io.github.ultrafeed.app4pda.data.googleplay.PlayDownloadFile
import io.github.ultrafeed.app4pda.data.googleplay.PlayDownloadGroup
import io.github.ultrafeed.app4pda.data.googleplay.PlayFileType
import io.github.ultrafeed.app4pda.feature.downloads.DownloadEntry
import java.io.File
import java.io.IOException

/** A listing snapshot, not a promise that files still exist when an action is invoked. */
internal class ReadyDownloads {
    private data class Stamp<T>(val size: Long, val modified: Long, val metadata: T)

    private data class CachedFile(val stamp: Stamp<DownloadRecord?>, val entry: DownloadEntry)

    private val files = mutableMapOf<String, CachedFile>()
    private val verified = mutableMapOf<String, Stamp<PlayDownloadFile>>()

    fun scan(
        context: Context,
        storage: DownloadsStorage,
        packages: PackageInspector,
        partialPaths: Set<String>,
        completed: Map<String, DownloadRecord>,
    ): List<DownloadEntry> {
        val local =
            storage.completedFiles(partialPaths).mapNotNull { file ->
                cachedFile(packages, file, completed[file.path])
            }
        val deliveries =
            File(storage.directory, ".play")
                .listFiles()
                .orEmpty()
                .mapNotNull(PlayDownloadGroup::read)
        val result =
            local +
                deliveries
                    .filterNot { group ->
                        group.control() == DeliveryState.CANCELLING ||
                            group.files.any { group.file(it).path in partialPaths }
                    }
                    .flatMap { group -> groupEntries(context, packages, group, completed) }
        files.keys.retainAll(
            local.map { it.path }.toSet() +
                deliveries.flatMap { group -> group.files.map { group.file(it).path } },
        )
        verified.keys.retainAll(
            deliveries
                .flatMap { group ->
                    group.files.map { group.file(it).path }
                }
                .toSet(),
        )
        return result
    }

    private fun cachedFile(
        packages: PackageInspector,
        file: File,
        record: DownloadRecord?,
        integrityVerified: Boolean = false,
    ): DownloadEntry? {
        val stamp = Stamp(file.length(), file.lastModified(), record)
        return try {
            files[file.path]?.takeIf { it.stamp == stamp }?.entry
                ?: run {
                    if (!integrityVerified) record?.verify(file)
                    readyFile(packages, file, record).also {
                        files[file.path] = CachedFile(stamp, it)
                    }
                }
        } catch (_: IllegalStateException) {
            files.remove(file.path)
            null
        }
    }

    private fun groupEntries(
        context: Context,
        packages: PackageInspector,
        group: PlayDownloadGroup,
        completed: Map<String, DownloadRecord>,
    ): List<DownloadEntry> {
        val parts =
            try {
                group.verifiedFiles { item -> verifyPart(group, item) }
            } catch (_: IOException) {
                emptyList()
            } catch (_: IllegalStateException) {
                emptyList()
            }
        if (parts.isEmpty()) return emptyList()
        val metadata =
            parts
                .asSequence()
                .filter { (item, _) ->
                    item.type == PlayFileType.BASE || item.type == PlayFileType.APK
                }
                .sortedBy { (item, _) -> item.type != PlayFileType.BASE }
                .mapNotNull { (_, file) ->
                    cachedFile(packages, file, completed[file.path], integrityVerified = true)
                }
                .firstOrNull { it.packageName == group.packageName && it.version.isNotBlank() }
        val entry =
            DownloadEntry(
                id = "group:${group.directory.path}",
                path = group.directory.path,
                fileName = group.directory.name,
                title =
                    metadata?.title?.takeIf { it != group.packageName && it != metadata.fileName }
                        ?: packages.getApplicationName(group.packageName).ifBlank {
                            group.packageName
                        },
                packageName = group.packageName,
                version =
                    metadata?.version?.takeIf(String::isNotBlank) ?: group.versionCode.toString(),
                totalBytes = parts.sumOf { (_, file) -> file.length() },
                group = true,
                createdAt =
                    parts.maxOfOrNull { (_, file) ->
                        completed[file.path]?.createdAt ?: file.lastModified()
                    } ?: group.directory.lastModified(),
            )
        return listOf(entry) +
            parts
                .filter { (item, _) ->
                    item.type == PlayFileType.OBB || item.type == PlayFileType.PATCH
                }
                .map { (_, file) -> expansionEntry(context, file, completed[file.path]) }
    }

    private fun verifyPart(group: PlayDownloadGroup, item: PlayDownloadFile) {
        val file = group.file(item)
        val stamp = Stamp(file.length(), file.lastModified(), item)
        if (!file.isFile || verified[file.path] != stamp) {
            verified.remove(file.path)
            group.verify(item)
            verified[file.path] = stamp
        }
    }

    private fun expansionEntry(context: Context, file: File, record: DownloadRecord?) =
        DownloadEntry(
            id = "file:${record?.url ?: file.path}",
            path = file.path,
            fileName = file.name,
            title = file.name,
            totalBytes = file.length(),
            createdAt = record?.createdAt ?: file.lastModified(),
            note = context.getString(R.string.play_obb),
        )
}

private fun readyFile(
    packages: PackageInspector,
    file: File,
    record: DownloadRecord?,
): DownloadEntry {
    val info =
        if (file.extension.equals("apk", true)) packages.artifacts.readApk(file.absolutePath, 0)
        else null
    info?.applicationInfo?.apply {
        sourceDir = file.absolutePath
        publicSourceDir = file.absolutePath
    }
    return DownloadEntry(
        "file:${record?.url ?: file.absolutePath}",
        file.absolutePath,
        file.name,
        info
            ?.let { packages.artifacts.getApkApplicationName(it, file.absolutePath) }
            .orEmpty()
            .ifBlank { file.name },
        info?.packageName.orEmpty(),
        info?.versionName.orEmpty(),
        totalBytes = file.length(),
        topicId = record?.topicId ?: 0,
        createdAt = record?.createdAt ?: file.lastModified(),
    )
}
