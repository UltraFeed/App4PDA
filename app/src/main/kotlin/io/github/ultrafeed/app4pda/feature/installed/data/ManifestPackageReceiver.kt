package io.github.ultrafeed.app4pda.feature.installed.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.ultrafeed.app4pda.data.preferences.AppSettings
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

class ManifestPackageReceiver : BroadcastReceiver(), KoinComponent {
    private val settings: AppSettings by inject()
    private val repository: InstalledAppsRepository by inject()

    private val ioDispatcher: CoroutineDispatcher by inject(named("io"))

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.data?.schemeSpecificPart == context.packageName) return
        if (
            intent.action == Intent.ACTION_PACKAGE_REMOVED &&
                intent.getBooleanExtra(
                    Intent.EXTRA_REPLACING,
                    false,
                )
        )
            return
        val pending = goAsync()
        CoroutineScope(ioDispatcher).launch {
            try {
                settings.awaitReady()
                repository.packageChanged().join()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                logSettingsFailure(error)
            } catch (error: IllegalStateException) {
                logSettingsFailure(error)
            } catch (error: IllegalArgumentException) {
                logSettingsFailure(error)
            } finally {
                pending.finish()
            }
        }
    }

    private fun logSettingsFailure(error: Exception) {
        Log.e("PackageReceiver", "Cannot read settings for package refresh", error)
    }
}
