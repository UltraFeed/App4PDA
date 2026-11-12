package io.github.ultrafeed.app4pda.feature.installed.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.app.MainActivity
import io.github.ultrafeed.app4pda.core.AppPolicy
import io.github.ultrafeed.app4pda.data.database.NotifiedRelease
import io.github.ultrafeed.app4pda.data.database.UpdateNotificationDao
import io.github.ultrafeed.app4pda.data.forum.ForumApiException
import io.github.ultrafeed.app4pda.data.forum.ForumFailure
import io.github.ultrafeed.app4pda.data.preferences.AppSettings
import io.github.ultrafeed.app4pda.data.preferences.SettingsDefaults
import io.github.ultrafeed.app4pda.data.preferences.SettingsValues
import io.github.ultrafeed.app4pda.feature.installed.InstalledApp
import io.github.ultrafeed.app4pda.feature.recent.data.ForumDigest
import io.ktor.http.HttpStatusCode
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class UpdateCheckResult {
    COMPLETED,
    PARTIAL,
    USER_VERIFICATION_REQUIRED,
    TEMPORARY_FAILURE,
}

class UpdateWorker(
    context: Context,
    parameters: WorkerParameters,
    private val repository: InstalledAppsRepository,
    private val settings: AppSettings,
    private val digest: ForumDigest,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result =
        try {
            settings.awaitReady()
            when (repository.checkInBackground()) {
                UpdateCheckResult.USER_VERIFICATION_REQUIRED -> Result.success()
                UpdateCheckResult.PARTIAL,
                UpdateCheckResult.TEMPORARY_FAILURE -> Result.retry()
                UpdateCheckResult.COMPLETED -> refreshDigest()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            Result.retry()
        }

    private suspend fun refreshDigest(): Result =
        try {
            val groups = digest.load(userInitiated = false)
            for (topic in groups.flatMap { it.entries }.map { it.topicId }.distinct()) digest
                .preview(
                    topic,
                )
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ForumApiException) {
            if (
                failure.reason == ForumFailure.VERIFICATION_REQUIRED ||
                    failure.statusCode == HttpStatusCode.Forbidden.value
            )
                Result.success()
            else Result.retry()
        } catch (_: IOException) {
            Result.retry()
        }
}

object UpdateSchedule {
    fun configure(context: Context, settings: SettingsValues) {
        val manager = WorkManager.getInstance(context)
        if (!settings.getBoolean("background_updates")) {
            manager.cancelUniqueWork("local-updates")
            return
        }
        val minutes =
            settings
                .getString("background_update_interval")
                .toLongOrNull()
                ?.coerceAtLeast(AppPolicy.UPDATE_MIN_INTERVAL_MINUTES)
                ?: SettingsDefaults.strings.getValue("background_update_interval").toLong()
        val constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request =
            PeriodicWorkRequestBuilder<UpdateWorker>(minutes, TimeUnit.MINUTES)
                .setConstraints(
                    constraints,
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    AppPolicy.UPDATE_BACKOFF_MINUTES,
                    TimeUnit.MINUTES,
                )
                .build()
        manager.enqueueUniquePeriodicWork(
            "local-updates",
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun request(context: Context) {
        val constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "catalog-discovery",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<UpdateWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        AppPolicy.UPDATE_BACKOFF_MINUTES,
                        TimeUnit.MINUTES,
                    )
                    .build(),
            )
    }
}

object LocalUpdateNotifications {
    private val lock = Mutex()
    const val ID: Int = 21590
    private const val CHANNEL = "local-updates"
    private const val SUMMARY_APPS = 3
    private const val SUMMARY_CHARACTERS = 512
    private const val EXPANDED_APPS = 6

    suspend fun publish(
        context: Context,
        apps: List<InstalledApp>,
        settings: SettingsValues,
        dao: UpdateNotificationDao,
    ): Unit = lock.withLock {
        if (!settings.getBoolean("notify_enable")) return@withLock
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
        )
            return@withLock
        val manager = context.getSystemService(NotificationManager::class.java)
        val updates = apps.filter { it.visibleUpdate }
        val notified =
            dao.notifiedForPackages(updates.map { it.packageName }).associateBy { it.packageName }
        val changed = updates.filter { it.hasNewRelease(notified[it.packageName]) }
        if (changed.isEmpty()) return@withLock
        showUpdates(context, manager, changed)
        dao.saveNotified(
            changed.map { it.notifiedRelease(notified[it.packageName]) },
        )
    }

    private fun InstalledApp.hasNewRelease(
        old: NotifiedRelease?,
    ): Boolean {
        return (forumUpdate &&
            !preferences.ignoreForum &&
            old?.forumVersion != forum.versionName) ||
            (marketUpdate && !preferences.ignoreMarket && old?.playCode != market?.versionCode) ||
            (ruStoreUpdate &&
                !preferences.ignoreRuStore &&
                old?.ruStoreCode != ruStore?.versionCode)
    }

    private fun InstalledApp.notifiedRelease(
        old: NotifiedRelease?,
    ): NotifiedRelease {

        return NotifiedRelease(
            packageName,
            if (forumUpdate && !preferences.ignoreForum) forum.versionName
            else old?.forumVersion.orEmpty(),
            if (marketUpdate && !preferences.ignoreMarket) market?.versionCode ?: 0
            else old?.playCode ?: 0,
            if (ruStoreUpdate && !preferences.ignoreRuStore) ruStore?.versionCode ?: 0
            else old?.ruStoreCode ?: 0,
        )
    }

    private fun showUpdates(
        context: Context,
        manager: NotificationManager,
        changed: List<InstalledApp>,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.available_updates),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            manager.createNotificationChannel(channel)
        }
        val intent =
            PendingIntent.getActivity(
                context,
                ID,
                Intent(context, MainActivity::class.java).putExtra("open_updates", true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        manager.notify(
            ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.available_updates))
                .setContentText(
                    changed.take(SUMMARY_APPS).joinToString { it.name }.take(SUMMARY_CHARACTERS),
                )
                .setStyle(
                    NotificationCompat.InboxStyle().also { style ->
                        changed.take(EXPANDED_APPS).forEach { style.addLine(it.name) }
                        style.setSummaryText(changed.size.toString())
                    },
                )
                .setNumber(changed.size)
                .setContentIntent(intent)
                .setAutoCancel(true)
                .build(),
        )
    }
}
