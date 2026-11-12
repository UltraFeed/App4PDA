package io.github.ultrafeed.app4pda.feature.installed

enum class UpdateProvider {
    FORUM,
    PLAY,
    RUSTORE,
}

enum class AppVersionStatus {
    NONE,
    INSTALLED_NEWER,
    UPDATE_AVAILABLE,
}

data class InstalledCardAppearance(
    val showPlayUpdates: Boolean = true,
    val showRuStoreUpdates: Boolean = true,
)

data class InstalledAppStatus(
    val localVersion: AppVersionStatus,
    val forumUpdate: Boolean,
    val playUpdate: Boolean,
    val ruStoreUpdate: Boolean = false,
)

/** Presentation applies source visibility to the same comparisons used by update lists. */
fun installedAppStatus(
    app: InstalledApp,
    appearance: InstalledCardAppearance,
    unavailableOnPlay: Boolean = false,
): InstalledAppStatus {
    val forumEnabled = app.installed && !app.preferences.ignoreForum
    val playEnabled =
        app.installed &&
            !unavailableOnPlay &&
            appearance.showPlayUpdates &&
            !app.preferences.ignoreMarket
    val ruStoreEnabled =
        app.installed && appearance.showRuStoreUpdates && !app.preferences.ignoreRuStore
    val comparisons =
        listOfNotNull(
            app.forumVersionStatus.takeIf { forumEnabled && app.forum.topicId > 0 },
            storeVersionStatus(app.local?.versionCode, app.market, playEnabled),
            storeVersionStatus(app.local?.versionCode, app.ruStore, ruStoreEnabled),
        )
    return InstalledAppStatus(
        aggregateVersionStatus(comparisons),
        forumEnabled && app.forumUpdate,
        playEnabled && app.marketUpdate,
        ruStoreEnabled && app.ruStoreUpdate,
    )
}

private fun storeVersionStatus(
    localCode: Long?,
    release: MarketRelease?,
    enabled: Boolean,
): AppVersionStatus? {
    val installed = localCode?.takeIf { it > 0 }
    val available = release?.versionCode?.takeIf { it > 0 }
    return when {
        !enabled -> null
        installed == null || available == null -> null
        available > installed -> AppVersionStatus.UPDATE_AVAILABLE
        available < installed -> AppVersionStatus.INSTALLED_NEWER
        else -> AppVersionStatus.NONE
    }
}

private fun aggregateVersionStatus(comparisons: List<AppVersionStatus>): AppVersionStatus =
    when {
        comparisons.any { it == AppVersionStatus.UPDATE_AVAILABLE } ->
            AppVersionStatus.UPDATE_AVAILABLE

        comparisons.isNotEmpty() && comparisons.all { it == AppVersionStatus.INSTALLED_NEWER } ->
            AppVersionStatus.INSTALLED_NEWER

        else -> AppVersionStatus.NONE
    }
