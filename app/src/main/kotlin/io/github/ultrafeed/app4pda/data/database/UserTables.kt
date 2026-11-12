package io.github.ultrafeed.app4pda.data.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import io.github.ultrafeed.app4pda.core.packages.LocalPackage
import io.github.ultrafeed.app4pda.data.googleplay.PlayRelease
import io.github.ultrafeed.app4pda.feature.installed.AppPreferences
import io.github.ultrafeed.app4pda.feature.installed.ForumApp
import io.github.ultrafeed.app4pda.feature.installed.InstalledApp
import io.github.ultrafeed.app4pda.feature.installed.MarketRelease
import io.github.ultrafeed.app4pda.feature.installed.SourceDiscovery
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "installed_apps")
data class InstalledRecord(
    @PrimaryKey val key: String,
    val packageName: String,
    @Embedded(prefix = "local_") val local: LocalPackage?,
    @Embedded(prefix = "forum_") val forum: ForumApp,
    @Embedded(prefix = "preference_") val preferences: AppPreferences,
    @Embedded(prefix = "play_") val playDiscovery: SourceDiscovery,
    val bindingSearchCompleted: Boolean,
    val bindingRevision: Long = 0,
    @Embedded(prefix = "rustore_") val ruStoreDiscovery: SourceDiscovery = SourceDiscovery(),
)

data class InstalledSnapshot(
    @Embedded val app: InstalledRecord,
    @Relation(parentColumn = "packageName", entityColumn = "packageName")
    val play: PlayCacheRecord?,
    @Relation(parentColumn = "packageName", entityColumn = "packageName")
    val ruStore: RuStoreCacheRecord?,
) {
    fun toApp(showPlay: Boolean, showRuStore: Boolean = true): InstalledApp =
        app.toApp(
            play
                ?.takeIf { showPlay }
                ?.let { MarketRelease(it.versionName, it.versionCode, it.updatedAt) },
            ruStore
                ?.takeIf { showRuStore }
                ?.let { MarketRelease(it.versionName, it.versionCode, it.fetchedAt) },
        )
}

internal fun InstalledApp.toRecord() =
    InstalledRecord(
        key,
        packageName,
        local,
        forum,
        preferences,
        playDiscovery,
        bindingSearchCompleted,
        ruStoreDiscovery = ruStoreDiscovery,
    )

internal fun InstalledRecord.hide() =
    copy(
        forum = ForumApp(),
        preferences = AppPreferences(hidden = true),
        playDiscovery = SourceDiscovery(),
        ruStoreDiscovery = SourceDiscovery(),
        bindingRevision = bindingRevision + 1,
    )

internal fun PlayRelease.toCacheRecord() =
    PlayCacheRecord(packageName, versionName, versionCode, fetchedAt)

internal fun InstalledRecord.toApp(
    market: MarketRelease? = null,
    ruStore: MarketRelease? = null,
) =
    InstalledApp(
        packageName,
        local,
        forum,
        preferences,
        market,
        playDiscovery,
        bindingSearchCompleted,
        key,
        ruStore = ruStore,
        ruStoreDiscovery = ruStoreDiscovery,
    )

@Entity(tableName = "download_tasks")
data class DownloadRecord(
    @PrimaryKey val url: String,
    val path: String,
    val originalName: String,
    val topicId: Int,
    val postId: Int,
    val totalBytes: Long,
    val status: Int,
    val createdAt: Long,
    val source: String = "url",
    val locator: String = "",
    val packageName: String = "",
    val versionCode: Long = 0,
    val partIndex: Int = -1,
    val partName: String = "",
    val attempts: Int = 0,
    val retryAt: Long = 0,
    val failureReason: Int = 0,
    val expectedBytes: Long = 0,
    val checksum: String = "",
    val replacing: Boolean = false,
)

@Entity(tableName = "play_cache")
data class PlayCacheRecord(
    @PrimaryKey val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val updatedAt: Long,
)

@Entity(tableName = "notified_releases")
data class NotifiedRelease(
    @PrimaryKey val packageName: String,
    val forumVersion: String,
    val playCode: Long,
    val ruStoreCode: Long = 0,
)

@Entity(tableName = "rustore_cache")
data class RuStoreCacheRecord(
    @PrimaryKey val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val fetchedAt: Long,
    val document: String,
)

@Dao
interface UserDao :
    RuStoreCacheDao, PlayCacheDao, InstalledRecordsDao, DownloadRecordsDao, UpdateNotificationDao

