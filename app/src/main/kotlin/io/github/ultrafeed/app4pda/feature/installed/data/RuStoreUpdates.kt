package io.github.ultrafeed.app4pda.feature.installed.data

import io.github.ultrafeed.app4pda.core.AppPolicy
import io.github.ultrafeed.app4pda.data.preferences.AppSettings
import io.github.ultrafeed.app4pda.data.rustore.RuStoreListing
import io.github.ultrafeed.app4pda.data.rustore.RuStoreRepository
import io.github.ultrafeed.app4pda.data.rustore.RuStoreUnavailable
import io.github.ultrafeed.app4pda.feature.installed.InstalledApp
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Check versions in batches; only changed or uncached listings need a full document. */
internal suspend fun checkRuStoreVersions(
    requested: List<InstalledApp>,
    repository: RuStoreRepository,
    settings: AppSettings,
    records: InstalledAppsStore,
    onProgress: () -> Unit,
) {
    for (chunk in requested.chunked(AppPolicy.RUSTORE_BATCH_SIZE)) {
        if (!settings.current.isRuStoreCheckEnabled) return
        val active = chunk.filter { records.sources.ruStoreAllowed(it) }
        if (active.isEmpty()) continue
        val batch = repository.versions(active.map { it.packageName })
        for (app in active) {
            currentCoroutineContext().ensureActive()
            if (!settings.current.isRuStoreCheckEnabled) return
            if (records.sources.ruStoreAllowed(app)) {
                if (app.packageName in batch.unresolved) records.sources.ruStoreUnresolved(app)
                else
                    updateRuStorePage(
                        app,
                        batch.found.getValue(app.packageName),
                        repository,
                        records,
                    )
            }
            onProgress()
        }
    }
}

private suspend fun updateRuStorePage(
    app: InstalledApp,
    listing: RuStoreListing,
    repository: RuStoreRepository,
    records: InstalledAppsStore,
) {
    try {
        val info =
            repository.cached(app.packageName)?.takeIf { it.versionCode == listing.versionCode }
                ?: repository.details(app.packageName, refresh = true)
        records.sources.applyRuStore(checkNotNull(app.local), info)
    } catch (_: RuStoreUnavailable) {
        records.sources.ruStoreUnavailable(app)
    }
}
