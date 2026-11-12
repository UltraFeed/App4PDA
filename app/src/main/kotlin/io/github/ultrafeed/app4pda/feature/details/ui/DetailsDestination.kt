package io.github.ultrafeed.app4pda.feature.details.ui

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.app.MainActivity
import io.github.ultrafeed.app4pda.app.navigation.AppDestination
import io.github.ultrafeed.app4pda.app.navigation.AppEntry
import io.github.ultrafeed.app4pda.app.navigation.LocalEntryActive
import io.github.ultrafeed.app4pda.app.navigation.openCustomTab
import io.github.ultrafeed.app4pda.app.navigation.openExternal
import io.github.ultrafeed.app4pda.app.shell.ScreenFrame
import io.github.ultrafeed.app4pda.core.images.ImageOrigin
import io.github.ultrafeed.app4pda.core.packages.PackageInspector
import io.github.ultrafeed.app4pda.data.forum.ForumPostLink
import io.github.ultrafeed.app4pda.data.forum.ForumUrls
import io.github.ultrafeed.app4pda.feature.details.DetailsTab
import io.github.ultrafeed.app4pda.feature.downloads.data.DownloadController
import io.github.ultrafeed.app4pda.feature.installed.data.InstalledAppsRepository
import io.github.ultrafeed.app4pda.feature.lookup.data.AppLinks
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun DetailsDestination(
    activity: MainActivity,
    entry: AppEntry,
    onOpen: (AppDestination) -> Unit,
    onBack: () -> Unit,
) {
    val active = LocalEntryActive.current
    val language = LocalConfiguration.current.locales.toLanguageTags()
    val args = entry.destination as AppDestination.Details
    val downloadsController: DownloadController = koinInject()
    val model: AppDetailsViewModel =
        koinViewModel() {
            parametersOf(
                args.topic,
                args.packageName.orEmpty(),
                args.initialSource ?: DetailsTab.FORUM,
            )
        }
    val ui by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(model, language, active) {
        if (active) model.refreshPlayLanguage()
    }
    val linkedPackage = args.linked ?: args.packageName?.takeIf { it.isNotBlank() }
    val installed: InstalledAppsRepository = koinInject()
    val installedState by installed.state.collectAsStateWithLifecycle()
    val target = installedState.apps.firstOrNull { it.packageName == linkedPackage && it.installed }
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    val onAction =
        rememberDetailsActions(activity, model, linkedPackage, onOpen) { showDiagnostics = true }

    ScreenFrame(
        onBack,
        ui.data?.title ?: activity.getString(R.string.catalog),
        actions = {
            AppDetailsToolbarActions(
                ui,
                target,
                args.topic,
                model::favorite,
                onAction,
            )
        },
    ) {
        AppDetailsScreen(
            ui,
            model::refresh,
            model::selectTab,
            onAction,
            { url, origin -> args.openDetailsLink(activity, model, url, origin, onOpen) },
            { origin, images, page ->
                onOpen(AppDestination.Gallery(images, origin, page))
            },
            { file ->
                downloadsController.submissions.submit(file, ui.data?.packageName.orEmpty())
            },
            { model.consumeResult(DetailsResult.ERROR) },
            model::retryError,
            onFileConsumed = { model.consumeResult(DetailsResult.FILE) },
            initialSource = args.initialSource,
        )
        if (showDiagnostics)
            ui.data?.let { data ->
                AppDetailsDiagnostics(data) { showDiagnostics = false }
            }
    }
}

@Composable
private fun rememberDetailsActions(
    activity: MainActivity,
    model: AppDetailsViewModel,
    linkedPackage: String?,
    onOpen: (AppDestination) -> Unit,
    onDiagnostics: () -> Unit,
): (DetailsAction) -> Unit {
    val scope = rememberCoroutineScope()
    val packages: PackageInspector = koinInject()
    val active by rememberUpdatedState(LocalEntryActive.current)
    var searching by remember { mutableStateOf(false) }
    return { action ->
        val data = model.state.value.data
        if (action == DetailsAction.BIND && (data?.topicId ?: 0) <= 0 && linkedPackage != null) {
            if (!searching)
                scope.launch {
                    searching = true
                    try {
                        val query = packages.bindingSearchName(linkedPackage, data?.title.orEmpty())
                        if (active)
                            onOpen(AppDestination.Catalog(filter = query, linked = linkedPackage))
                    } finally {
                        searching = false
                    }
                }
        } else performDetailsAction(activity, model, linkedPackage, action, onDiagnostics)
    }
}

private fun AppDestination.Details.openDetailsLink(
    activity: MainActivity,
    model: AppDetailsViewModel,
    url: String,
    origin: ImageOrigin,
    onOpen: (AppDestination) -> Unit,
) {
    when (val link = ForumPostLink.parse(url, ForumUrls.topic(topic), topic)) {
        is ForumPostLink.Attachment -> {
            model.openAttachment(link.url)
        }

        is ForumPostLink.Post ->
            onOpen(
                AppDestination.Post(
                    link.address.topicId,
                    link.address.postId,
                    model.state.value.data?.title.orEmpty(),
                ),
            )

        is ForumPostLink.Topic -> onOpen(AppDestination.Details(link.topicId, null))
        is ForumPostLink.Image -> onOpen(AppDestination.Gallery(listOf(link.url), origin))
        is ForumPostLink.External ->
            activity.openExternal(Intent(Intent.ACTION_VIEW, link.url.toUri()))
        null -> Unit
    }
}

private fun performDetailsAction(
    activity: MainActivity,
    model: AppDetailsViewModel,
    linkedPackage: String?,
    action: DetailsAction,
    onDiagnostics: () -> Unit,
) {
    val data = model.state.value.data ?: return
    when (action) {
        DetailsAction.FORUM -> activity.openCustomTab(ForumUrls.topic(data.topicId))
        DetailsAction.MARKET ->
            activity.openExternal(
                AppLinks.playStoreIntent(
                    data.packageName,
                ),
            )

        DetailsAction.RUSTORE ->
            activity.openCustomTab("https://www.rustore.ru/catalog/app/${data.packageName}")

        DetailsAction.BIND -> linkedPackage?.let { model.binding(it, DetailsBinding.BIND) }
        DetailsAction.UNLINK -> linkedPackage?.let { model.binding(it, DetailsBinding.UNLINK) }
        DetailsAction.DEBUG -> onDiagnostics()
    }
}
