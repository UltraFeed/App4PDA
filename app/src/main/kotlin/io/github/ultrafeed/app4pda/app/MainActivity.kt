package io.github.ultrafeed.app4pda.app

import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewTreeObserver
import android.view.Window
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.os.BundleCompat
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.app.navigation.AppDestination
import io.github.ultrafeed.app4pda.app.shell.AppShell
import io.github.ultrafeed.app4pda.app.shell.MainDialogsState
import io.github.ultrafeed.app4pda.app.shell.SettingsGate
import io.github.ultrafeed.app4pda.app.shell.ShellNavigationState
import io.github.ultrafeed.app4pda.core.packages.PackageInspector
import io.github.ultrafeed.app4pda.core.ui.components.AppLoadingIndicator
import io.github.ultrafeed.app4pda.data.preferences.AppSettings
import io.github.ultrafeed.app4pda.data.preferences.SettingsLoad
import io.github.ultrafeed.app4pda.feature.downloads.data.DownloadController
import io.github.ultrafeed.app4pda.feature.settings.data.SettingsRepository
import io.github.ultrafeed.app4pda.feature.updates.SelfUpdate
import io.github.ultrafeed.app4pda.feature.updates.SelfUpdateDialogs
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    internal val dialogs by lazy { ActivityDialogs(this, appSettings, downloads) }
    internal val appearanceSettings: SettingsRepository by inject()

    internal val appSettings: AppSettings by inject()
    private val packages: PackageInspector by inject()
    private val downloads: DownloadController by inject()
    internal val selfUpdate: SelfUpdate by inject()
    internal val navigation = ShellNavigationState()
    internal lateinit var initialDestination: AppDestination
        private set

    override val defaultViewModelCreationExtras: CreationExtras
        get() =
            MutableCreationExtras(super.defaultViewModelCreationExtras).apply {
                // External commands are consumed by typed navigation, not defaults for every
                // screen.
                this[DEFAULT_ARGS_KEY] = Bundle.EMPTY
            }

    private var startupIntent: Intent? = null

    override fun getResources(): Resources {
        val fallback = super.getResources()
        return (application as? App4PdaApplication)?.localizedResources(baseContext, fallback)
            ?: fallback
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val state = savedInstanceState
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(state)
        enableEdgeToEdge()
        restoreCommands(state)
        var startupFrameReady = false
        val startupView = window.decorView
        startupView.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!startupFrameReady) return false
                    startupView.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            splashScreen.setOnExitAnimationListener { it.remove() }
        setContent {
            val loadState by appSettings.state.collectAsState()
            SideEffect {
                startupFrameReady = loadState !is SettingsLoad.Loading
            }
            SettingsGate {
                LaunchedEffect(downloads) {
                    downloads.submissions.resultsFlow.collect { accepted ->
                        if (accepted) dialogs.downloadQueued()
                        else
                            Toast.makeText(
                                    this@MainActivity,
                                    R.string.download_request_failed,
                                    Toast.LENGTH_LONG,
                                )
                                .show()
                        dialogs.finishDownloadEntry()
                    }
                }
                var destinationReady by remember {
                    mutableStateOf(::initialDestination.isInitialized)
                }
                LaunchedEffect(Unit) {
                    if (!::initialDestination.isInitialized) {
                        initialDestination = initialIntentDestination(checkNotNull(startupIntent))
                        startupIntent = null
                        setIntent(Intent(Intent.ACTION_MAIN).setComponent(componentName))
                    }
                    destinationReady = true
                    dialogs.checkWebViewAvailable()
                }
                if (destinationReady) {
                    AppShell(this)
                    if (selfUpdate.enabled) SelfUpdateDialogs(this, selfUpdate)
                } else
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppLoadingIndicator()
                    }
            }
        }
    }

    private fun restoreCommands(state: Bundle?) {
        dialogs.state =
            state?.getString("main_dialogs")?.let { Json.decodeFromString<MainDialogsState>(it) }
                ?: MainDialogsState()

        val restoredDestination =
            state?.getString("initial_destination")?.let {
                Json.decodeFromString<AppDestination>(it)
            }
        if (restoredDestination != null) initialDestination = restoredDestination
        startupIntent =
            if (!::initialDestination.isInitialized) {
                state?.let {
                    BundleCompat.getParcelable(
                        it,
                        "startup_intent",
                        Intent::class.java,
                    )
                } ?: Intent(intent)
            } else null
        navigation.pendingIntents.addAll(
            state
                ?.let {
                    BundleCompat.getParcelableArrayList(
                        it,
                        "pending_intents",
                        Intent::class.java,
                    )
                }
                .orEmpty(),
        )
        val externalLaunch =
            intent.action != Intent.ACTION_MAIN &&
                intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0
        if (restoredDestination != null && externalLaunch)
            navigation.pendingIntents.add(Intent(intent))
        navigation.downloadsRequested =
            state?.getBoolean("downloads_requested")
                ?: (state == null && intent.action == ACTION_DOWNLOAD_OPEN)
    }

    private suspend fun initialIntentDestination(input: Intent): AppDestination =
        when (val command = classifyIntent(input)) {
            is IncomingCommand.Navigate -> command.destination
            is IncomingCommand.SharedApk -> sharedApkDestination(command.uri) ?: AppDestination.Home
            IncomingCommand.Downloads -> {
                navigation.downloadsRequested = true
                AppDestination.Home
            }

            IncomingCommand.None -> AppDestination.Home
        }

    internal suspend fun takePendingIntentDestination(): AppDestination? {
        if (navigation.pendingIntents.isEmpty()) return null
        return when (val command = classifyIntent(navigation.pendingIntents.first())) {
            is IncomingCommand.Navigate -> command.destination
            is IncomingCommand.SharedApk -> sharedApkDestination(command.uri)
            IncomingCommand.Downloads -> {
                navigation.downloadsRequested = true
                null
            }

            IncomingCommand.None -> null
        }.also { navigation.pendingIntents.removeAt(0) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        enqueueIncomingIntent(intent)
        setIntent(Intent(Intent.ACTION_MAIN).setComponent(componentName))
    }

    /** Queues a copy so a later external command cannot overwrite deferred startup routing. */
    internal fun enqueueIncomingIntent(intent: Intent) {
        navigation.pendingIntents.add(Intent(intent))
    }

    private suspend fun sharedApkDestination(uri: Uri): AppDestination? =
        try {
            AppDestination.Lookup(packages.artifacts.sharedApkPackage(uri))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Toast.makeText(
                    this,
                    R.string.action_failed,
                    Toast.LENGTH_LONG,
                )
                .show()
            null
        }

    override fun onResume() {
        super.onResume()
        (application as App4PdaApplication).onForeground(this)
        if (appSettings.state.value is SettingsLoad.Ready) appearanceSettings.refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val state = outState
        state.putString("main_dialogs", Json.encodeToString(dialogs.state))
        if (::initialDestination.isInitialized)
            state.putString("initial_destination", Json.encodeToString(initialDestination))
        state.putParcelable("startup_intent", startupIntent)
        state.putParcelableArrayList("pending_intents", ArrayList(navigation.pendingIntents))
        state.putBoolean("downloads_requested", navigation.downloadsRequested)
        super.onSaveInstanceState(state)
    }

    override fun onDestroy() {
        navigation.detach()
        super.onDestroy()
    }
}
