package io.github.ultrafeed.app4pda.data.googleplay

import android.content.Context
import android.util.AtomicFile
import androidx.core.util.writeText
import io.github.ultrafeed.app4pda.core.AppPolicy
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Full public Play pages, separated by request language; sessions and delivery URLs never enter it.
 */
class PlayPageCache(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val directory = File(context.cacheDir, "play-pages")
    private val operations = Mutex()
    private var generation = 0L
    private var clearedAt = 0L
    private val removedAt = mutableMapOf<String, Long>()

    suspend fun revision(): Long = operations.withLock { generation }

    suspend fun read(pack: String, language: String): PlayAppDetails? =
        withContext(ioDispatcher) {
            operations.withLock {
                val file = file(pack, language)
                try {
                    val saved =
                        Json.decodeFromString<CachedPlayPage>(
                            AtomicFile(file).openRead().bufferedReader().use { it.readText() },
                        )
                    saved.details.takeIf {
                        it.release.packageName == pack &&
                            now() - saved.fetchedAt in 0 until AppPolicy.CACHE_TTL_MILLIS
                    }
                } catch (_: IOException) {
                    null
                } catch (_: SerializationException) {
                    file.delete()
                    null
                }
            }
        }

    suspend fun save(details: PlayAppDetails, language: String, revision: Long): Unit =
        withContext(ioDispatcher) {
            operations.withLock {
                currentCoroutineContext().ensureActive()
                if (
                    revision >= clearedAt &&
                        revision >= (removedAt[details.release.packageName] ?: 0L)
                ) {
                    check(directory.isDirectory || directory.mkdirs()) {
                        "Cannot create Google Play page cache"
                    }
                    AtomicFile(
                            file(
                                details.release.packageName,
                                language,
                            ),
                        )
                        .writeText(Json.encodeToString(CachedPlayPage(now(), details)))
                }
            }
        }

    suspend fun remove(pack: String): Unit =
        withContext(ioDispatcher) {
            operations.withLock {
                generation++
                removedAt[pack] = generation
                directory
                    .listFiles()
                    .orEmpty()
                    .filter {
                        it.name
                            .removeSuffix(".bak")
                            .removeSuffix(".new")
                            .removeSuffix(".json")
                            .substringBeforeLast('.') == pack
                    }
                    .forEach { check(it.delete() || !it.exists()) }
            }
        }

    suspend fun clear(): Unit =
        withContext(ioDispatcher) {
            operations.withLock {
                generation++
                clearedAt = generation
                removedAt.clear()
                directory.listFiles().orEmpty().forEach { check(it.delete() || !it.exists()) }
            }
        }

    private fun file(pack: String, language: String): File {
        require(pack.isNotBlank() && pack.all { it.isLetterOrDigit() || it == '.' || it == '_' })
        require(language.isNotBlank() && language.all { it.isLetterOrDigit() || it == '-' })
        return File(directory, "$pack.$language.json")
    }
}

@Serializable private data class CachedPlayPage(val fetchedAt: Long, val details: PlayAppDetails)
