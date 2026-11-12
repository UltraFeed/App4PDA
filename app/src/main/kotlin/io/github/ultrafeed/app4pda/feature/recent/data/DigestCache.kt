package io.github.ultrafeed.app4pda.feature.recent.data

import android.util.AtomicFile
import androidx.core.util.writeText
import io.github.ultrafeed.app4pda.core.AppPolicy
import io.github.ultrafeed.app4pda.data.forum.ForumTopicPreview
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class CachedDigestTopic(val fetchedAt: Long, val preview: ForumTopicPreview)

@Serializable
internal data class CachedDigest(
    val checkedAt: Long,
    val groups: List<DigestGroup>,
    val issues: List<DigestIssue>,
)

data class DigestSnapshot(
    val groups: List<DigestGroup> = emptyList(),
    val previews: Map<Int, ForumTopicPreview> = emptyMap(),
    val issues: List<DigestIssue> = emptyList(),
)

/** One successful publication snapshot; failed refreshes do not renew its lifetime. */
internal class DigestCache(
    directory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long,
) {
    private val content = AtomicFile(File(directory, "digest.json"))
    private val checked = AtomicFile(File(directory, "digest.checked"))
    private val topics = AtomicFile(File(directory, "digest-topics.json"))
    private val mutex = Mutex()

    suspend fun read(): List<DigestGroup> = snapshot().groups

    suspend fun snapshot(): DigestSnapshot =
        withContext(ioDispatcher) {
            mutex.withLock {
                val current = readContent()
                val ids = current.groups.flatMap { it.entries }.map { it.topicId }.toSet()
                current.copy(
                    previews = readTopics().filterKeys { it in ids }.mapValues { it.value.preview },
                )
            }
        }

    private fun readTopics(): Map<Int, CachedDigestTopic> =
        try {
            Json.decodeFromString<Map<Int, CachedDigestTopic>>(read(topics)).filterValues {
                now() - it.fetchedAt in 0 until AppPolicy.CACHE_TTL_MILLIS
            }
        } catch (_: IOException) {
            emptyMap()
        } catch (_: SerializationException) {
            emptyMap()
        }

    suspend fun savePreview(topic: Int, preview: ForumTopicPreview) =
        withContext(ioDispatcher) {
            mutex.withLock {
                topics.writeText(
                    Json.encodeToString(
                        readTopics() + (topic to CachedDigestTopic(now(), preview)),
                    ),
                )
            }
        }

    suspend fun freshPreview(topic: Int): ForumTopicPreview? =
        withContext(ioDispatcher) {
            mutex.withLock {
                readTopics()[topic]
                    ?.takeIf {
                        now() - it.fetchedAt in 0 until AppPolicy.DIGEST_METADATA_INTERVAL_MILLIS
                    }
                    ?.preview
            }
        }

    suspend fun clear() =
        withContext(ioDispatcher) {
            mutex.withLock {
                content.delete()
                checked.delete()
                topics.delete()
            }
        }

    suspend fun removeTopic(topic: Int) =
        withContext(ioDispatcher) {
            mutex.withLock {
                val current = readContent()
                val groups =
                    current.groups
                        .map { group ->
                            group.copy(entries = group.entries.filterNot { it.topicId == topic })
                        }
                        .filter { it.entries.isNotEmpty() }
                if (groups != current.groups) {
                    val stored = Json.decodeFromString<CachedDigest>(read(content))
                    content.writeText(Json.encodeToString(stored.copy(groups = groups)))
                }
                topics.writeText(Json.encodeToString(readTopics() - topic))
            }
        }

    private fun readContent(): DigestSnapshot =
        try {
            val data = Json.decodeFromString<CachedDigest>(read(content))
            val timestamp =
                try {
                    read(checked).toLongOrNull() ?: data.checkedAt
                } catch (_: IOException) {
                    data.checkedAt
                }
            val periods = data.issues.map { it.period }.toSet()
            if (
                now() - timestamp in 0 until AppPolicy.CACHE_TTL_MILLIS &&
                    data.groups.all { it.period in periods }
            ) {
                DigestSnapshot(groups = data.groups, issues = data.issues)
            } else DigestSnapshot()
        } catch (_: IOException) {
            DigestSnapshot()
        } catch (_: SerializationException) {
            DigestSnapshot()
        }

    suspend fun save(snapshot: DigestSnapshot) =
        withContext(ioDispatcher) {
            mutex.withLock {
                val current = readContent()
                if (current.groups != snapshot.groups || current.issues != snapshot.issues) {
                    content.writeText(
                        Json.encodeToString(CachedDigest(now(), snapshot.groups, snapshot.issues)),
                    )
                }
                checked.writeText("${now()}")
            }
        }

    private fun read(file: AtomicFile): String =
        file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
}
