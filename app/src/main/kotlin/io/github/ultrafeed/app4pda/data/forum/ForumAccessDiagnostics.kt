package io.github.ultrafeed.app4pda.data.forum

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import androidx.core.net.toUri
import io.github.ultrafeed.app4pda.core.network.ForumHttpResponse
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Debug-only HTTP tracing: session presence is recorded, never its value or document body. */
internal fun forumHttpEvent(
    context: Context,
    original: String,
    response: ForumHttpResponse,
    session: Boolean,
) {
    if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
    Log.d(
        "ForumAccess",
        "http original=${diagnosticUrl(original)} url=${diagnosticUrl(response.url.toString())} status=${response.code} session=$session elapsed=${SystemClock.elapsedRealtime()} location=${response.headers["Location"]?.let(::diagnosticUrl).orEmpty()}",
    )
}

private fun diagnosticUrl(value: String): String =
    value
        .toHttpUrlOrNull()
        ?.newBuilder()
        ?.removeAllQueryParameters("s")
        ?.removeAllQueryParameters("auth_key")
        ?.removeAllQueryParameters("token")
        ?.build()
        ?.toString() ?: value.substringBefore('?')

/** Only fixed event names, route categories and numeric codes. Never HTML, headers or queries. */
internal fun forumAccessEvent(
    context: Context,
    event: String,
    url: String? = null,
    code: Int? = null,
) {
    if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
    val uri = url?.toUri()
    val route =
        when {
            uri == null -> ""
            uri.path?.startsWith("/cdn-cgi/") == true -> {
                val operation =
                    Regex(
                            "/(orchestrate/(?:chl_page|managed|jsch|turnstile)/v[0-9]+|flow|pat)(?:/|$)",
                        )
                        .find(uri.path.orEmpty())
                        ?.groupValues
                        ?.get(1)
                "challenge-platform/${operation ?: "other"}"
            }

            uri.path == "/forum/index.php" -> "forum-index"
            else -> "resource"
        }
    Log.d(
        "ForumAccess",
        "$event host=${uri?.host.orEmpty()} route=$route topic=${uri?.getQueryParameter("showtopic").orEmpty()} code=${code ?: 0}",
    )
}
