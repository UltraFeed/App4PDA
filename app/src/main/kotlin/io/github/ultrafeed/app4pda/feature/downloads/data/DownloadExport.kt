package io.github.ultrafeed.app4pda.feature.downloads.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class DownloadExport(private val resolver: ContentResolver) {
    suspend fun copy(
        files: List<File>,
        destination: Uri,
        group: Boolean,
        progress: (Float) -> Unit,
    ) {
        require(files.isNotEmpty() && files.all { it.isFile }) {
            "Downloaded file is missing. Download it again."
        }
        val created = mutableListOf<Uri>()
        val total = files.sumOf { it.length() }.coerceAtLeast(1)
        var copied = 0L
        var completed = false
        try {
            val parent =
                if (group)
                    DocumentsContract.buildDocumentUriUsingTree(
                        destination,
                        DocumentsContract.getTreeDocumentId(destination),
                    )
                else null
            for (file in files) {
                currentCoroutineContext().ensureActive()
                val expectedBytes = file.length()
                var fileBytes = 0L
                val target =
                    if (parent != null)
                        checkNotNull(
                            DocumentsContract.createDocument(
                                resolver,
                                parent,
                                "application/vnd.android.package-archive",
                                file.name,
                            ),
                        )
                    else destination
                created += target
                copyFile(file, target) { count ->
                    copied += count
                    fileBytes += count
                    progress((copied.toFloat() / total).coerceIn(0f, 1f))
                }
                check(fileBytes == expectedBytes && file.length() == expectedBytes) {
                    "Source file changed during export"
                }
            }
            progress(1f)
            completed = true
        } finally {
            if (!completed)
                created.forEach {
                    runCatching { DocumentsContract.deleteDocument(resolver, it) }
                }
        }
    }

    private suspend fun copyFile(file: File, target: Uri, onBytes: (Int) -> Unit) {
        file.inputStream().use { input ->
            checkNotNull(resolver.openOutputStream(target, "wt")) { "Cannot open destination" }
                .use { output ->
                    val buffer = ByteArray(EXPORT_BUFFER_BYTES)
                    var count = input.read(buffer)
                    while (count >= 0) {
                        currentCoroutineContext().ensureActive()
                        output.write(buffer, 0, count)
                        onBytes(count)
                        count = input.read(buffer)
                    }
                }
        }
    }
}

private const val EXPORT_BUFFER_BYTES = 32 * 1024
