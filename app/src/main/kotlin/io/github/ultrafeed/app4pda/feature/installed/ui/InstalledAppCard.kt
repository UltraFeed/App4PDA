package io.github.ultrafeed.app4pda.feature.installed.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.formatting.DisplayFormats
import io.github.ultrafeed.app4pda.core.images.AppImageSource
import io.github.ultrafeed.app4pda.core.images.ImageOrigin
import io.github.ultrafeed.app4pda.core.images.forumIconCacheKey
import io.github.ultrafeed.app4pda.core.ui.components.AppImage
import io.github.ultrafeed.app4pda.feature.installed.AppSection
import io.github.ultrafeed.app4pda.feature.installed.AppVersionStatus
import io.github.ultrafeed.app4pda.feature.installed.InstalledApp
import io.github.ultrafeed.app4pda.feature.installed.InstalledCardAppearance
import io.github.ultrafeed.app4pda.feature.installed.installedAppStatus

/**
 * Catalog and Hidden retain forum artwork; Home's installed lists and updates use package icons.
 */
internal fun appCardIconSource(app: InstalledApp, section: AppSection?): AppImageSource? {
    val local = app.local?.let { AppImageSource.PackageIcon(app.packageName, it.versionCode) }
    val forum =
        app.forum.iconUrl
            .takeIf {
                it.startsWith("https://") || it.startsWith("http://")
            }
            ?.let {
                AppImageSource.Url(it, ImageOrigin.FORUM)
            }
    return when (section) {
        AppSection.APPLICATIONS,
        AppSection.UNLINKED,
        AppSection.PENDING_LINKING,
        AppSection.FAVORITES,
        AppSection.UPDATES -> local ?: forum

        AppSection.HIDDEN,
        null -> forum ?: local
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun InstalledAppCard(
    app: InstalledApp,
    unavailableOnPlay: Boolean,
    onOpen: () -> Unit,
    onLongPress: (() -> Unit)?,
    appearance: InstalledCardAppearance = InstalledCardAppearance(),
    section: AppSection? = null,
) {
    val colors = MaterialTheme.colorScheme
    val status = installedAppStatus(app, appearance, unavailableOnPlay)
    Card(
        Modifier.fillMaxWidth()
            .clip(CardDefaults.shape)
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onLongPress,
                onLongClickLabel =
                    if (onLongPress != null) stringResource(R.string.app_menu_updates) else null,
            ),
        shape = CardDefaults.shape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
    ) {
        Row {
            Column(Modifier.weight(1f)) {
                AppCardHeader(app, section)

                AppCardVersions(app, unavailableOnPlay, status.localVersion)
            }
        }
    }
}

internal enum class VersionSource {
    LOCAL,
    FORUM,
    PLAY,
    RUSTORE,
}

internal data class InstalledVersionCell(
    val source: VersionSource,
    val version: String,
)

internal fun installedVersionCells(
    app: InstalledApp,
    unavailableOnPlay: Boolean,
): List<InstalledVersionCell> = buildList {
    app.local
        ?.versionName
        ?.takeIf { it.isNotBlank() }
        ?.let { add(InstalledVersionCell(VersionSource.LOCAL, it)) }
    app.forum.versionName
        .takeIf { it.isNotBlank() && !app.preferences.ignoreForum }
        ?.let { add(InstalledVersionCell(VersionSource.FORUM, it)) }
    if (!unavailableOnPlay && !app.preferences.ignoreMarket)
        app.market
            ?.versionName
            ?.takeIf { it.isNotBlank() }
            ?.let {
                add(InstalledVersionCell(VersionSource.PLAY, it))
            }
    app.ruStore
        ?.versionName
        ?.takeIf { it.isNotBlank() && !app.preferences.ignoreRuStore }
        ?.let {
            add(InstalledVersionCell(VersionSource.RUSTORE, it))
        }
}

@Composable
private fun VersionCell(
    source: VersionSource,
    version: String,
    color: Color?,
    modifier: Modifier,
) {
    ListItem(
        modifier = modifier,
        colors =
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        leadingContent = {
            if (source == VersionSource.LOCAL)
                Icon(
                    Icons.Rounded.Smartphone,
                    null,
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            else
                Image(
                    painterResource(
                        if (source == VersionSource.FORUM) R.drawable.ic_4pda_small_color
                        else if (source == VersionSource.PLAY) R.drawable.ic_market_small_color
                        else R.drawable.ic_rustore,
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
        },
        content = {
            Text(
                version,
                style = MaterialTheme.typography.bodyMedium,
                color = color ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun AppCardHeader(app: InstalledApp, section: AppSection?) {
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        run {
            val image = appCardIconSource(app, section)
            AppImage(
                image,
                if (image is AppImageSource.Url)
                    forumIconCacheKey(
                        app.forum.topicId,
                    )
                else "${app.packageName}_${app.local?.versionCode ?: app.forum.iconVersion}",
                app.name,
                Modifier.size(64.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        AppCardText(app, Modifier.weight(1f))
    }
}

@Composable
private fun AppCardVersions(
    app: InstalledApp,
    unavailableOnPlay: Boolean,
    localStatus: AppVersionStatus,
) {
    val colors = MaterialTheme.colorScheme
    val versions = installedVersionCells(app, unavailableOnPlay)
    if (versions.isNotEmpty()) {
        HorizontalDivider(color = colors.outlineVariant)
        Column(Modifier.fillMaxWidth()) {
            versions.forEachIndexed { index, cell ->
                if (index > 0) HorizontalDivider(color = colors.outlineVariant)
                VersionCell(
                    cell.source,
                    cell.version,
                    if (cell.source == VersionSource.LOCAL && localStatus != AppVersionStatus.NONE)
                        when (localStatus) {
                            AppVersionStatus.UPDATE_AVAILABLE -> colors.error
                            AppVersionStatus.INSTALLED_NEWER -> colors.tertiary
                            AppVersionStatus.NONE -> colors.onSurface
                        }
                    else null,
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AppCardText(app: InstalledApp, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                app.name,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (app.forum.updatedAt > 0)
                Text(
                    DisplayFormats.dateTime(
                        app.forum.updatedAt,
                        "dd.MM.yyyy",
                    ),
                    Modifier.padding(start = 8.dp).widthIn(max = 112.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (app.forum.description.isNotBlank())
                Text(
                    app.forum.description,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            if (app.forum.description.isBlank()) Spacer(Modifier.weight(1f))
        }
    }
}
