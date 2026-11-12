package io.github.ultrafeed.app4pda.core.network

import io.github.ultrafeed.app4pda.data.forum.ForumUrls
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.charset
import io.ktor.http.takeFrom
import java.nio.charset.Charset

internal data class ForumPage(
    val status: Int,
    val html: String,
    val location: String? = null,
    val etag: String? = null,
    val lastModified: String? = null,
)

internal fun interface ForumPageTransport {
    suspend fun execute(request: HttpRequestBuilder): ForumPage
}

internal class ForumPageHttpTransport(private val client: HttpClient) : ForumPageTransport {
    override suspend fun execute(request: HttpRequestBuilder): ForumPage {
        request.url.takeFrom(ForumUrls.normalize(request.url.buildString()))
        val response = client.request(request)
        val charset =
            response.headers["Content-Type"]?.let {
                io.ktor.http.ContentType.parse(it).charset()
            } ?: Charset.forName("windows-1251")
        return ForumPage(
            response.status.value,
            String(response.bodyAsBytes(), charset),
            response.headers["Location"],
            response.headers["ETag"],
            response.headers["Last-Modified"],
        )
    }
}
