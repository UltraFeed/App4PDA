package io.github.ultrafeed.app4pda.feature.details.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.images.AppImageSource
import io.github.ultrafeed.app4pda.core.images.ImageOrigin
import io.github.ultrafeed.app4pda.core.ui.components.AppErrorCard
import io.github.ultrafeed.app4pda.core.ui.components.AppImage
import io.github.ultrafeed.app4pda.core.ui.document.DocumentContent
import io.github.ultrafeed.app4pda.core.ui.theme.AppLayout
import io.github.ultrafeed.app4pda.feature.details.DetailsFile
import io.github.ultrafeed.app4pda.feature.details.DetailsSection

@Composable
internal fun StoreDocument(
    section: DetailsSection?,
    file: DetailsFile?,
    origin: ImageOrigin,
    busy: Boolean,
    onRetry: () -> Unit,
    onImages: (List<String>, Int) -> Unit,
    onLink: (String) -> Unit,
    onFile: (DetailsFile) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppLayout.itemGap),
    ) {
        section?.problem?.let { AppErrorCard(it, onRetry = onRetry) }
        if (section == null || section.error) return@Column
        if (section.screenshots.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(AppLayout.itemGap),
                contentPadding = AppLayout.listPadding,
            ) {
                itemsIndexed(section.screenshots) { index, original ->
                    AppImage(
                        AppImageSource.Url(
                            section.screenshotPreviews.getOrNull(index) ?: original,
                            origin,
                        ),
                        null,
                        section.title,
                        Modifier.height(192.dp).width(112.dp).clickable {
                            onImages(section.screenshots, index)
                        },
                    )
                }
            }
        }
        file?.let { StoreFileCard(it, busy, onFile) }
        if (section.document.isBlank())
            Text(stringResource(R.string.no_description), Modifier.padding(AppLayout.content))
        else DocumentContent(section.document, origin, fitContent = true, onLink = onLink)
    }
}

@Composable
private fun StoreFileCard(file: DetailsFile, busy: Boolean, onFile: (DetailsFile) -> Unit) {
    Card(
        onClick = { onFile(file) },
        enabled = !busy,
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppLayout.content),
    ) {
        val secondaryColor =
            if (busy) LocalContentColor.current else MaterialTheme.colorScheme.onSurfaceVariant
        Column(
            Modifier.padding(AppLayout.content),
            verticalArrangement = Arrangement.spacedBy(AppLayout.itemGap),
        ) {
            Text(file.name, style = MaterialTheme.typography.titleMediumEmphasized)
            if (file.packageName.isNotBlank())
                Text(
                    file.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor,
                )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppLayout.content),
                verticalArrangement = Arrangement.spacedBy(AppLayout.itemGap),
            ) {
                if (file.version.isNotBlank())
                    Text(
                        file.version,
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryColor,
                    )
                if (file.size.isNotBlank())
                    Text(
                        file.size,
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryColor,
                    )
            }
        }
    }
}
