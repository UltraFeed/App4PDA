package io.github.ultrafeed.app4pda.feature.reports.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.core.content.pm.PackageInfoCompat
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.AppPolicy
import io.github.ultrafeed.app4pda.core.formatting.DisplayFormats
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

enum class CrashCopyResult {
    COPIED,
    UNAVAILABLE,
    TOO_LARGE,
    FAILED,
}

class CrashReports(
    private val context: Context,
    val cache: CrashReportCache = CrashReportCache(context.cacheDir),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val writeClipboard: (ClipData) -> Unit = { clip ->
        checkNotNull(context.getSystemService(ClipboardManager::class.java)).setPrimaryClip(clip)
    },
) {
    private val handler = Handler(Looper.getMainLooper())
    private val cleanup = Runnable { scheduleCleanup() }

    private val logDirectory: File
        get() =
            File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: File(
                        context.filesDir,
                        "downloads",
                    ),
                "logs",
            )

    suspend fun prepareSessionLog(): File =
        runInterruptible(ioDispatcher) {
            val output =
                File(
                    logDirectory,
                    "logcat_" +
                        DisplayFormats.date(
                            Date(),
                            "yyyy-MM-dd_HH-mm-ss",
                        ) +
                        ".txt",
                )
            check(output.parentFile?.isDirectory == true || output.parentFile?.mkdirs() == true)
            val process =
                ProcessBuilder(
                        "logcat",
                        "--pid=${android.os.Process.myPid()}",
                        "-v",
                        "time",
                        "-d",
                    )
                    .start()
            var completed = false
            try {
                process.inputStream.use { input -> output.outputStream().use(input::copyTo) }
                check(process.waitFor() == 0) { "Unable to read logcat" }
                completed = true
                output
            } finally {
                if (!completed) output.delete()
                process.destroy()
            }
        }

    fun record(thread: Thread, failure: Throwable) {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = PackageInfoCompat.getLongVersionCode(info)
        val trace = StringWriter().also { failure.printStackTrace(PrintWriter(it)) }.toString()
        val text =
            "Time: ${Date()}\nApp: ${context.packageName} ${info.versionName} ($versionCode)\nAndroid: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}\nThread: ${thread.name}\n\n$trace"
        val report =
            try {
                cache.save(text)
            } catch (_: Exception) {
                null
            }
        if (copyText(text) == CrashCopyResult.COPIED && report != null) cache.markCopied(report.id)
    }

    fun copy(id: String): CrashCopyResult {
        val report = cache.read(id) ?: return CrashCopyResult.UNAVAILABLE
        val result = copyText(report.text)
        if (result == CrashCopyResult.COPIED) {
            try {
                cache.markCopied(id)
            } catch (_: Exception) {
                /* Clipboard has already accepted the text. */
            }
        }
        return result
    }

    private fun copyText(text: String): CrashCopyResult {
        // Leave Binder headroom; never silently truncate a report to make it fit.
        if (text.toByteArray(Charsets.UTF_8).size > MAX_CLIPBOARD_BYTES)
            return CrashCopyResult.TOO_LARGE
        return try {
            val clip = ClipData.newPlainText(context.getString(R.string.crash_clip_label), text)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                clip.description.extras =
                    PersistableBundle().apply {
                        putBoolean("android.content.extra.IS_SENSITIVE", true)
                    }
            writeClipboard(clip)
            CrashCopyResult.COPIED
        } catch (_: Exception) {
            CrashCopyResult.FAILED
        }
    }

    fun onForeground(): Pair<String, CrashCopyResult?>? {
        scheduleCleanup()
        val report =
            try {
                cache.claimForegroundReport()
            } catch (_: Exception) {
                null
            } ?: return null
        // A previously copied report is presented once, without replacing the user's clipboard.
        return report.id to if (report.copied) null else copy(report.id)
    }

    fun scheduleCleanup() {
        handler.removeCallbacks(cleanup)
        cache.read()?.let {
            handler.postDelayed(
                cleanup,
                (it.createdAt + AppPolicy.CACHE_TTL_MILLIS - System.currentTimeMillis())
                    .coerceAtLeast(
                        1,
                    ),
            )
        }
    }

    fun close() {
        handler.removeCallbacks(cleanup)
    }
}

// Keep report clipboard payload below Binder's transaction budget.
private const val MAX_CLIPBOARD_BYTES = 256 * 1024
