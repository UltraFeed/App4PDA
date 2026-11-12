package io.github.ultrafeed.app4pda.feature.recent.data

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

internal data class DigestPeriod(val start: LocalDate, val end: LocalDate) {
    companion object {
        private val whitespace = Regex("[\\p{Z}\\s]+")
        private val datePattern = Regex("\\d{1,2} \\p{L}+ \\d{4}")

        fun parse(label: String): DigestPeriod {
            val parts = label.replace('\u00a0', ' ').trim().split(Regex("\\s*[-–—]\\s*"))
            if (parts.size != 2) throw DigestContentException("Invalid digest period: $label")
            val start = date(parts[0])
            val end = date(parts[1])
            if (start == null || end == null || start > end)
                throw DigestContentException("Invalid digest period: $label")
            return DigestPeriod(start, end)
        }

        private fun date(text: String): LocalDate? {
            val normalized = text.trim().replace(whitespace, " ")
            if (!datePattern.matches(normalized)) return null
            // Android's localized java.time text tables differ from the host JDK's.
            // DateFormat handles Russian grammatical month forms on both runtimes.
            val parser =
                SimpleDateFormat("d MMMM yyyy", Locale.forLanguageTag("ru-RU")).apply {
                    isLenient = false
                    timeZone = TimeZone.getTimeZone("UTC")
                }
            val position = ParsePosition(0)
            val parsed = parser.parse(normalized, position)
            return if (parsed == null || position.index != normalized.length) null
            else
                parser.calendar.let {
                    LocalDate.of(
                        it.get(Calendar.YEAR),
                        it.get(Calendar.MONTH) + 1,
                        it.get(Calendar.DAY_OF_MONTH),
                    )
                }
        }
    }
}
