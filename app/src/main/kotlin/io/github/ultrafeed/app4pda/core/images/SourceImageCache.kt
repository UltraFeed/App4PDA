package io.github.ultrafeed.app4pda.core.images

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import io.github.ultrafeed.app4pda.core.AppPolicy
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One Coil journal per source. Reconfiguration waits for readers/editors to release their lease.
 */
internal class SourceImageCache(
    private val context: Context,
    origin: ImageOrigin,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val directory = File(context.cacheDir, "images/${origin.name.lowercase()}")
    private val epoch = File(directory.parentFile, "${origin.name.lowercase()}.created")
    private val operations = Mutex()
    private val readers = MutableStateFlow(0)
    private var loader: ImageLoader? = null
    private var limit = 0L

    suspend fun acquire(configuration: suspend () -> ImageCacheSettings): ImageCacheLease? {
        var acquired: ImageCacheLease? = null
        try {
            return withContext(ioDispatcher) {
                operations.withLock {
                    val currentConfiguration = configuration()
                    if (!currentConfiguration.enabled) return@withLock null
                    configure(currentConfiguration.limitBytes)
                    val current = checkNotNull(loader)
                    val expired = expired()
                    if (expired) {
                        readers.first { it == 0 }
                        current.diskCache?.clear()
                        epoch.writeText("")
                        check(epoch.setLastModified(System.currentTimeMillis())) {
                            "Cannot set image cache age"
                        }
                    }
                    readers.update { it + 1 }
                    ImageCacheLease(current, expired) { readers.update { it - 1 } }
                        .also {
                            acquired = it
                        }
                }
            }
        } catch (cancelled: CancellationException) {
            // withContext can cancel delivery after the lease has already been acquired.
            acquired?.close()
            throw cancelled
        }
    }

    suspend fun resize(configuration: suspend () -> ImageCacheSettings) =
        withContext(ioDispatcher) {
            operations.withLock {
                val current = configuration()
                if (!current.enabled) removeDiskCache()
                else if (loader != null || directory.exists()) configure(current.limitBytes)
            }
        }

    suspend fun clear(configuration: ImageCacheSettings) =
        withContext(ioDispatcher) {
            operations.withLock {
                if (!configuration.enabled) removeDiskCache()
                else if (loader != null || directory.exists()) {
                    readers.first { it == 0 }
                    configure(configuration.limitBytes)
                    loader?.diskCache?.clear()
                }
                epoch.delete()
            }
        }

    fun close() {
        loader?.shutdown()
        loader?.diskCache?.shutdown()
        loader = null
    }

    private suspend fun removeDiskCache() {
        readers.first { it == 0 }
        close()
        check(directory.deleteRecursively()) { "Cannot remove image cache" }
        check(!epoch.exists() || epoch.delete()) { "Cannot remove image cache age" }
    }

    suspend fun size(): Long =
        withContext(ioDispatcher) {
            operations.withLock {
                val root = directory.canonicalFile
                directory
                    .walkTopDown()
                    .onFail { _, error -> throw error }
                    .filter { it.isFile }
                    .sumOf {
                        check(it.canonicalPath.startsWith(root.path + File.separator)) {
                            "Image cache path outside its root"
                        }
                        it.length()
                    }
            }
        }

    private suspend fun configure(bytes: Long) {
        if (loader != null && limit == bytes) return
        readers.first { it == 0 }
        close()
        limit = bytes
        // Public shutdown trims an initialized Coil journal to its new bound before reopening.
        if (directory.exists()) {
            val trimming = DiskCache.Builder().directory(directory).maxSizeBytes(bytes).build()
            try {
                trimming.size
            } finally {
                trimming.shutdown()
            }
        }
        loader =
            ImageLoader.Builder(context)
                .memoryCache(null)
                .diskCache { DiskCache.Builder().directory(directory).maxSizeBytes(bytes).build() }
                .build()
    }

    private fun expired(): Boolean =
        !epoch.exists() ||
            System.currentTimeMillis() - epoch.lastModified() !in 0 until AppPolicy.CACHE_TTL_MILLIS
}

internal class ImageCacheLease(
    val loader: ImageLoader,
    val expired: Boolean,
    private val release: () -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean()

    override fun close() {
        if (closed.compareAndSet(false, true)) release()
    }
}
