package io.github.ultrafeed.app4pda.core.network

import android.content.Context
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.formatting.DisplayFormats
import io.github.ultrafeed.app4pda.data.forum.ForumApiException
import io.github.ultrafeed.app4pda.data.forum.ForumFailure
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

interface HttpStatusFailure {
    val httpStatus: Int?
}

class SourceHttpException(val status: Int) : IOException("HTTP $status"), HttpStatusFailure {
    private companion object {
        private const val serialVersionUID = 1L
    }

    override val httpStatus: Int
        get() = status
}

data class SourceProblem(
    val code: String,
    val description: String,
    val forumPaused: Boolean = false,
) {
    companion object {
        fun from(context: Context, source: String, failure: Throwable): SourceProblem {
            val causes = generateSequence(failure) { it.cause }.take(8).toList()
            val http =
                causes
                    .filterIsInstance<HttpStatusFailure>()
                    .firstOrNull { it.httpStatus != null }
                    ?.httpStatus
            val pause = forumPause(context, causes)
            val (code, message) =
                when {
                    pause != null -> pause
                    causes.any { it is SourceRateLimited } ->
                        "HTTP_429" to
                            context.getString(
                                R.string.source_problem_retry_at,
                                DisplayFormats.dateTime(
                                    causes.filterIsInstance<SourceRateLimited>().first().retryAt,
                                    "dd.MM.yyyy, HH:mm:ss",
                                ),
                            )

                    http != null ->
                        "HTTP_$http" to
                            context.getString(
                                R.string.source_problem_http,
                                http,
                            )

                    causes.any { it is SSLException } ->
                        "TLS_CERTIFICATE" to context.getString(R.string.source_problem_tls)

                    causes.any { it is SocketTimeoutException } ->
                        "TIMEOUT" to context.getString(R.string.forum_update_timeout)

                    failure is ForumApiException && failure.reason != ForumFailure.NETWORK ->
                        failure.reason.name to
                            context.getString(
                                forumMessage(failure.reason),
                            )

                    causes.any {
                        it is UnknownHostException || it is ConnectException || it is IOException
                    } -> "NETWORK" to context.getString(R.string.source_problem_network)

                    failure is IllegalArgumentException || failure is IllegalStateException ->
                        "INVALID_RESPONSE" to
                            context.getString(
                                R.string.source_problem_invalid,
                            )

                    else -> "UNEXPECTED" to context.getString(R.string.source_problem_unknown)
                }
            return SourceProblem("$source · $code", message, forumPaused = pause != null)
        }
    }
}

class SourceRateLimited(val retryAt: Long) : IOException("HTTP 429"), HttpStatusFailure {
    private companion object {
        private const val serialVersionUID = 1L
    }

    override val httpStatus: Int = 429
}

private fun forumMessage(reason: ForumFailure): Int =
    when (reason) {
        ForumFailure.LOGIN_REQUIRED -> R.string.forum_update_login
        ForumFailure.VERIFICATION_REQUIRED -> R.string.forum_verification_required
        ForumFailure.EMPTY_CONTENT -> R.string.forum_empty_document
        else -> R.string.source_problem_invalid
    }

private fun forumPause(context: Context, causes: List<Throwable>): Pair<String, String>? {
    val failures = causes.filterIsInstance<ForumApiException>()
    val verification = failures.firstOrNull { it.reason == ForumFailure.VERIFICATION_REQUIRED }
    val retryAt = failures.firstNotNullOfOrNull { it.retryAt }
    return when {
        verification != null ->
            "VERIFICATION_REQUIRED" to
                (retryAt?.let {
                    context.getString(
                        R.string.forum_verification_retry_at,
                        DisplayFormats.dateTime(it, "dd.MM.yyyy, HH:mm:ss"),
                    )
                } ?: context.getString(R.string.forum_verification_required))

        retryAt != null ->
            "FORUM_PAUSED" to
                context.getString(
                    R.string.source_problem_retry_at,
                    DisplayFormats.dateTime(retryAt, "dd.MM.yyyy, HH:mm:ss"),
                )

        else -> null
    }
}
