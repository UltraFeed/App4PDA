package io.github.ultrafeed.app4pda.core.formatting

import android.content.Context
import io.github.ultrafeed.app4pda.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

internal fun informationDate(value: Long) = DisplayFormats.dateTime(value).ifEmpty { "—" }

object DisplayFormats {
    fun date(value: Date?, pattern: String = "dd.MM.yyyy"): String =
        value?.let { dateTime(it.time, pattern) }.orEmpty()

    fun dateTime(value: Long, pattern: String = "dd.MM.yyyy, HH:mm"): String =
        if (value <= 0) ""
        else
            DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(value))

    fun fileSize(context: Context, bytes: Long): String {
        val tebibytes = bytes.toFloat() / 1.0995116E12f
        val gibibytes = bytes.toFloat() / 1.0737418E9f
        val mebibytes = bytes.toFloat() / 1048576f
        val kibibytes = bytes.toFloat() / 1024f
        return when {
            tebibytes > 1f -> context.getString(R.string.format_size_tb, tebibytes)
            gibibytes > 1f -> context.getString(R.string.format_size_gb, gibibytes)
            mebibytes > 1f -> context.getString(R.string.format_size_mb, mebibytes)
            kibibytes > 1f -> context.getString(R.string.format_size_kb, kibibytes)
            else -> context.getString(R.string.format_size_bytes, bytes)
        }
    }

    private val androidVersions =
        listOf(
            "1.0",
            "1.1",
            "1.5",
            "1.6",
            "2.0",
            "2.0.1",
            "2.1",
            "2.2",
            "2.3",
            "2.3.3",
            "3.0",
            "3.1",
            "3.2",
            "4.0",
            "4.0.3",
            "4.1",
            "4.2",
            "4.3",
            "4.4",
            "4.4W",
            "5.0",
            "5.1",
            "6.0",
            "7.0",
            "7.1",
            "8.0",
            "8.1",
            "9",
            "10",
            "11",
            "12",
        )

    fun androidVersion(sdk: Int): String =
        androidVersions.getOrNull(sdk - 1)?.let { "$it+" }.orEmpty()
}
