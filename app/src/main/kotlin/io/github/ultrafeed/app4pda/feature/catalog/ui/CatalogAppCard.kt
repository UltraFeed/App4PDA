package io.github.ultrafeed.app4pda.feature.catalog.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.feature.catalog.CatalogApp
import io.github.ultrafeed.app4pda.feature.installed.AppPreferences
import io.github.ultrafeed.app4pda.feature.installed.ForumApp
import io.github.ultrafeed.app4pda.feature.installed.InstalledApp
import io.github.ultrafeed.app4pda.feature.installed.InstalledCardAppearance
import io.github.ultrafeed.app4pda.feature.installed.ui.InstalledAppCard

@Composable
internal fun CatalogAppCard(
    app: CatalogApp,
    installed: List<InstalledApp>,
    onOpen: () -> Unit,
    appearance: InstalledCardAppearance,
) {
    val local = installed.firstOrNull { it.forum.topicId == app.topicId && it.local != null }
    val card =
        InstalledApp(
            local?.packageName ?: app.packageName,
            local?.local?.copy(name = app.name),
            ForumApp(
                app.topicId,
                app.name,
                app.version.ifBlank { local?.forum?.versionName.orEmpty() },
                if (app.requiresAndroidConfirmation) stringResource(R.string.forum_check_android)
                else app.description.ifBlank { local?.forum?.description.orEmpty() },
                app.updatedAt.takeIf { it > 0 } ?: local?.forum?.updatedAt ?: 0,
                iconUrl = app.icon.ifBlank { local?.forum?.iconUrl.orEmpty() },
                iconVersion = app.iconVersion.toLong(),
            ),
            local?.preferences?.copy(favorite = app.favorite)
                ?: AppPreferences(favorite = app.favorite),
            local?.market,
            entryKey = "topic:${app.topicId}",
            ruStore = local?.ruStore,
        )
    InstalledAppCard(
        card,
        false,
        onOpen,
        null,
        appearance,
    )
}
