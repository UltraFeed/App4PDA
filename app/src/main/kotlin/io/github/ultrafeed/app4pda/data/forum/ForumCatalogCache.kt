package io.github.ultrafeed.app4pda.data.forum

import android.content.Context
import android.util.AtomicFile
import androidx.core.util.writeText
import io.github.ultrafeed.app4pda.core.AppPolicy
import java.io.File
import java.io.IOException
import java.security.MessageDigest
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

/** Public parsed listings only; never stores HTML, cookies or account metadata. */
class ForumCatalogCache(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val directory = File(context.cacheDir, "catalog-pages")
    private val operations = Mutex()
    private var generation = 0L
    private val missing = mutableMapOf<Int, Long>()

    suspend fun revision(): Long = operations.withLock { generation }

    suspend fun read(key: String): ForumListingPage? =
        withContext(ioDispatcher) {
            operations.withLock {
                val file = file(key)
                try {
                    val entry =
                        Json.decodeFromString<CachedCatalogPage>(
                            file.openRead().bufferedReader().use { it.readText() },
                        )
                    if (now() - entry.fetchedAt in 0 until AppPolicy.CACHE_TTL_MILLIS) {
                        excludeMissing(entry.page)
                    } else {
                        file.delete()
                        null
                    }
                } catch (_: IOException) {
                    null
                } catch (_: SerializationException) {
                    file.delete()
                    null
                }
            }
        }

    suspend fun save(key: String, page: ForumListingPage, revision: Long): ForumListingPage =
        withContext(ioDispatcher) {
            operations.withLock {
                currentCoroutineContext().ensureActive()
                val filtered = excludeMissing(page)
                if (revision != generation) return@withLock filtered
                check(directory.isDirectory || directory.mkdirs()) { "Cannot create catalog cache" }
                file(key)
                    .writeText(
                        Json.encodeToString(CachedCatalogPage(now(), filtered)),
                    )
                filtered
            }
        }

    suspend fun removeTopic(topic: Int) =
        withContext(ioDispatcher) {
            operations.withLock {
                generation++
                missing[topic] = now()
                directory
                    .listFiles()
                    .orEmpty()
                    .filter { it.extension == "json" }
                    .forEach { path ->
                        val file = AtomicFile(path)
                        try {
                            val saved =
                                Json.decodeFromString<CachedCatalogPage>(
                                    file.openRead().bufferedReader().use { it.readText() },
                                )
                            val filtered = excludeMissing(saved.page)
                            if (filtered != saved.page)
                                file.writeText(Json.encodeToString(saved.copy(page = filtered)))
                        } catch (_: IOException) {
                            file.delete()
                        } catch (_: SerializationException) {
                            file.delete()
                        }
                    }
            }
        }

    suspend fun clear() =
        withContext(ioDispatcher) {
            operations.withLock {
                generation++
                missing.clear()
                directory.listFiles().orEmpty().forEach { check(it.delete() || !it.exists()) }
            }
        }

    private fun excludeMissing(page: ForumListingPage): ForumListingPage {
        missing.entries.removeAll { now() - it.value !in 0 until AppPolicy.CACHE_TTL_MILLIS }
        return page.copy(topics = page.topics.filterNot { it.id in missing })
    }

    private fun file(key: String): AtomicFile {
        val name = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).toHexString()
        return AtomicFile(File(directory, "$name.json"))
    }
}

@Serializable private data class CachedCatalogPage(val fetchedAt: Long, val page: ForumListingPage)
