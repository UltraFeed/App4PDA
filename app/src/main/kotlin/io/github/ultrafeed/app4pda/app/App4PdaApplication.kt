package io.github.ultrafeed.app4pda.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.StrictMode
import android.widget.Toast
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.app.di.dataModule
import io.github.ultrafeed.app4pda.app.di.infrastructureModule
import io.github.ultrafeed.app4pda.app.di.screenModule
import io.github.ultrafeed.app4pda.core.images.AppImages
import io.github.ultrafeed.app4pda.data.preferences.AppSettings
import io.github.ultrafeed.app4pda.data.preferences.SettingsLoad
import io.github.ultrafeed.app4pda.data.preferences.withAppLanguage
import io.github.ultrafeed.app4pda.feature.downloads.data.DownloadsRepository
import io.github.ultrafeed.app4pda.feature.installed.data.UpdateSchedule
import io.github.ultrafeed.app4pda.feature.reports.data.CrashCopyResult
import io.github.ultrafeed.app4pda.feature.reports.data.CrashReports
import io.github.ultrafeed.app4pda.feature.reports.ui.ReportActivity
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

class App4PdaApplication : Application() {

    val images: AppImages by inject()
    private val settings: AppSettings by inject()
    private val downloads: DownloadsRepository by inject()

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadedSettings: AppSettings? = null

    override fun getResources(): Resources = localizedResources(baseContext, super.getResources())

    internal fun localizedResources(
        context: Context,
        fallback: Resources,
    ): Resources {
        val language = (loadedSettings?.state?.value as? SettingsLoad.Ready)?.settings?.language
        return if (language == null || language == "default") fallback
        else context.withAppLanguage(language).resources
    }

    private var crashHandler: CrashReportExceptionHandler? = null
    val crashReports: CrashReports by inject()

    fun onForeground(activity: Activity) {
        val result = crashReports.onForeground() ?: return
        if (
            result.second == CrashCopyResult.COPIED &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        )
            Toast.makeText(
                    activity,
                    R.string.crash_copied,
                    Toast.LENGTH_LONG,
                )
                .show()
        if (activity !is ReportActivity)
            activity.startActivity(
                Intent(activity, ReportActivity::class.java)
                    .putExtra(
                        ReportActivity.REPORT_ID,
                        result.first,
                    ),
            )
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder().build(),
            )
        startKoin {
            androidContext(this@App4PdaApplication)
            modules(infrastructureModule, dataModule, screenModule)
            workManagerFactory()
        }
        crashReports.scheduleCleanup()
        crashHandler =
            CrashReportExceptionHandler(
                    Thread.getDefaultUncaughtExceptionHandler(),
                    crashReports::record,
                ) {
                    exitProcess(2)
                }
                .also(Thread::setDefaultUncaughtExceptionHandler)
        loadedSettings = settings
        startupScope.launch {
            val values = settings.changes.first()
            UpdateSchedule.configure(
                this@App4PdaApplication,
                values,
            )
            downloads.refresh()
        }
    }

    /** Explicit shutdown is also used by tests; Android process death does not call onTerminate. */
    private fun closeProcessResources() {
        startupScope.cancel()
        crashHandler?.let {
            if (Thread.getDefaultUncaughtExceptionHandler() === it)
                Thread.setDefaultUncaughtExceptionHandler(
                    it.previous,
                )
        }
        crashHandler = null
        stopKoin()
    }

    override fun onTerminate() {
        closeProcessResources()
        super.onTerminate()
    }
}

internal class CrashReportExceptionHandler(
    val previous: Thread.UncaughtExceptionHandler?,
    private val report: (Thread, Throwable) -> Unit,
    private val terminate: () -> Unit,
) : Thread.UncaughtExceptionHandler {
    private val handling = AtomicBoolean(false)

    override fun uncaughtException(thread: Thread, failure: Throwable) {
        if (!handling.compareAndSet(false, true)) return
        try {
            report(thread, failure)
        } catch (_: Throwable) {
            /* Reporting must not prevent delegation. */
        }
        if (previous != null && previous !== this) previous.uncaughtException(thread, failure)
        else terminate()
    }
}
