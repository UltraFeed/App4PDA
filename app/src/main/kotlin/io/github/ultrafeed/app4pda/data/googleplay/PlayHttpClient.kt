package io.github.ultrafeed.app4pda.data.googleplay

import android.util.Log
import io.github.ultrafeed.app4pda.core.AppPolicy
import io.github.ultrafeed.app4pda.core.network.retryAfterMillis
import io.ktor.http.HttpStatusCode
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/** A shared cooldown for every Aurora request, including check-in and delivery. */
internal class PlayHttpClient : Interceptor {
    private val retryAt = AtomicLong()

    val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(AppPolicy.HTTP_CONNECT_MILLIS, TimeUnit.MILLISECONDS)
            .readTimeout(AppPolicy.HTTP_AUTH_READ_MILLIS, TimeUnit.MILLISECONDS)
            .addInterceptor(this)
            .build()

    override fun intercept(chain: Interceptor.Chain): Response {
        val deadline = retryAt.get()
        if (System.currentTimeMillis() < deadline) throw PlaySourceException.RateLimited(deadline)

        val request = chain.request()
        val response = chain.proceed(request)
        Log.d(
            "GooglePlay",
            "HTTP ${request.method} ${request.url.encodedPath} status=${response.code} language=${request.header("Accept-Language")}",
        )
        if (response.code == HttpStatusCode.TooManyRequests.value) {
            val now = System.currentTimeMillis()
            val delay =
                retryAfterMillis(response.header("Retry-After"), now)
                    ?.coerceAtMost(AppPolicy.PLAY_RETRY_MAX_MILLIS)
                    ?: AppPolicy.PLAY_RETRY_DEFAULT_MILLIS
            retryAt.accumulateAndGet(now + delay, ::maxOf)
            response.close()
            throw PlaySourceException.RateLimited(retryAt.get())
        }
        return response
    }
}
