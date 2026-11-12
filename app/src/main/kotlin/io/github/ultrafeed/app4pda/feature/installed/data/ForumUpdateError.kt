package io.github.ultrafeed.app4pda.feature.installed.data

import android.content.Context
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.AppPolicy
import io.github.ultrafeed.app4pda.core.formatting.DisplayFormats
import io.github.ultrafeed.app4pda.core.network.SourceProblem
import io.github.ultrafeed.app4pda.data.forum.ForumApiException
import io.github.ultrafeed.app4pda.data.forum.ForumFailure
import io.ktor.http.HttpStatusCode
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.TimeoutCancellationException

/** Only known fields reach UI/logs; exception messages can contain private request URLs. */
internal fun forumUpdateError(
    context: Context,
    failure: Throwable,
    now: Long = System.currentTimeMillis(),
): String {
    val forum = failure as? ForumApiException
    val causes = generateSequence(failure) { it.cause }.take(8).toList()
    val networkProblem = networkFailureResource(causes)
    return when {
        forum?.retryAt != null -> SourceProblem.from(context, "4PDA", forum).description
        forum?.let(::isSearchQuota) == true ->
            context.getString(
                R.string.forum_update_quota,
                DisplayFormats.dateTime(
                    forum.searchRetryAt(now),
                ),
            )

        forum?.reason == ForumFailure.LOGIN_REQUIRED ->
            context.getString(R.string.forum_update_login)

        forum?.reason == ForumFailure.VERIFICATION_REQUIRED ->
            context.getString(R.string.forum_verification_required)

        networkProblem != null -> context.getString(networkProblem)

        forum?.reason == ForumFailure.HTTP && forum.statusCode != null ->
            context.getString(
                R.string.forum_update_http,
                forum.statusCode,
            )

        forum?.reason == ForumFailure.NETWORK -> context.getString(R.string.forum_update_network)
        forum?.reason == ForumFailure.EMPTY_CONTENT ->
            context.getString(R.string.forum_update_empty)

        forum?.reason?.let(::isParsingFailure) == true ->
            context.getString(R.string.forum_update_parsing)

        else -> context.getString(R.string.source_problem_unknown)
    }
}

internal fun forumFailureKey(failure: Throwable): String {
    val forum = failure as? ForumApiException
    return "${forum?.reason}:${forum?.statusCode}:${failure.javaClass.name}:${failure.cause?.javaClass?.name}"
}

private fun networkFailureResource(causes: List<Throwable>): Int? =
    when {
        causes.any {
            it is SocketTimeoutException || it is TimeoutCancellationException
        } -> (R.string.forum_update_timeout)

        causes.any { it is SSLException } -> (R.string.forum_update_tls)
        causes.any { it is UnknownHostException || it is ConnectException } ->
            (R.string.forum_update_network)

        else -> null
    }

private fun isParsingFailure(reason: ForumFailure): Boolean =
    reason in setOf(ForumFailure.PARSING, ForumFailure.INVALID_RESPONSE)

private fun isSearchQuota(failure: ForumApiException): Boolean =
    failure.statusCode == HttpStatusCode.TooManyRequests.value || failure.searchQuota

private fun ForumApiException.searchRetryAt(now: Long): Long =
    now +
        if (searchQuota) {
            retryAfterMillis ?: AppPolicy.SEARCH_COOLDOWN_MILLIS
        } else maxOf(retryAfterMillis ?: 0, AppPolicy.SEARCH_COOLDOWN_MILLIS)
