package io.github.ultrafeed.app4pda.data.forum

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import io.github.ultrafeed.app4pda.core.AppPolicy
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Entity(
    tableName = "entries",
    indices = [Index(value = ["topicId", "messageId"], unique = true)],
)
data class ForumCacheEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topicId: Int,
    val messageId: Int,
    val updatedAt: Long,
    val checkedAt: Long = updatedAt,
    val sizeBytes: Long,
    val content: ByteArray,
    val etag: String? = null,
    val lastModified: String? = null,
    val postUpdatedAt: Long? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ForumCacheEntry

        if (id != other.id) return false
        if (topicId != other.topicId) return false
        if (messageId != other.messageId) return false
        if (updatedAt != other.updatedAt) return false
        if (checkedAt != other.checkedAt) return false
        if (sizeBytes != other.sizeBytes) return false
        if (postUpdatedAt != other.postUpdatedAt) return false
        if (!content.contentEquals(other.content)) return false
        if (etag != other.etag) return false
        if (lastModified != other.lastModified) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + topicId
        result = 31 * result + messageId
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + checkedAt.hashCode()
        result = 31 * result + sizeBytes.hashCode()
        result = 31 * result + postUpdatedAt.hashCode()
        result = 31 * result + content.contentHashCode()
        result = 31 * result + etag.hashCode()
        result = 31 * result + lastModified.hashCode()
        return result
    }
}

@Dao
interface ForumCacheDao {
    @Query("DELETE FROM entries WHERE topicId = :topic") suspend fun removeTopic(topic: Int)

    @Query(
        "SELECT * FROM entries WHERE topicId = :topic AND messageId = :post LIMIT 1",
    )
    suspend fun entry(topic: Int, post: Int): ForumCacheEntry?

    @Query("DELETE FROM entries WHERE updatedAt <= :cutoff") suspend fun expire(cutoff: Long)

    @Query(
        "UPDATE entries SET checkedAt = :time, etag = :etag, lastModified = :modified, postUpdatedAt = :postUpdatedAt WHERE id = :id",
    )
    suspend fun checked(
        id: Long,
        time: Long,
        etag: String?,
        modified: String?,
        postUpdatedAt: Long?,
    )

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM entries") suspend fun size(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entry: ForumCacheEntry)

    @Query("DELETE FROM entries WHERE id IN (:ids)") suspend fun remove(ids: List<Long>)

    @Query("DELETE FROM entries") suspend fun clear()

    @Transaction
    suspend fun put(entry: ForumCacheEntry) {
        expire(entry.checkedAt - AppPolicy.CACHE_TTL_MILLIS)
        val existing = entry(entry.topicId, entry.messageId)
        if (existing != null && existing.content.contentEquals(entry.content)) {
            checked(
                existing.id,
                entry.checkedAt,
                entry.etag,
                entry.lastModified,
                entry.postUpdatedAt,
            )
        } else insert(entry)
    }
}

@Database(entities = [ForumCacheEntry::class], version = 1, exportSchema = false)
abstract class ForumCacheDatabase : RoomDatabase() {
    abstract fun entries(): ForumCacheDao
}

class ForumCache(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Closeable {
    private val context = context.applicationContext
    private val directory = File(context.cacheDir, "forum-cache")
    private var database: ForumCacheDatabase? = null
    private val operations = Mutex()
    private var generation = 0L

    internal suspend fun revision(): Long = operations.withLock { generation }

    suspend fun removeTopic(topic: Int): Unit = operations.withLock {
        generation++
        withContext(ioDispatcher) { open().entries().removeTopic(topic) }
    }

    private fun open(): ForumCacheDatabase {
        database?.let {
            return it
        }
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create forum cache directory" }
        for (name in listOf("forum-html-room-v4.db", "forum-html-room-v5.db")) {
            for (suffix in listOf("", "-wal", "-shm", "-journal")) {
                val obsolete = File(directory, "$name$suffix")
                check(!obsolete.exists() || obsolete.delete()) {
                    "Cannot remove obsolete temporary forum cache"
                }
            }
        }
        return Room.databaseBuilder(
                context,
                ForumCacheDatabase::class.java,
                File(directory, "forum-cache.db").absolutePath,
            )
            .build()
            .also { database = it }
    }

    suspend fun getEntry(topicId: Int, postId: Int): ForumCacheEntry? {
        return operations.withLock {
            withContext(ioDispatcher) {
                val dao = open().entries()
                dao.expire(
                    System.currentTimeMillis() - AppPolicy.CACHE_TTL_MILLIS,
                )
                dao.entry(topicId, postId)
            }
        }
    }

    internal suspend fun putEntry(
        topicId: Int,
        dataId: Int,
        data: ByteArray,
        etag: String? = null,
        lastModified: String? = null,
        postUpdatedAt: Long? = null,
        revision: Long,
    ) {
        operations.withLock {
            if (revision != generation) return@withLock
            withContext(ioDispatcher) {
                open()
                    .entries()
                    .put(
                        ForumCacheEntry(
                            topicId = topicId,
                            messageId = dataId,
                            updatedAt = System.currentTimeMillis(),
                            sizeBytes = data.size.toLong(),
                            content = data,
                            etag = etag,
                            lastModified = lastModified,
                            postUpdatedAt = postUpdatedAt,
                        ),
                    )
            }
        }
    }

    suspend fun clear(): Unit = operations.withLock {
        generation++
        withContext(ioDispatcher) {
            database?.close()
            database = null
            for (name in
                listOf(
                    "forum-cache.db",
                    "forum-cache.db-wal",
                    "forum-cache.db-shm",
                    "forum-cache.db-journal",
                    "forum-html-room-v4.db",
                    "forum-html-room-v4.db-wal",
                    "forum-html-room-v4.db-shm",
                    "forum-html-room-v4.db-journal",
                    "forum-html-room-v5.db",
                    "forum-html-room-v5.db-wal",
                    "forum-html-room-v5.db-shm",
                    "forum-html-room-v5.db-journal",
                )) {
                val file = File(directory, name)
                check(!file.exists() || file.delete()) { "Cannot clear forum cache file" }
            }
        }
    }

    override fun close() {
        database?.close()
        database = null
    }
}
