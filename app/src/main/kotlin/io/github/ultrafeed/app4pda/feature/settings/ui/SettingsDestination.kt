package io.github.ultrafeed.app4pda.feature.settings.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.app.MainActivity
import io.github.ultrafeed.app4pda.app.navigation.AppDestination
import io.github.ultrafeed.app4pda.app.navigation.AppEntry
import io.github.ultrafeed.app4pda.app.navigation.LocalEntryActive
import io.github.ultrafeed.app4pda.app.navigation.OnScreenResume
import io.github.ultrafeed.app4pda.app.navigation.openCustomTab
import io.github.ultrafeed.app4pda.app.navigation.openExternal
import io.github.ultrafeed.app4pda.app.shell.ScreenFrame
import io.github.ultrafeed.app4pda.feature.installed.AppSection
import io.github.ultrafeed.app4pda.feature.installed.data.InstalledAppsRepository
import io.github.ultrafeed.app4pda.feature.settings.SettingAction
import io.github.ultrafeed.app4pda.feature.settings.SettingId
import io.github.ultrafeed.app4pda.feature.settings.SettingItem
import io.github.ultrafeed.app4pda.feature.settings.SettingKind
import io.github.ultrafeed.app4pda.feature.settings.SettingsPage
import io.github.ultrafeed.app4pda.feature.updates.PROJECT_URL
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SettingsDestination(
    activity: MainActivity,
    entry: AppEntry,
    onOpen: (AppDestination) -> Unit,
    onBack: () -> Unit,
) {
    val active = LocalEntryActive.current
    val model: SettingsViewModel = koinViewModel()
    val state by model.state.collectAsStateWithLifecycle()
    val installed: InstalledAppsRepository = koinInject()
    val apps by installed.state.collectAsStateWithLifecycle()
    val root = (entry.destination as AppDestination.Settings).page
    if (root == SettingsPage.ACCOUNTS) {
        ForumAccountsScreen(onBack) { onOpen(AppDestination.Auth(addAccount = true)) }
        return
    }
    val title = if (root == SettingsPage.ROOT) null else activity.getString(root.title)

    var backupAction by remember { mutableStateOf<SettingAction?>(null) }
    SettingsBackupActions(backupAction) { backupAction = null }

    SettingsLogShare(activity, model, active)

    OnScreenResume(model::refresh)
    ScreenFrame(
        onBack,
        title,
    ) {
        SettingsScreen(
            state,
            root,
            { item ->
                selectSetting(activity, model, item, onOpen) { backupAction = it }
            },
            model::changeDraft,
            {
                val key = model.state.value.dialog?.key
                model.saveDialog()
                if (model.state.value.dialog == null) activity.restartHint(key)
            },
            model::dismissDialog,
            model::dismissError,
            {
                val clearCache = state.cacheClearFailed
                model.dismissError()
                if (clearCache) {
                    model.edit(SettingId.CLEAR_CACHE)
                    model.saveDialog()
                } else model.refresh()
            },
            hiddenCount = apps.counts[AppSection.HIDDEN] ?: 0,
        )
        SettingsExtraDialogs(
            state,
            model.log::dismiss,
            model.log::prepare,
        )
    }
}

private fun MainActivity.restartHint(key: SettingId?) {
    if (key in setOf(SettingId.LANGUAGE, SettingId.EXTENDED_LOG))
        Toast.makeText(
                this,
                R.string.settings_need_restart,
                Toast.LENGTH_SHORT,
            )
            .show()
}

private fun performSettingsAction(
    activity: MainActivity,
    model: SettingsViewModel,
    action: SettingAction,
    onOpen: (AppDestination) -> Unit,
    onBackup: (SettingAction) -> Unit,
) {
    when (action) {
        SettingAction.HIDDEN ->
            onOpen(
                AppDestination.Apps(
                    AppSection.HIDDEN,
                ),
            )

        SettingAction.NOTIFICATION_SETTINGS -> {
            val intent =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(
                            Settings.EXTRA_APP_PACKAGE,
                            activity.packageName,
                        )
                else
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        ("package:" + activity.packageName).toUri(),
                    )
            activity.startActivity(intent)
        }

        SettingAction.GITHUB -> activity.openCustomTab(PROJECT_URL)
        SettingAction.CHECK_SELF_UPDATE -> activity.selfUpdate.check(manual = true)
        SettingAction.SEND_LOG -> model.log.confirm()
        SettingAction.CLEAR_CACHE -> model.edit(SettingId.CLEAR_CACHE)
        SettingAction.EXPORT_BACKUP,
        SettingAction.IMPORT_BACKUP -> onBackup(action)
    }
}

@Composable
private fun SettingsLogShare(activity: MainActivity, model: SettingsViewModel, active: Boolean) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentActive by rememberUpdatedState(active)
    LaunchedEffect(model, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            model.state.collect { ui ->
                if (ui.preparedLog != null && currentActive)
                    model.log.takePrepared()?.let { output ->
                        if (!output.canRead())
                            Toast.makeText(
                                    activity,
                                    R.string.action_failed,
                                    Toast.LENGTH_SHORT,
                                )
                                .show()
                        else
                            activity.openExternal(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_SUBJECT,
                                            activity.getString(R.string.log_share_subject),
                                        )
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        putExtra(
                                            Intent.EXTRA_STREAM,
                                            FileProvider.getUriForFile(
                                                activity,
                                                activity.packageName + ".fileprovider",
                                                output,
                                            ),
                                        )
                                    },
                                    activity.getString(R.string.send_via),
                                ),
                            )
                    }
            }
        }
    }
}

private fun selectSetting(
    activity: MainActivity,
    model: SettingsViewModel,
    item: SettingItem,
    onOpen: (AppDestination) -> Unit,
    onBackup: (SettingAction) -> Unit,
) {
    when (item.kind) {
        SettingKind.SCREEN -> onOpen(AppDestination.Settings(requireNotNull(item.destination)))

        SettingKind.SWITCH -> {
            model.toggle(item.key)
            if (model.state.value.dialog == null) activity.restartHint(item.key)
        }

        SettingKind.ACTION ->
            if (item.confirmation != null) model.edit(item.key)
            else
                performSettingsAction(
                    activity,
                    model,
                    requireNotNull(item.action),
                    onOpen,
                ) {
                    onBackup(it)
                }

        SettingKind.CATEGORY -> Unit
        else -> model.edit(item.key)
    }
}
