package io.github.ultrafeed.app4pda.data.forum

import android.os.SystemClock
import io.github.ultrafeed.app4pda.core.AppPolicy
import io.github.ultrafeed.app4pda.core.AppPolicy.SEARCH_COOLDOWN_MILLIS
import io.github.ultrafeed.app4pda.data.database.ForumDao
import io.github.ultrafeed.app4pda.data.database.ForumQueueState
import io.ktor.http.HttpStatusCode
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shared pacing; background backoff must not prevent an interactive page from being opened. */
class ForumRequests(
    private val dao: ForumDao,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val wallTime: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val interactive = AtomicInteger()
    private var nextAt = 0L
    private val limitedPages = mutableMapOf<String, ForumApiException>()
    private val pacing = ForumPacing()

    suspend fun <T> execute(
        userInitiated: Boolean,
        url: String? = null,
        action: suspend () -> T,
    ): T {
        if (userInitiated) interactive.incrementAndGet()
        try {
            var turn: RequestTurn<T>
            do {
                turn = mutex.withLock { takeTurn(userInitiated, url, action) }
                if (turn is RequestTurn.Wait) delay(turn.millis.milliseconds)
            } while (turn is RequestTurn.Wait)
            return (turn as RequestTurn.Completed).value
        } finally {
            if (userInitiated) interactive.decrementAndGet()
        }
    }

    private suspend fun <T> takeTurn(
        userInitiated: Boolean,
        url: String?,
        action: suspend () -> T,
    ): RequestTurn<T> {
        if (!userInitiated && interactive.get() > 0) {
            return RequestTurn.Wait(AppPolicy.REQUEST_PRIORITY_POLL_MILLIS)
        }
        requireAvailable(userInitiated, url)
        val pause = nextAt - elapsedRealtime()
        return if (pause > 0) {
            RequestTurn.Wait(pause.coerceAtMost(AppPolicy.REQUEST_PRIORITY_POLL_MILLIS))
        } else {
            RequestTurn.Completed(perform(userInitiated, url, action))
        }
    }

    private suspend fun requireAvailable(userInitiated: Boolean, url: String?) {
        val now = wallTime()
        limitedPages.entries.removeAll { (it.value.retryAt ?: 0L) <= now }
        limitedPages[url]?.let { throw it }
        if (userInitiated) return
        val remaining = (dao.queueState()?.retryAt ?: 0L) - wallTime()
        if (remaining > 0) {
            throw ForumApiException(
                ForumFailure.HTTP,
                "Forum rate limit",
                HttpStatusCode.TooManyRequests.value,
                retryAfterMillis = remaining,
                retryAt = wallTime() + remaining,
            )
        }
    }

    private suspend fun <T> perform(
        userInitiated: Boolean,
        url: String?,
        action: suspend () -> T,
    ): T {
        try {
            return action().also {
                pacing.succeeded()
                if (userInitiated) {
                    dao.queueState()
                        ?.takeIf { state -> state.retryAt > wallTime() }
                        ?.let { state ->
                            dao.saveQueueState(state.copy(retryAt = 0))
                        }
                }
            }
        } catch (failure: ForumApiException) {
            val recorded = recordFailure(failure)
            val duration = failure.retryAfterMillis ?: 0
            if (url != null && !failure.searchQuota && duration > 0) {
                val now = wallTime()
                val retryAt = now + duration.coerceAtMost(Long.MAX_VALUE - now)
                limitedPages[url] =
                    ForumApiException(
                        failure.reason,
                        failure.message.orEmpty(),
                        failure.statusCode,
                        cause = failure,
                        retryAfterMillis = failure.retryAfterMillis,
                        retryAt = retryAt,
                    )
            }
            throw recorded
        } finally {
            nextAt = elapsedRealtime() + pacing.intervalMillis
        }
    }

    private suspend fun recordFailure(failure: ForumApiException): ForumApiException {
        val quota =
            !failure.searchQuota &&
                (failure.statusCode == HttpStatusCode.TooManyRequests.value ||
                    failure.retryAfterMillis != null)
        when {
            failure.reason == ForumFailure.VERIFICATION_REQUIRED -> {
                val duration =
                    failure.retryAfterMillis?.takeIf { it > 0 }
                        ?: AppPolicy.FORUM_VERIFICATION_COOLDOWN_MILLIS
                val retryAt = postpone(duration)
                return ForumApiException(
                    failure.reason,
                    failure.message.orEmpty(),
                    failure.statusCode,
                    cause = failure,
                    retryAfterMillis = retryAt - wallTime(),
                    retryAt = retryAt,
                )
            }

            quota -> {
                val duration = failure.retryAfterMillis ?: SEARCH_COOLDOWN_MILLIS
                postpone(duration)
            }

            (failure.statusCode ?: 0) >= HttpStatusCode.InternalServerError.value ||
                failure.reason == ForumFailure.NETWORK -> pacing.failed()
        }
        return failure
    }

    private suspend fun postpone(duration: Long): Long {
        val state = dao.queueState() ?: ForumQueueState()
        val now = wallTime()
        val retryAt = maxOf(state.retryAt, now + duration.coerceIn(0, Long.MAX_VALUE - now))
        dao.saveQueueState(state.copy(retryAt = retryAt))
        return retryAt
    }
}

private sealed interface RequestTurn<out T> {
    data class Completed<T>(val value: T) : RequestTurn<T>

    data class Wait(val millis: Long) : RequestTurn<Nothing>
}

internal class ForumPacing {
    var intervalMillis = AppPolicy.REQUEST_INITIAL_MILLIS
        private set

    private var successes = 0

    fun succeeded() {
        if (++successes == AppPolicy.REQUEST_SUCCESS_COUNT) {
            intervalMillis =
                (intervalMillis - AppPolicy.REQUEST_STEP_MILLIS).coerceAtLeast(
                    AppPolicy.REQUEST_INITIAL_MILLIS,
                )
            successes = 0
        }
    }

    fun failed() {
        successes = 0
        intervalMillis =
            (intervalMillis + AppPolicy.REQUEST_STEP_MILLIS).coerceAtMost(
                AppPolicy.REQUEST_MAX_MILLIS,
            )
    }
}
