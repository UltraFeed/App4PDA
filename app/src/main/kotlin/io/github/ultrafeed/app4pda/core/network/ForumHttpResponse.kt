package io.github.ultrafeed.app4pda.core.network

import io.ktor.http.Headers
import io.ktor.http.Url

internal data class ForumHttpResponse(
    val code: Int,
    val message: String,
    val headers: Headers,
    val url: Url,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        when {
            this === other -> true
            other !is ForumHttpResponse -> false
            code != other.code -> false
            message != other.message -> false
            headers != other.headers -> false
            url != other.url -> false
            else -> bytes.contentEquals(other.bytes)
        }

    override fun hashCode(): Int {
        var result = code
        result = 31 * result + message.hashCode()
        result = 31 * result + headers.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}
