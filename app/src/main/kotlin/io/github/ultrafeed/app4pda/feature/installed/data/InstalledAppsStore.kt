package io.github.ultrafeed.app4pda.feature.installed.data

import androidx.room.withTransaction
import io.github.ultrafeed.app4pda.core.AppPolicy
import io.github.ultrafeed.app4pda.core.packages.LocalPackage
import io.github.ultrafeed.app4pda.data.database.LocalDatabase
import io.github.ultrafeed.app4pda.data.database.hide
import io.github.ultrafeed.app4pda.data.database.toRecord
import io.github.ultrafeed.app4pda.data.preferences.AppSettings
import io.github.ultrafeed.app4pda.feature.installed.AppPreferences
import io.github.ultrafeed.app4pda.feature.installed.AppPreferencesJson
import io.github.ultrafeed.app4pda.feature.installed.ForumApp
import io.github.ultrafeed.app4pda.feature.installed.InstalledApp
import io.github.ultrafeed.app4pda.feature.settings.data.BackupApp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns local app records, user choices and transactional package snapshots; performs no network IO.
 */
class InstalledAppsStore
internal constructor(
    private val settings: AppSettings,
    private val database: LocalDatabase,
    private val packageScan: () -> Map<String, LocalPackage>,
    private val packageInstalled: (String) -> Boolean,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val packageHasOwnIcon: (String) -> Boolean,
) {
    private val scanMutex = Mutex()
    private val users = database.users()
    internal val sources = InstalledSourceRecords(settings, database)
    internal val choices = InstalledUserChoices(database, ioDispatcher)

    internal suspend fun importPreferences(
        apps: List<BackupApp>,
    ): Int = scanMutex.withLock {
        val installed = packageScan()
        database.withTransaction {
            var applied = 0
            for ((packageName, preferences1, binding) in apps) {
                val local = installed[packageName]
                if (local == null || !packageInstalled(packageName)) continue
                val old = users.installed(packageName) ?: newApp(packageName, local).toRecord()
                val preferences = AppPreferencesJson.merge(old.preferences, preferences1)
                val imported =
                    old.copy(
                        local = local,
                        preferences = preferences,
                        forum = binding?.let { ForumApp(topicId = it.topicId ?: 0) } ?: old.forum,
                        bindingSearchCompleted = binding != null || old.bindingSearchCompleted,
                    )
                users.saveInstalled(
                    if (preferences.hidden) imported.hide()
                    else imported.copy(bindingRevision = old.bindingRevision + 1),
                )
                applied++
            }
            applied
        }
    }

    suspend fun scan() =
        withContext(ioDispatcher) {
            scanMutex.withLock {
                settings.awaitReady()
                val scanned = packageScan()
                database.withTransaction {
                    val previous = users.installed().associateBy { it.key }
                    currentCoroutineContext().ensureActive()
                    val cutoff = System.currentTimeMillis() - AppPolicy.CACHE_TTL_MILLIS
                    database.forum().expireSnapshots(cutoff)
                    users.expirePlayCache(cutoff)
                    users.expireRuStoreCache(cutoff)
                    for (old in
                        previous.values.filter {
                            it.local != null &&
                                it.packageName !in scanned &&
                                !packageInstalled(it.packageName)
                        }) {
                        database.users().saveInstalled(old.copy(local = null))
                        database.users().deletePlayCache(old.packageName)
                    }
                    for ((name, local) in scanned) {
                        currentCoroutineContext().ensureActive()
                        val old = previous[name]
                        val value = old?.copy(local = local) ?: newApp(name, local).toRecord()
                        if (value != old) database.users().saveInstalled(value)
                    }
                }
            }
        }

    private fun newApp(packageName: String, local: LocalPackage) =
        InstalledApp(
            packageName,
            local,
            preferences =
                AppPreferences(
                    hidden = local.system && !local.launchable && !packageHasOwnIcon(packageName),
                ),
        )
}
