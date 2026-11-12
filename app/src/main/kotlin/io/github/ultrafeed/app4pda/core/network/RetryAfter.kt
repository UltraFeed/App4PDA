package io.github.ultrafeed.app4pda.core.network

import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

internal fun retryAfterMillis(value: String?, now: Long = System.currentTimeMillis()): Long? {
    value ?: return null
    val seconds = value.toLongOrNull()
    return if (seconds != null) {
        val maximumSeconds = TimeUnit.MILLISECONDS.toSeconds(Long.MAX_VALUE - now.coerceAtLeast(0))
        TimeUnit.SECONDS.toMillis(seconds.coerceIn(0, maximumSeconds))
    } else
        runCatching {
            Duration.between(
                    Instant.ofEpochMilli(now),
                    ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(),
                )
                .toMillis()
                .coerceAtLeast(0)
        }
            .getOrNull()
}
