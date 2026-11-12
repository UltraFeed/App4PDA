package io.github.ultrafeed.app4pda.data.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

@Entity(tableName = "forum_snapshots")
data class ForumSnapshot(
    @PrimaryKey val topicId: Int,
    val html: String,
    val version: String,
    val updatedAt: Long?,
    val createdAt: Long?,
    val checkedAt: Long,
    val name: String = "",
    val description: String = "",
    val verifiedPackages: String = "",
)

/** Shared server deadline, independent of the removed catalog/discovery queues. */
@Entity(tableName = "forum_queue_state")
data class ForumQueueState(@PrimaryKey val id: Int = 1, val retryAt: Long = 0)

@Dao
interface ForumDao {
    @Query("DELETE FROM forum_snapshots WHERE topicId = :topic")
    suspend fun deleteSnapshot(topic: Int)

    @Query("DELETE FROM forum_snapshots WHERE checkedAt <= :cutoff")
    suspend fun expireSnapshots(cutoff: Long)

    @Query(
        "SELECT * FROM forum_snapshots WHERE instr(';' || verifiedPackages || ';', ';' || :pack || ';') > 0 ORDER BY topicId",
    )
    suspend fun candidates(pack: String): List<ForumSnapshot>

    @Query("SELECT * FROM forum_snapshots WHERE topicId IN (:topics)")
    suspend fun snapshots(topics: List<Int>): List<ForumSnapshot>

    @Query("SELECT * FROM forum_snapshots WHERE topicId = :topic")
    suspend fun snapshot(topic: Int): ForumSnapshot?

    @Upsert suspend fun saveSnapshot(row: ForumSnapshot)

    @Query("SELECT * FROM forum_queue_state WHERE id = 1")
    suspend fun queueState(): ForumQueueState?

    @Upsert suspend fun saveQueueState(state: ForumQueueState)
}
