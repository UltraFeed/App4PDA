package io.github.ultrafeed.app4pda.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities =
        [
            InstalledRecord::class,
            DownloadRecord::class,
            PlayCacheRecord::class,
            RuStoreCacheRecord::class,
            ForumSnapshot::class,
            ForumQueueState::class,
            NotifiedRelease::class,
        ],
    version = 1,
    exportSchema = false,
)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun users(): UserDao

    abstract fun forum(): ForumDao
}
