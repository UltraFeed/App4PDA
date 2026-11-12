package io.github.ultrafeed.app4pda.data.forum

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ForumDates {
    fun parseCompactDate(value: Int): Long =
        if (value > 0)
            parse(
                    "$value",
                    "yyyyMMdd",
                )
                .time
        else 0L

    fun parse(text: String?, pattern: String, locale: Locale = Locale.getDefault()): Date {
        if (text.isNullOrEmpty()) return Date(0)
        val now = System.currentTimeMillis()
        val relativeDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val normalized =
            text
                .trim()
                .replace("Сегодня", relativeDateFormat.format(Date(now)))
                .replace("Вчера", relativeDateFormat.format(Date(now - 86400000L)))
        val patterns =
            listOf(
                pattern,
                "EEE, dd MMM yyyy HH:mm:ss zzz",
                "EEE, d MMM yyyy HH:mm:ss zzz",
                "EEE, dd-MMM-yyyy HH:mm:ss zzz",
                "EEE, d-MMM-yyyy HH:mm:ss zzz",
                "EEE, dd-MMM-yy HH:mm:ss zzz",
                "EEE, d-MMM-yy HH:mm:ss zzz",
            )
        return patterns.firstNotNullOfOrNull { candidate ->
            try {
                SimpleDateFormat(candidate, locale).apply { isLenient = true }.parse(normalized)
            } catch (_: IllegalArgumentException) {
                // Server-defined patterns may be invalid; try the supported formats.
                null
            } catch (_: ParseException) {
                null
            }
        } ?: Date(0)
    }
}
