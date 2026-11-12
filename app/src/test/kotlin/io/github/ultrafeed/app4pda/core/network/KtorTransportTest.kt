package io.github.ultrafeed.app4pda.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.url
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class KtorTransportTest {
    @Test
    fun actualRestrictionResponseIsDecodedUsingDeclaredUtf8AndNotRetried() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val html = "<title>4PDA .:. Всё в порядке, но...</title>"
            server.enqueue(
                MockResponse.Builder()
                    .code(403)
                    .addHeader("Content-Type", "text/html; charset=UTF-8")
                    .body(html)
                    .build(),
            )
            val client = createForumHttpClient()
            client.use { client ->
                val page =
                    ForumPageHttpTransport(client)
                        .execute(
                            HttpRequestBuilder().apply { url(server.url("/auth").toString()) },
                        )
                assertThat(
                    page.status,
                    CoreMatchers.`is`(
                        403,
                    ),
                )
                assertThat(
                    page.html,
                    CoreMatchers.`is`(
                        html,
                    ),
                )
                assertThat(
                    server.requestCount,
                    CoreMatchers.`is`(
                        1,
                    ),
                )
            }
        }
    }

    @Test
    fun pageUsesDeclaredWindows1251AndReturnsRelativeRedirectWithoutFollowing() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val html = "Сообщение"
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Content-Type", "text/html; charset=windows-1251")
                    .body(Buffer().write(html.toByteArray(charset("windows-1251"))))
                    .build(),
            )
            server.enqueue(
                MockResponse.Builder().code(302).addHeader("Location", "../profile").build(),
            )
            val client = createForumHttpClient()
            client.use { client ->
                val transport = ForumPageHttpTransport(client)
                fun request() =
                    HttpRequestBuilder().apply { url(server.url("/forum/post").toString()) }
                assertThat(
                    transport.execute(request()).html,
                    CoreMatchers.`is`(
                        html,
                    ),
                )
                val redirect = transport.execute(request())
                assertThat(
                    redirect.status,
                    CoreMatchers.`is`(
                        302,
                    ),
                )
                assertThat(
                    redirect.location,
                    CoreMatchers.`is`(
                        "../profile",
                    ),
                )
                assertThat(
                    server.requestCount,
                    CoreMatchers.`is`(
                        2,
                    ),
                )
            }
        }
    }

    @Test
    fun stalledBodyIsCancelledWithoutWaitingForSocketTimeout() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder().body("body").bodyDelay(10, TimeUnit.SECONDS).build(),
            )
            val client = createForumHttpClient()
            client.use { client ->
                val job =
                    async(Dispatchers.IO) {
                        ForumPageHttpTransport(client)
                            .execute(
                                HttpRequestBuilder().apply { url(server.url("/slow").toString()) },
                            )
                    }
                assertThat(
                    server.takeRequest(5, TimeUnit.SECONDS),
                    CoreMatchers.notNullValue(),
                )
                withTimeout(
                    2000.milliseconds,
                ) {
                    job.cancelAndJoin()
                }
                assertTrue(job.isCancelled)
                assertThat(
                    server.requestCount,
                    CoreMatchers.`is`(
                        1,
                    ),
                )
            }
        }
    }

    @Test
    fun socketTimeoutIsReportedWithoutAnAutomaticRetry() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder().body("body").bodyDelay(2, TimeUnit.SECONDS).build(),
            )
            val client =
                HttpClient(OkHttp) {
                    engine { config { readTimeout(100, TimeUnit.MILLISECONDS) } }
                }
            client.use { client ->
                try {
                    ForumPageHttpTransport(client)
                        .execute(
                            HttpRequestBuilder().apply { url(server.url("/timeout").toString()) },
                        )
                    fail("Timeout accepted")
                } catch (_: IOException) {}
                assertThat(
                    server.requestCount,
                    CoreMatchers.`is`(
                        1,
                    ),
                )
            }
        }
    }
}
