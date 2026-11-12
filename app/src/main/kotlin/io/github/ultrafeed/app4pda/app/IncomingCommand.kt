package io.github.ultrafeed.app4pda.app

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import io.github.ultrafeed.app4pda.app.navigation.AppDestination
import io.github.ultrafeed.app4pda.feature.installed.AppSection
import io.github.ultrafeed.app4pda.feature.lookup.data.AppLinks
import io.github.ultrafeed.app4pda.feature.settings.SettingsPage

internal sealed interface IncomingCommand {
    data class Navigate(val destination: AppDestination) : IncomingCommand

    data class SharedApk(val uri: Uri) : IncomingCommand

    data object Downloads : IncomingCommand

    data object None : IncomingCommand
}

internal const val ACTION_DOWNLOAD_OPEN = "io.github.ultrafeed.app4pda.intent.action.DOWNLOAD_OPEN"
private const val ACTION_PREFERENCES = "android.intent.action.PREFERENCES"

/**
 * Classifies input only; startup and subsequent intents deliberately apply commands differently.
 */
internal fun classifyIntent(input: Intent): IncomingCommand =
    when {
        input.getBooleanExtra("open_updates", false) ->
            IncomingCommand.Navigate(AppDestination.Apps(AppSection.UPDATES))
        input.action == ACTION_PREFERENCES ->
            IncomingCommand.Navigate(
                AppDestination.Settings(
                    SettingsPage.entries.firstOrNull {
                        it.name == input.data?.toString()
                    } ?: SettingsPage.ROOT,
                ),
            )
        else -> classifyContent(input)
    }

private fun classifyContent(input: Intent): IncomingCommand {
    val shared = sharedApkUri(input)
    val store =
        when (input.action) {
            Intent.ACTION_VIEW -> input.data?.toString()
            Intent.ACTION_SEND ->
                if (input.type == "text/plain") input.getStringExtra(Intent.EXTRA_TEXT) else null
            else -> null
        }?.let(AppLinks::storeFromUrl)
    return when {
        shared != null -> IncomingCommand.SharedApk(shared)
        store != null ->
            IncomingCommand.Navigate(
                AppDestination.Details(0, store.packageName, initialSource = store.source),
            )
        input.action == Intent.ACTION_SEND -> IncomingCommand.Navigate(AppDestination.Lookup(null))
        input.action == Intent.ACTION_SEARCH ->
            IncomingCommand.Navigate(
                AppDestination.Catalog(
                    filter = input.getStringExtra("query"),
                    linked = input.getBundleExtra("app_data")?.getString("linked_package"),
                ),
            )
        input.action == Intent.ACTION_VIEW -> IncomingCommand.None
        input.action == ACTION_DOWNLOAD_OPEN -> IncomingCommand.Downloads
        else -> IncomingCommand.None
    }
}

private fun sharedApkUri(input: Intent): Uri? {
    if (input.action != Intent.ACTION_SEND || input.type == "text/plain") return null
    return IntentCompat.getParcelableExtra(input, Intent.EXTRA_STREAM, Uri::class.java)
        ?: input.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
}
