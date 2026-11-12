package io.github.ultrafeed.app4pda.feature.installed

import io.github.ultrafeed.app4pda.core.formatting.VersionComparator
import io.github.ultrafeed.app4pda.core.network.SourceProblem
import io.github.ultrafeed.app4pda.core.packages.LocalPackage
import kotlinx.serialization.Serializable

/** Application identity is its package, or its topic when no package is known. */
class InstalledApp(
    val packageName: String,
    val local: LocalPackage? = null,
    val forum: ForumApp = ForumApp(),
    val preferences: AppPreferences = AppPreferences(),
    val market: MarketRelease? = null,
    val playDiscovery: SourceDiscovery = SourceDiscovery(),
    val bindingSearchCompleted: Boolean = false,
    private val entryKey: String? = null,
    val ruStore: MarketRelease? = null,
    val ruStoreDiscovery: SourceDiscovery = SourceDiscovery(),
) {
    val key: String
        get() = entryKey ?: packageName.ifBlank { forum.topicId.toString() }

    val name: String
        get() = local?.name ?: forum.name

    val installed: Boolean
        get() = local != null

    val eligibleForUpdates: Boolean
        get() = installed && !hidden

    val hidden: Boolean
        get() = preferences.hidden

    val forumVersionStatus: AppVersionStatus =
        if (forum.topicId <= 0) AppVersionStatus.NONE
        else
            when (
                VersionComparator.compare(
                    local?.versionName,
                    forum.versionName,
                )
            ) {
                VersionComparator.INSTALLED_NEWER -> AppVersionStatus.INSTALLED_NEWER
                VersionComparator.AVAILABLE_NEWER -> AppVersionStatus.UPDATE_AVAILABLE
                else -> AppVersionStatus.NONE
            }
    val forumUpdate: Boolean
        get() = forumVersionStatus == AppVersionStatus.UPDATE_AVAILABLE

    val marketUpdate: Boolean =
        local != null && local.versionCode > 0 && (market?.versionCode ?: 0) > local.versionCode
    val ruStoreUpdate: Boolean =
        local != null && local.versionCode > 0 && (ruStore?.versionCode ?: 0) > local.versionCode
    val visibleUpdate: Boolean
        get() =
            eligibleForUpdates &&
                !hidden &&
                ((forumUpdate && !preferences.ignoreForum) ||
                    (marketUpdate && !preferences.ignoreMarket) ||
                    (ruStoreUpdate && !preferences.ignoreRuStore))

    fun with(
        local: LocalPackage? = this.local,
        forum: ForumApp = this.forum,
        preferences: AppPreferences = this.preferences,
        market: MarketRelease? = this.market,
        playDiscovery: SourceDiscovery = this.playDiscovery,
        ruStore: MarketRelease? = this.ruStore,
        ruStoreDiscovery: SourceDiscovery = this.ruStoreDiscovery,
    ): InstalledApp =
        InstalledApp(
            packageName,
            local,
            forum,
            preferences,
            market,
            playDiscovery,
            bindingSearchCompleted,
            entryKey,
            ruStore,
            ruStoreDiscovery,
        )

    fun belongsTo(section: AppSection): Boolean {
        if (hidden) return section == AppSection.HIDDEN
        return when (section) {
            AppSection.FAVORITES -> preferences.favorite
            AppSection.HIDDEN -> false
            AppSection.UPDATES -> visibleUpdate
            AppSection.APPLICATIONS -> installed && (forum.topicId > 0 || preferences.ignoreForum)
            AppSection.UNLINKED -> eligibleForLinking && bindingSearchCompleted
            AppSection.PENDING_LINKING -> eligibleForLinking && !bindingSearchCompleted
        }
    }

    private val eligibleForLinking: Boolean
        get() = installed && forum.topicId <= 0 && !preferences.ignoreForum

    val userChoicesEditable: Boolean
        get() = !hidden && !belongsTo(AppSection.PENDING_LINKING)
}

data class ForumApp(
    val topicId: Int = 0,
    val name: String = "",
    val versionName: String = "",
    val description: String = "",
    val updatedAt: Long = 0,
    val fetchedAt: Long = 0,
    val forumType: Int = -1,
    val iconUrl: String = "",
    val iconVersion: Long = 0,
)

data class SourceDiscovery(val found: Boolean = false, val checkedAt: Long = 0)

@Serializable
data class AppPreferences(
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    val ignoreForum: Boolean = false,
    val ignoreMarket: Boolean = false,
    val ignoreRuStore: Boolean = false,
)

data class MarketRelease(val versionName: String, val versionCode: Long, val updatedAt: Long)

@Serializable
enum class AppSection {
    APPLICATIONS,
    UNLINKED,
    PENDING_LINKING,
    FAVORITES,
    UPDATES,
    HIDDEN,
}

enum class UpdateSource {
    OPERATION,
    PACKAGES,
    FORUM,
    MARKET,
    RUSTORE,
}

data class InstalledAppsState(
    val apps: List<InstalledApp> = emptyList(),
    val loading: Boolean = false,
    val userRefreshing: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val errors: Map<UpdateSource, SourceProblem> = emptyMap(),
    val initialized: Boolean = false,
    val unavailableOnPlay: Set<String> = emptySet(),
) {
    val counts: Map<AppSection, Int> =
        AppSection.entries.associateWith { section -> apps.count { it.belongsTo(section) } }
}
