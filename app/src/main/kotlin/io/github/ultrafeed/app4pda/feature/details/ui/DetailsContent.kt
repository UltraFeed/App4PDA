package io.github.ultrafeed.app4pda.feature.details.ui

import android.R as AndroidR
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.formatting.DisplayFormats
import io.github.ultrafeed.app4pda.core.images.ImageOrigin
import io.github.ultrafeed.app4pda.core.network.SourceProblem
import io.github.ultrafeed.app4pda.core.ui.components.AppErrorCard
import io.github.ultrafeed.app4pda.core.ui.components.AppLoadingIndicator
import io.github.ultrafeed.app4pda.core.ui.components.SectionSwitch
import io.github.ultrafeed.app4pda.core.ui.components.SectionSwitches
import io.github.ultrafeed.app4pda.core.ui.document.DocumentContent
import io.github.ultrafeed.app4pda.core.ui.theme.AppLayout
import io.github.ultrafeed.app4pda.feature.details.AppDetails
import io.github.ultrafeed.app4pda.feature.details.AppDetailsUiState
import io.github.ultrafeed.app4pda.feature.details.DetailsFile
import io.github.ultrafeed.app4pda.feature.details.DetailsSection
import io.github.ultrafeed.app4pda.feature.details.DetailsTab

@Composable
internal fun DetailsRequestState(
    state: AppDetailsUiState,
    onRetry: () -> Unit,
    onActionRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val waitingOnStore = state.playLoading && state.tab != DetailsTab.FORUM
    if (state.loading || state.actionBusy || waitingOnStore)
        AppLoadingIndicator(Modifier.padding(AppLayout.content))
    if (state.error || state.actionError)
        AppErrorCard(
            problem =
                state.actionProblem
                    ?: SourceProblem(
                        "APP · REQUEST_FAILED",
                        stringResource(R.string.action_failed),
                    ),
            onRetry = {
                if (state.actionError) onActionRetry() else onRetry()
            },
            onDismiss = onDismiss,
            modifier = Modifier.padding(AppLayout.content),
        )
}

@Composable
internal fun DetailsMetadata(data: AppDetails, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = AppLayout.content, vertical = AppLayout.itemGap),
        verticalArrangement = Arrangement.spacedBy(AppLayout.itemGap),
    ) {
        if (data.version.isNotBlank())
            Text(data.version, style = MaterialTheme.typography.titleSmall)
        if (data.updatedAt > 0)
            Text(
                stringResource(
                    R.string.last_dt_upd,
                    DisplayFormats.dateTime(data.updatedAt, "dd.MM.yyyy, HH:mm"),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
    listOfNotNull(data.market, data.ruStore)
        .filter { it.error }
        .forEach { section ->
            section.problem?.let {
                AppErrorCard(
                    problem = it,
                    onRetry = onRetry,
                    modifier = Modifier.padding(AppLayout.content),
                )
            }
        }
}

@Composable
internal fun DetailsSelector(tabs: List<DetailsTab>, selected: Int, onSelect: (Int) -> Unit) {
    SectionSwitches(
        tabs.map {
            SectionSwitch(
                when (it) {
                    DetailsTab.FORUM -> stringResource(R.string.source_forum)
                    DetailsTab.GOOGLE_PLAY -> stringResource(R.string.source_google_play)
                    DetailsTab.RUSTORE -> stringResource(R.string.source_rustore)
                },
            )
        },
        selected,
        onSelect = onSelect,
        modifier = Modifier.padding(horizontal = AppLayout.content, vertical = AppLayout.itemGap),
    )
}

@Composable
internal fun DetailsDocument(
    section: DetailsSection?,
    origin: ImageOrigin,
    onRetry: () -> Unit,
    onOpenForum: (() -> Unit)?,
    onImages: (List<String>, Int) -> Unit,
    onLink: (String) -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        if (section == null || section.error) {
            AppErrorCard(
                problem =
                    section?.problem
                        ?: SourceProblem(
                            "SOURCE · UNAVAILABLE",
                            stringResource(R.string.no_description),
                        ),
                onRetry = onRetry,
                modifier = Modifier.padding(AppLayout.content),
            )
            onOpenForum
                ?.takeIf { section?.problem?.forumPaused != true }
                ?.let {
                    TextButton(onClick = it) {
                        Text(stringResource(R.string.forum_open_in_browser))
                    }
                }
        } else {
            section.problem?.let {
                AppErrorCard(
                    problem = it,
                    onRetry = onRetry,
                    modifier = Modifier.padding(AppLayout.content),
                )
            }
            DescriptionDocument(section, origin, onImages, onLink)
        }
    }
}

@Composable
private fun ColumnScope.DescriptionDocument(
    section: DetailsSection,
    origin: ImageOrigin,
    onImages: (List<String>, Int) -> Unit,
    onLink: (String) -> Unit,
) {
    if (section.document.isBlank() && section.screenshots.isEmpty())
        Text(stringResource(R.string.no_description), Modifier.padding(AppLayout.content))
    else
        DocumentContent(
            origin = origin,
            document = section.document,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            gallery = section.screenshots,
            galleryPreviews = section.screenshotPreviews,
            onImages = onImages,
            onLink = onLink,
        )
}

@Composable
internal fun DetailsDownloadDialog(
    file: DetailsFile,
    data: AppDetails,
    busy: Boolean,
    onDismiss: () -> Unit,
    onDownload: (DetailsFile) -> Unit,
) {
    val permitted =
        when {
            busy -> false
            !data.downloadDirectoryReady -> false
            file.market || file.ruStore -> true
            else -> data.loggedIn
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(file.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(file.details)
                if (file.market) Text(stringResource(R.string.play_anonymous))
                if (!file.market && !file.ruStore && !data.loggedIn)
                    Text(stringResource(R.string.warning_download_no_login))
                if (!data.downloadDirectoryReady)
                    Text(stringResource(R.string.downloads_cache_unavailable))
            }
        },
        confirmButton = {
            TextButton(
                enabled = permitted,
                onClick = {
                    onDismiss()
                    onDownload(file)
                },
            ) {
                Text(stringResource(R.string.action_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(AndroidR.string.cancel)) }
        },
    )
}
