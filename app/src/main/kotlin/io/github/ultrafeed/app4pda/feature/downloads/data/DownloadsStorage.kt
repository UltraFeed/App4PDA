package io.github.ultrafeed.app4pda.feature.downloads.data

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import io.github.ultrafeed.app4pda.R
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/** Completed files are outside disposable cache; Android owns staging transfers. */
class DownloadsStorage(context: Context) {
    private val context = context.applicationContext
    val directory =
        File(
            requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)),
            "completed",
        )

    fun ensureDirectory(): Boolean = directory.let {
        (it.isDirectory || it.mkdirs()) && it.canWrite()
    }

    fun requireDestination(file: File) {
        require(file.canonicalPath.startsWith(directory.canonicalPath + File.separator))
    }

    fun uniqueDestination(name: String): File {
        check(ensureDirectory())
        val safe = name.substringAfterLast('/').substringAfterLast('\\').ifBlank { "download" }
        require(safe != "." && safe != ".." && '\u0000' !in safe)
        val owner = File(directory, UUID.randomUUID().toString())
        check(owner.mkdirs())
        return File(owner, safe)
    }

    fun requireFile(path: String): File {
        val file = File(path).canonicalFile
        require(file.path.startsWith(directory.canonicalPath + File.separator))
        check(file.isFile) {
            context.getString(R.string.downloads_cache_missing)
        }
        return file
    }

    fun completedFiles(partialPaths: Set<String>): List<File> =
        directory
            .walkTopDown()
            .onEnter { it.name != ".play" }
            .filter { it.isFile && it.absolutePath !in partialPaths }
            .toList()

    /** Watches the existing tree and attaches new child directories after structural changes. */
    internal fun changes(dispatcher: CoroutineDispatcher) = callbackFlow {
        val signals = Channel<Unit>(Channel.CONFLATED)
        val watcher =
            launch(dispatcher) {
                val observers = mutableMapOf<String, FileObserver>()
                try {
                    check(ensureDirectory())
                    signals.trySend(Unit)
                    while (signals.receiveCatching().isSuccess) {
                        val directories =
                            directory
                                .walkTopDown()
                                .filter { it.isDirectory }
                                .map { it.absolutePath }
                                .toSet()
                        (observers.keys - directories).forEach {
                            observers.remove(it)?.stopWatching()
                        }
                        (directories - observers.keys).forEach { path ->
                            val onChange = {
                                signals.trySend(Unit)
                                Unit
                            }
                            val observer =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    object : FileObserver(File(path), CHANGE_EVENTS) {
                                        override fun onEvent(event: Int, path: String?) = onChange()
                                    }
                                } else {
                                    object : FileObserver(path, CHANGE_EVENTS) {
                                        override fun onEvent(event: Int, path: String?) = onChange()
                                    }
                                }
                            observers[path] = observer
                            observer.startWatching()
                        }
                        trySend(Unit)
                    }
                } finally {
                    observers.values.forEach { it.stopWatching() }
                }
            }
        awaitClose {
            signals.close()
            watcher.cancel()
        }
    }

    fun deleteFile(path: String) {
        val file = File(path).canonicalFile
        require(file.path.startsWith(directory.canonicalPath + File.separator))
        check(!file.exists() || file.delete()) { "Cannot delete ${file.name}" }
    }

    private companion object {
        const val CHANGE_EVENTS =
            FileObserver.CLOSE_WRITE or
                FileObserver.CREATE or
                FileObserver.DELETE or
                FileObserver.MOVED_FROM or
                FileObserver.MOVED_TO or
                FileObserver.DELETE_SELF or
                FileObserver.MOVE_SELF
    }
}
