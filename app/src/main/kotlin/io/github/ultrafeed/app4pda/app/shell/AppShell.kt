package io.github.ultrafeed.app4pda.app.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.app.MainActivity
import io.github.ultrafeed.app4pda.app.applySystemBars
import io.github.ultrafeed.app4pda.app.navigation.AppDestination
import io.github.ultrafeed.app4pda.app.navigation.LocalCanGoBack
import io.github.ultrafeed.app4pda.app.navigation.MainNavigation
import io.github.ultrafeed.app4pda.app.navigation.MainSection
import io.github.ultrafeed.app4pda.app.navigation.isDialog
import io.github.ultrafeed.app4pda.app.navigation.rememberMainNavigation
import io.github.ultrafeed.app4pda.core.ui.theme.AppTheme
import io.github.ultrafeed.app4pda.feature.installed.data.InstalledAppsRepository
import io.github.ultrafeed.app4pda.feature.installed.data.UpdateSchedule
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppShell(activity: MainActivity) {
    val installed: InstalledAppsRepository = koinInject()
    val navigation = rememberMainNavigation(activity.initialDestination)
    val current = navigation.current
    var backInProgress by remember { mutableStateOf(false) }
    val goBack: () -> Unit = {
        if (!backInProgress) {
            if (navigation.back()) backInProgress = true else activity.finish()
        }
    }
    val appearance by activity.appearanceSettings.snapshots.collectAsStateWithLifecycle()
    AppShellLifecycle(activity, navigation, installed)
    AppTheme(
        appearance.darkTheme,
        appearance.accentColor,
        appearance.animationsEnabled,
        appearance.useSystemAccent,
    ) {
        val motion = NavigationMotion(appearance.animationsEnabled, MaterialTheme.motionScheme)
        val gallery = current.destination is AppDestination.Gallery
        val showBottom = !gallery && !WindowInsets.isImeVisible
        ApplySystemAppearance(activity, showBottom)
        Box(Modifier.fillMaxSize()) {
            val decorated =
                rememberAppEntries(
                    activity,
                    navigation,
                    installed,
                    goBack,
                    backInProgress,
                ) {
                    backInProgress = false
                }
            val entries =
                if (navigation.selected == MainSection.HOME) {
                    decorated.getValue(MainSection.HOME)
                } else {
                    listOf(decorated.getValue(MainSection.HOME).first()) +
                        decorated.getValue(navigation.selected)
                }
            MainStackDisplay(
                entries,
                navigation,
                motion,
                showBottom,
                gallery,
                backInProgress,
                goBack,
            )
            activity.dialogs.Render()
        }
        BackHandler(backInProgress) {}
    }
}

@Composable
internal fun ScreenFrame(
    onBack: () -> Unit,
    title: String?,
    subtitle: String? = null,
    topBar: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Scaffold(
            contentWindowInsets =
                WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            topBar = {
                if (topBar != null) topBar()
                else if (title != null)
                    TopAppBar(
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        title = {
                            Column {
                                Text(
                                    title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                subtitle?.takeIf(String::isNotBlank)?.let {
                                    Text(
                                        it,
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            if (LocalCanGoBack.current)
                                IconButton(
                                    onClick = onBack,
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        stringResource(R.string.back),
                                    )
                                }
                        },
                        actions = actions,
                    )
            },
        ) { padding ->
            Box(
                Modifier.fillMaxSize().padding(padding),
            ) {
                content()
            }
        }
    }
}

/** Foreground work and platform intents belong to the shell, independently of rendered entries. */
@Composable
private fun AppShellLifecycle(
    activity: MainActivity,
    navigation: MainNavigation,
    installed: InstalledAppsRepository,
) {
    LaunchedEffect(installed, activity) {
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            installed.foregroundChanged(true)
            try {
                awaitCancellation()
            } finally {
                installed.foregroundChanged(false)
            }
        }
    }
    LaunchedEffect(Unit) { UpdateSchedule.request(activity.applicationContext) }
    val activeStack = navigation.stack
    DisposableEffect(activeStack) {
        activity.navigation.stack = activeStack
        onDispose {
            if (activity.navigation.stack === activeStack) activity.navigation.stack = null
        }
    }
    LaunchedEffect(activity.navigation.pendingIntents.firstOrNull(), navigation.current.id) {
        activity.takePendingIntentDestination()?.let {
            navigation.external(it)
        }
    }
    LaunchedEffect(activity.navigation.downloadsRequested) {
        if (activity.navigation.downloadsRequested) {
            activity.navigation.downloadsRequested = false
            navigation.external(AppDestination.Downloads)
        }
    }
}

@Composable
private fun MainStackDisplay(
    entries: List<NavEntry<NavKey>>,
    navigation: MainNavigation,
    motion: NavigationMotion,
    showBottom: Boolean,
    gallery: Boolean,
    backInProgress: Boolean,
    goBack: () -> Unit,
) {
    val current = navigation.current
    Column(Modifier.fillMaxSize().imePadding()) {
        NavDisplay(
            entries = entries,
            modifier =
                Modifier.weight(1f)
                    .then(
                        if (!showBottom && !gallery) Modifier.navigationBarsPadding() else Modifier,
                    ),
            onBack = goBack,
            sceneStrategies = remember { listOf(DialogSceneStrategy()) },
            transitionSpec = { motion.sceneTransform(this, pop = false) },
            popTransitionSpec = { motion.sceneTransform(this, pop = true) },
            predictivePopTransitionSpec = { motion.transform(this, forward = false) },
        )
        if (showBottom)
            MainNavigationBar(navigation.selected) { section ->
                if (!current.destination.isDialog && !backInProgress) {
                    navigation.select(section)
                }
            }
    }
}

@Composable
private fun ApplySystemAppearance(activity: MainActivity, showBottom: Boolean) {
    val systemColors =
        screenColors(MaterialTheme.colorScheme).let {
            if (showBottom)
                it.copy(navigationBar = MaterialTheme.colorScheme.surfaceContainer.toArgb())
            else it
        }
    val surfaceColor = MaterialTheme.colorScheme.surface
    SideEffect {
        activity.applySystemBars(systemColors)
        activity.window.setBackgroundDrawable(surfaceColor.toArgb().toDrawable())
    }
}
