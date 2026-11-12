package io.github.ultrafeed.app4pda.feature.installed.data

import androidx.room.withTransaction
import io.github.ultrafeed.app4pda.data.database.InstalledRecord
import io.github.ultrafeed.app4pda.data.database.LocalDatabase
import io.github.ultrafeed.app4pda.data.database.TopicSummary
import io.github.ultrafeed.app4pda.data.database.hide
import io.github.ultrafeed.app4pda.data.database.toApp
import io.github.ultrafeed.app4pda.data.database.toRecord
import io.github.ultrafeed.app4pda.data.forum.ForumInfo
import io.github.ultrafeed.app4pda.data.forum.ForumSummary
import io.github.ultrafeed.app4pda.feature.installed.ForumApp
import io.github.ultrafeed.app4pda.feature.installed.InstalledApp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** User choices share a transaction boundary; source refreshes never rewrite them. */
internal class InstalledUserChoices(
    private val database: LocalDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val users = database.users()

    private suspend fun change(
        key: String?,
        showingHidden: Boolean = false,
        transform: (InstalledRecord) -> InstalledRecord,
    ): Boolean =
        withContext(ioDispatcher) {
            database.withTransaction {
                val old = key?.let { users.installed(it) } ?: return@withTransaction false
                if (!old.toApp().userChoicesEditable && !(showingHidden && old.preferences.hidden))
                    return@withTransaction false
                val updated = transform(old)
                if (updated != old) users.saveInstalled(updated)
                true
            }
        }

    suspend fun setHidden(key: String?, hidden: Boolean) =
        change(key, showingHidden = !hidden) {
            if (hidden) it.hide()
            else
                it.copy(
                    preferences = it.preferences.copy(hidden = hidden),
                    bindingRevision = it.bindingRevision + 1,
                )
        }

    suspend fun setForumUpdatesIgnored(key: String?, ignored: Boolean) =
        change(key) {
            it.copy(
                preferences = it.preferences.copy(ignoreForum = ignored),
                bindingRevision = it.bindingRevision + 1,
            )
        }

    suspend fun setMarketUpdatesIgnored(key: String?, ignored: Boolean) =
        change(key) { it.copy(preferences = it.preferences.copy(ignoreMarket = ignored)) }

    suspend fun setRuStoreUpdatesIgnored(key: String?, ignored: Boolean) =
        change(key) { it.copy(preferences = it.preferences.copy(ignoreRuStore = ignored)) }

    suspend fun setFavorite(key: String?, favorite: Boolean): Boolean =
        withContext(ioDispatcher) {
            database.withTransaction {
                val old = key?.let { users.installed(it) } ?: return@withTransaction false
                if (!old.toApp().userChoicesEditable) return@withTransaction false
                if (old.forum.topicId > 0) saveFavorite(old.toApp(), favorite)
                else {
                    val updated = old.copy(preferences = old.preferences.copy(favorite = favorite))
                    if (updated != old) users.saveInstalled(updated)
                }
                true
            }
        }

    suspend fun favoriteMetadata(
        summary: TopicSummary,
        type: Int,
        favorite: Boolean,
    ): Boolean =
        withContext(ioDispatcher) {
            val pack = summary.packageName
            if (pack.isBlank() && summary.topicId <= 0) return@withContext false
            val app =
                InstalledApp(
                    pack,
                    forum =
                        ForumApp(
                            summary.topicId,
                            summary.name,
                            summary.version,
                            summary.description,
                            forumType = type,
                        ),
                )
            database.withTransaction {
                val installed = users.installed(pack)
                if (installed != null && !installed.toApp().userChoicesEditable)
                    return@withTransaction false
                saveFavorite(app, favorite)
                true
            }
        }

    private suspend fun saveFavorite(app: InstalledApp, favorite: Boolean) {
        val topic = app.forum.topicId
        val existing = users.installed(app.packageName)?.takeIf { it.forum.topicId == topic }
        val key = existing?.key ?: if (topic > 0) "favorite:$topic" else app.packageName
        val stored = existing ?: users.installed(key)
        val old = stored ?: app.toRecord().copy(key = key)
        if (topic > 0) {
            users
                .installedForTopic(topic)
                .filter { it.key != key && it.local == null }
                .forEach { users.deleteInstalled(it.key) }
        }
        val updated = old.copy(preferences = old.preferences.copy(favorite = favorite))
        if (stored == null || updated != stored) users.saveInstalled(updated)
    }

    suspend fun bindAppToTopic(packageName: String?, forum: ForumSummary): Boolean =
        change(packageName) {
            it.copy(
                bindingSearchCompleted = true,
                bindingRevision = it.bindingRevision + 1,
                forum =
                    ForumApp(
                        topicId = forum.getId(),
                        name = (forum as? ForumInfo)?.name.orEmpty().ifBlank { it.forum.name },
                        versionName = forum.getVersionName().orEmpty(),
                        description = forum.getDescription().orEmpty(),
                        updatedAt = forum.getUpdatedAtMillis(),
                        fetchedAt = System.currentTimeMillis(),
                        forumType = forum.getType(),
                    ),
            )
        }

    suspend fun unlinkApp(packageName: String?): Boolean =
        change(packageName) {
            it.copy(
                forum = ForumApp(),
                bindingSearchCompleted = true,
                bindingRevision = it.bindingRevision + 1,
            )
        }
}
