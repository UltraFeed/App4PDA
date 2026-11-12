package io.github.ultrafeed.app4pda.feature.reports.data

import android.util.AtomicFile
import androidx.core.util.writeBytes
import io.github.ultrafeed.app4pda.core.AppPolicy
import java.io.File
import java.io.IOException
import java.util.UUID
import org.json.JSONException
import org.json.JSONObject

data class CachedCrashReport(
    val id: String,
    val createdAt: Long,
    val text: String,
    val copied: Boolean = false,
    val foregroundHandled: Boolean = false,
)

/** One process-owned cache. Neither the report nor its delivery state enters preferences. */
class CrashReportCache(cacheDir: File, private val now: () -> Long = System::currentTimeMillis) {
    private val directory = File(cacheDir, "crash-reports")
    private val document = AtomicFile(File(directory, "latest.json"))
    private val export = File(directory, "crash-report.txt")

    @Synchronized
    fun save(text: String): CachedCrashReport {
        val report = CachedCrashReport(UUID.randomUUID().toString(), now(), text)
        export.delete()
        write(report)
        return report
    }

    @Synchronized
    fun read(id: String? = null): CachedCrashReport? =
        readDocument()?.takeIf(::fresh)?.takeIf { id == null || it.id == id }

    private fun readDocument(): CachedCrashReport? =
        try {
            val json =
                JSONObject(
                    document.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() },
                )
            CachedCrashReport(
                json.getString("id"),
                json.getLong("createdAt"),
                json.getString("text"),
                json.getBoolean("copied"),
                json.getBoolean("foregroundHandled"),
            )
        } catch (_: IOException) {
            clear()
            null
        } catch (_: JSONException) {
            clear()
            null
        }

    private fun fresh(report: CachedCrashReport): Boolean {
        val valid =
            report.createdAt > 0 && now() - report.createdAt in 0 until AppPolicy.CACHE_TTL_MILLIS
        if (!valid) clear()
        return valid
    }

    @Synchronized
    fun markCopied(id: String) {
        read(id)?.let { write(it.copy(copied = true)) }
    }

    /** Claim once before presentation/retry, so process death cannot cause a clipboard loop. */
    @Synchronized
    fun claimForegroundReport(): CachedCrashReport? {
        val report = read()?.takeUnless { it.foregroundHandled } ?: return null
        write(report.copy(foregroundHandled = true))
        return report
    }

    @Synchronized
    fun export(id: String): File? {
        val report = read(id) ?: return null
        export.writeText(report.text, Charsets.UTF_8)
        return export
    }

    private fun write(report: CachedCrashReport) {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create crash cache" }
        document.writeBytes(
            JSONObject()
                .put("id", report.id)
                .put("createdAt", report.createdAt)
                .put("text", report.text)
                .put("copied", report.copied)
                .put("foregroundHandled", report.foregroundHandled)
                .toString()
                .toByteArray(Charsets.UTF_8),
        )
    }

    @Synchronized
    fun sizeBytes(): Long =
        directory.listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() }

    @Synchronized
    fun clear() {
        document.delete()
        export.delete()
    }
}
