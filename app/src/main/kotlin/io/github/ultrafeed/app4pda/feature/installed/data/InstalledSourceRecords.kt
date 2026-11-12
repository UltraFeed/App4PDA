package io.github.ultrafeed.app4pda.feature.installed.data

import androidx.room.withTransaction
import io.github.ultrafeed.app4pda.core.packages.LocalPackage
import io.github.ultrafeed.app4pda.data.database.InstalledRecord
import io.github.ultrafeed.app4pda.data.database.LocalDatabase
import io.github.ultrafeed.app4pda.data.database.toCacheRecord
import io.github.ultrafeed.app4pda.data.forum.ForumInfo
import io.github.ultrafeed.app4pda.data.googleplay.MarketBatch
import io.github.ultrafeed.app4pda.data.preferences.AppSettings
import io.github.ultrafeed.app4pda.data.rustore.RuStoreDetails
import io.github.ultrafeed.app4pda.feature.installed.InstalledApp
import io.github.ultrafeed.app4pda.feature.installed.SourceDiscovery
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Applies source results only to the package and binding revision that requested them. */
internal class InstalledSourceRecords(
    private val settings: AppSettings,
    private val database: LocalDatabase,
) {
    private val users = database.users()

    internal suspend fun ruStoreAllowed(expected: InstalledApp): Boolean =
        users.installed(expected.key)?.let {
            it.local == expected.local && !it.preferences.hidden && !it.preferences.ignoreRuStore
        } == true

    internal suspend fun applyForum(
        expected: InstalledRecord,
        info: ForumInfo,
        confirmPackage: Boolean,
    ): Boolean = transaction { writeForum(expected, info, confirmPackage) }

    internal suspend fun markBindingSearch(expected: InstalledRecord) = transaction {
        val current = users.installed(expected.key) ?: return@transaction
        if (
            !current.preferences.hidden &&
                !current.preferences.ignoreForum &&
                current.sameBinding(
                    expected,
                )
        )
            users.saveInstalled(current.copy(bindingSearchCompleted = true))
    }

    private suspend fun writeForum(
        expected: InstalledRecord,
        info: ForumInfo,
        confirmPackage: Boolean,
    ): Boolean {
        currentCoroutineContext().ensureActive()
        val current =
            users.installed(expected.key)?.takeIf {
                !it.preferences.hidden &&
                    it.sameBinding(expected) &&
                    it.acceptsForum(
                        info,
                        confirmPackage,
                    )
            } ?: return false
        val now = System.currentTimeMillis()
        users.saveInstalled(
            current.copy(
                forum =
                    current.forum.copy(
                        topicId = info.topicId,
                        name = info.name,
                        versionName = info.getVersionName().ifBlank { current.forum.versionName },
                        description = info.getDescription(),
                        updatedAt = info.getUpdatedAtMillis(),
                        fetchedAt = now,
                        forumType = info.getType(),
                    ),
                bindingSearchCompleted = true,
                bindingRevision = current.bindingRevision + if (confirmPackage) 1 else 0,
            ),
        )
        return true
    }

    internal suspend fun applyPlay(requested: Map<String, LocalPackage>, batch: MarketBatch) =
        transaction {
            currentCoroutineContext().ensureActive()
            if (!settings.current.isGooglePlayCheckEnabled) return@transaction
            val now = System.currentTimeMillis()
            for (name in batch.unavailablePackages) {
                val old = matchingPackage(name, requested) ?: continue
                users.saveInstalled(old.copy(playDiscovery = SourceDiscovery(checkedAt = now)))
                users.deletePlayCache(old.packageName)
            }
            for ((release) in batch.items) {
                currentCoroutineContext().ensureActive()
                val old =
                    matchingPackage(
                        release.packageName,
                        requested,
                    ) ?: continue
                users.saveInstalled(old.copy(playDiscovery = SourceDiscovery(true, now)))
                users.savePlayCache(
                    release.toCacheRecord(),
                )
            }
        }

    internal suspend fun applyRuStore(
        expected: LocalPackage,
        info: RuStoreDetails,
    ) = transaction {
        if (!settings.current.isRuStoreCheckEnabled) return@transaction
        val row = users.installed(info.packageName) ?: return@transaction
        if (row.local == expected && !row.preferences.hidden && !row.preferences.ignoreRuStore)
            users.saveInstalled(
                row.copy(ruStoreDiscovery = SourceDiscovery(true, System.currentTimeMillis())),
            )
    }

    internal suspend fun ruStoreUnavailable(expected: InstalledApp) = transaction {
        if (!settings.current.isRuStoreCheckEnabled) return@transaction
        val row = users.installed(expected.packageName) ?: return@transaction
        if (
            row.local == expected.local && !row.preferences.hidden && !row.preferences.ignoreRuStore
        ) {
            users.saveInstalled(
                row.copy(
                    ruStoreDiscovery = SourceDiscovery(checkedAt = System.currentTimeMillis()),
                ),
            )
            users.deleteRuStoreCache(row.packageName)
        }
    }

    /** A successful batch omission completes this check, without claiming absence. */
    internal suspend fun ruStoreUnresolved(expected: InstalledApp) = transaction {
        if (!settings.current.isRuStoreCheckEnabled) return@transaction
        val row = users.installed(expected.packageName) ?: return@transaction
        if (
            row.local == expected.local && !row.preferences.hidden && !row.preferences.ignoreRuStore
        )
            users.saveInstalled(
                row.copy(
                    ruStoreDiscovery =
                        row.ruStoreDiscovery.copy(checkedAt = System.currentTimeMillis()),
                ),
            )
    }

    private suspend fun <T> transaction(block: suspend () -> T): T = database.withTransaction(block)

    private suspend fun matchingPackage(
        name: String,
        requested: Map<String, LocalPackage>,
    ): InstalledRecord? {
        val expected = requested[name] ?: return null
        return users.installed(name)?.takeIf {
            it.local == expected && !it.preferences.hidden && !it.preferences.ignoreMarket
        }
    }
}

/** A source response belongs to the exact package/binding revision that requested it. */
internal fun InstalledRecord.sameBinding(expected: InstalledRecord): Boolean {
    val sameChoice =
        bindingSearchCompleted == expected.bindingSearchCompleted &&
            bindingRevision == expected.bindingRevision
    return sameChoice && local == expected.local && forum == expected.forum
}

internal fun InstalledRecord.acceptsForum(info: ForumInfo, confirmPackage: Boolean): Boolean =
    info.topicId > 0 &&
        if (confirmPackage) {
            !bindingSearchCompleted &&
                !preferences.ignoreForum &&
                forum.topicId == 0 &&
                packageName in info.packageNames
        } else forum.topicId == info.topicId
