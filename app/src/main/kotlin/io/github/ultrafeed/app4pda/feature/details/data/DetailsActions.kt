package io.github.ultrafeed.app4pda.feature.details.data

import io.github.ultrafeed.app4pda.data.database.InstalledRecordsDao
import io.github.ultrafeed.app4pda.feature.installed.data.InstalledAppsStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Explicit user choices are independent of page and store loading. */
class DetailsActions(
    private val installed: InstalledAppsStore,
    private val ioDispatcher: CoroutineDispatcher,
    private val users: InstalledRecordsDao,
) {
    fun observeFavorite(topic: Int, pack: String) = users.observeFavorite(topic, pack)

    suspend fun setFavorite(snapshot: DetailsContext, value: Boolean) =
        withContext(ioDispatcher) {
            val app = checkNotNull(snapshot.summary)
            check(
                installed.choices.favoriteMetadata(
                    app,
                    snapshot.forum?.getType() ?: -1,
                    value,
                ),
            )
        }

    suspend fun bind(snapshot: DetailsContext, packageName: String) =
        withContext(ioDispatcher) {
            check(installed.choices.bindAppToTopic(packageName, checkNotNull(snapshot.forum)))
        }

    suspend fun unlink(packageName: String) =
        withContext(ioDispatcher) { check(installed.choices.unlinkApp(packageName)) }
}
