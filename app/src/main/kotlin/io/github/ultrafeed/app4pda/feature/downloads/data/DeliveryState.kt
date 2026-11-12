package io.github.ultrafeed.app4pda.feature.downloads.data

import android.util.AtomicFile
import androidx.core.util.writeText
import io.github.ultrafeed.app4pda.data.database.DownloadRecord
import io.github.ultrafeed.app4pda.data.googleplay.PlayDownloadGroup
import java.io.File
import java.io.IOException

internal enum class DeliveryState {
    PREPARING,
    ACTIVE,
    PAUSED,
    CANCELLING,
    FAILED,
}

internal const val DELIVERY_CONTROL = "transfer-state"

internal fun PlayDownloadGroup.control(): DeliveryState? {
    val file = AtomicFile(File(directory, DELIVERY_CONTROL))
    if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
    return try {
        file.openRead().bufferedReader().use { DeliveryState.valueOf(it.readText()) }
    } catch (invalid: IllegalArgumentException) {
        throw IOException("Invalid delivery control state", invalid)
    }
}

internal fun PlayDownloadGroup.control(state: DeliveryState) {
    AtomicFile(File(directory, DELIVERY_CONTROL)).writeText(state.name)
}

internal fun deliveryGroups(storage: DownloadsStorage): List<PlayDownloadGroup> =
    File(storage.directory, ".play").listFiles().orEmpty().mapNotNull(PlayDownloadGroup::read)

internal fun PlayDownloadGroup.containsPath(path: String): Boolean = files.any {
    file(it).path == path
}

internal fun PlayDownloadGroup.retryRecord(
    index: Int,
): DownloadRecord {
    val part = files[index]
    return DownloadRecord(
        url = "",
        path = file(part).path,
        originalName = part.name,
        topicId = 0,
        postId = 0,
        totalBytes = 0,
        status = TransferStatus.PENDING,
        createdAt = 0,
        source = directory.name.substringBefore('-'),
        packageName = packageName,
        versionCode = versionCode,
        partIndex = index,
        partName = part.name,
        expectedBytes = part.size,
        checksum = part.sha256.ifBlank { part.sha1 },
    )
}
