package io.github.ultrafeed.app4pda.feature.downloads

data class DownloadEntry(
    val id: String,
    val path: String,
    val fileName: String,
    val title: String,
    val packageName: String = "",
    val version: String = "",
    val topicId: Int = 0,
    val totalBytes: Long = 0,
    val createdAt: Long = 0,
    val note: String? = null,
    val group: Boolean = false,
)

data class DownloadsSnapshot(
    val transfers: List<ActiveDownload> = emptyList(),
    val entries: List<DownloadEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

data class InstallRequest(
    val path: String,
    val packageName: String,
    val requiresUninstall: Boolean,
    val installable: Boolean = true,
)

data class DownloadsUiState(
    val transfers: List<ActiveDownload> = emptyList(),
    val entries: List<DownloadEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val listError: String? = null,
    val busy: Boolean = false,
    val pendingInstall: InstallRequest? = null,
    val exportProgress: Float? = null,
    val exportComplete: Boolean = false,
)

enum class TransferCommand {
    PAUSE,
    RESUME,
    CANCEL,
    RETRY,
}

enum class TransferState {
    CANCELLING,
    QUEUED,
    RUNNING,
    PAUSED,
    FAILED,
}

sealed interface TransferTarget {
    data class Single(val id: Int) : TransferTarget

    data class Group(val path: String) : TransferTarget
}

data class ActiveDownload(
    val target: TransferTarget,
    val name: String,
    val state: TransferState,
    val downloadedBytes: Long,
    val totalBytes: Long?,
) {
    val progress: Float?
        get() =
            totalBytes
                ?.takeIf { it > 0 }
                ?.let { (downloadedBytes.toDouble() / it).toFloat().coerceIn(0f, 1f) }
}

internal fun List<ActiveDownload>.overallProgress(): Float? {
    val active = filter { it.state != TransferState.CANCELLING }
    if (active.isEmpty() || active.any { it.totalBytes == null || it.totalBytes <= 0 }) return null
    val total = active.sumOf { checkNotNull(it.totalBytes).toDouble() }
    return (active.sumOf { it.downloadedBytes.toDouble() } / total).toFloat().coerceIn(0f, 1f)
}
