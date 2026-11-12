package io.github.ultrafeed.app4pda.core.network

import io.github.ultrafeed.app4pda.core.AppPolicy
import io.github.ultrafeed.app4pda.data.forum.ForumUrls
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareRequest
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpMethod
import java.io.IOException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * GET/HEAD only: auth POST responses deliberately retain their separate, non-following contract.
 */
internal suspend fun HttpClient.readForumResponse(
    url: String,
    method: HttpMethod = HttpMethod.Get,
    headers: Map<String, String> = emptyMap(),
    maxRedirects: Int = AppPolicy.PAGE_REDIRECT_LIMIT,
    onResponse: (ForumHttpResponse, Boolean) -> Unit = { _, _ -> },
): ForumHttpResponse {
    require(method == HttpMethod.Get || method == HttpMethod.Head)
    require(maxRedirects >= 0)
    val original = ForumUrls.normalize(url).toHttpUrl()
    var current = original
    val visited = hashSetOf<String>()
    repeat(maxRedirects + 1) { followed ->
        currentCoroutineContext().ensureActive()
        if (!visited.add(current.newBuilder().fragment(null).build().toString()))
            throw IOException("Forum redirect loop")
        val response = readForumHop(current, original, method, headers, onResponse)
        currentCoroutineContext().ensureActive()
        val location = response.headers["Location"]
        if (response.code !in REDIRECT_CODES || location == null) return response
        current = nextForumHop(current, location, followed, maxRedirects)
    }
    error("Unreachable redirect state")
}

private fun nextForumHop(current: HttpUrl, location: String, followed: Int, limit: Int): HttpUrl {
    val next = current.resolve(location)
    val failure =
        when {
            followed == limit -> "Too many forum redirects"
            next == null -> "Invalid forum redirect"
            next.scheme != current.scheme -> "Forum redirect changes protocol"
            else -> null
        }
    if (failure != null) throw IOException(failure)
    return ForumUrls.normalize("${checkNotNull(next)}").toHttpUrl()
}

private suspend fun HttpClient.readForumHop(
    current: HttpUrl,
    original: HttpUrl,
    method: HttpMethod,
    headers: Map<String, String>,
    onResponse: (ForumHttpResponse, Boolean) -> Unit,
): ForumHttpResponse {
    val sameOrigin =
        current.scheme == original.scheme &&
            current.host == original.host &&
            current.port == original.port
    return prepareRequest("$current") {
            this.method = method
            headers.forEach { (name, value) ->
                val credential =
                    name.lowercase() in setOf("cookie", "authorization", "proxy-authorization")
                if (!credential || sameOrigin) this.headers.append(name, value)
            }
        }
        .execute { response ->
            val redirect =
                response.status.value in REDIRECT_CODES && response.headers["Location"] != null
            val bytes =
                if (redirect || method == HttpMethod.Head) {
                    // Cancel before closing: an unused intermediate body can otherwise block the
                    // next hop.
                    response.cancel("Response body is not needed")
                    byteArrayOf()
                } else response.bodyAsBytes()
            ForumHttpResponse(
                response.status.value,
                response.status.description,
                response.headers,
                response.call.request.url,
                bytes,
            )
        }
        .also {
            onResponse(
                it,
                sameOrigin &&
                    headers.any { (name, value) ->
                        name.equals("Cookie", true) && value.isNotBlank()
                    },
            )
        }
}

private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