interface RuStoreCacheDao {
    @Query("SELECT * FROM rustore_cache WHERE packageName = :pack")
    suspend fun ruStoreCache(pack: String): RuStoreCacheRecord?

    @Upsert suspend fun saveRuStoreCache(row: RuStoreCacheRecord)

    @Query("DELETE FROM rustore_cache WHERE packageName = :pack")
    suspend fun deleteRuStoreCache(pack: String)

    @Query("DELETE FROM rustore_cache") suspend fun clearRuStoreCache()

    @Query("SELECT COALESCE(SUM(LENGTH(CAST(document AS BLOB))), 0) FROM rustore_cache")
    suspend fun ruStoreCacheBytes(): Long

    @Query("DELETE FROM rustore_cache WHERE fetchedAt <= :cutoff")
    suspend fun expireRuStoreCache(cutoff: Long)
}

interface UpdateNotificationDao {
    @Upsert suspend fun saveNotified(row: NotifiedRelease)

    @Query("SELECT * FROM notified_releases WHERE packageName IN (:packages)")
    suspend fun notifiedForPackages(packages: List<String>): List<NotifiedRelease>

    @Transaction @Upsert suspend fun saveNotified(rows: List<NotifiedRelease>)
}

interface InstalledRecordsDao {
    @Query("SELECT * FROM installed_apps") suspend fun installed(): List<InstalledRecord>

    @Query(
        "SELECT DISTINCT forum_topicId FROM installed_apps WHERE preference_favorite = 1 AND forum_topicId > 0",
    )
    suspend fun favoriteTopics(): List<Int>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM installed_apps WHERE preference_favorite = 1 AND ((:topic > 0 AND forum_topicId = :topic) OR (:topic <= 0 AND packageName = :pack)))",
    )
    fun observeFavorite(topic: Int, pack: String): Flow<Boolean>

    @Query("SELECT * FROM installed_apps WHERE `key` = :key")
    suspend fun installed(key: String): InstalledRecord?

    @Query("SELECT * FROM installed_apps WHERE `key` = :key")
    fun observeInstalledRecord(key: String): Flow<InstalledRecord?>

    @Query("SELECT * FROM installed_apps WHERE forum_topicId = :topicId")
    suspend fun installedForTopic(topicId: Int): List<InstalledRecord>

    @Transaction
    @Query("SELECT * FROM installed_apps")
    fun observeInstalled(): Flow<List<InstalledSnapshot>>

    @Transaction
    @Query("SELECT * FROM installed_apps")
    suspend fun installedSnapshot(): List<InstalledSnapshot>

    @Upsert suspend fun saveInstalled(row: InstalledRecord)

    @Query("DELETE FROM installed_apps WHERE `key` = :key")
    suspend fun deleteInstalled(key: String): Int
}

interface DownloadRecordsDao {
    @Query("SELECT * FROM download_tasks") suspend fun downloads(): List<DownloadRecord>

    @Query("SELECT * FROM download_tasks WHERE status = :successful")
    suspend fun completedDownloads(successful: Int): List<DownloadRecord>

    @Query("SELECT * FROM download_tasks WHERE path = :path")
    suspend fun downloadsForPath(path: String): List<DownloadRecord>

    @Query("SELECT * FROM download_tasks WHERE status = :failed")
    suspend fun failedDownloads(failed: Int): List<DownloadRecord>

    @Query("SELECT path FROM download_tasks WHERE status != :successful")
    suspend fun unfinishedDownloadPaths(successful: Int): List<String>

    @Query("SELECT * FROM download_tasks WHERE url = :url")
    suspend fun download(url: String): DownloadRecord?

    @Upsert suspend fun saveDownload(row: DownloadRecord)

    @Query("DELETE FROM download_tasks WHERE url = :url")
    suspend fun deleteDownload(url: String): Int
}

interface PlayCacheDao {
    @Query("SELECT * FROM play_cache") suspend fun playCache(): List<PlayCacheRecord>

    @Query("DELETE FROM play_cache WHERE updatedAt <= :cutoff")
    suspend fun expirePlayCache(cutoff: Long)

    @Query("SELECT * FROM play_cache WHERE packageName = :packageName")
    suspend fun playCache(packageName: String): PlayCacheRecord?

    @Upsert suspend fun savePlayCache(row: PlayCacheRecord)

    @Query("DELETE FROM play_cache WHERE packageName = :packageName")
    suspend fun deletePlayCache(packageName: String): Int
}
