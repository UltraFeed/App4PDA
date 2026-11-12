package io.github.ultrafeed.app4pda.core.network

import io.github.ultrafeed.app4pda.core.AppPolicy
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.concurrent.TimeUnit

/** Separate clients preserve the forum's page and authentication redirect policies. */
internal fun createForumHttpClient(): HttpClient =
    HttpClient(OkHttp) {
        expectSuccess = false
        followRedirects = false
        engine {
            config {
                followRedirects(false)
                connectTimeout(
                    AppPolicy.HTTP_CONNECT_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
                readTimeout(
                    AppPolicy.HTTP_AUTH_READ_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
            }
        }
    }

internal fun createForumPagesClient(): HttpClient =
    HttpClient(OkHttp) {
        expectSuccess = false
        followRedirects = false
        engine {
            config {
                connectTimeout(
                    AppPolicy.HTTP_CONNECT_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
                readTimeout(
                    AppPolicy.HTTP_PAGE_READ_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
                followRedirects(false)
                followSslRedirects(false)
            }
        }
    }
