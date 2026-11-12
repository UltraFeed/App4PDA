package io.github.ultrafeed.app4pda.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.http.HttpMethod
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.fail
import org.junit.Test

/** Controlled HTTP transitions, not synthetic successful forum HTML. */
class ForumRedirectsTest {
    private fun client(seen: MutableList<Request>, reply: (Int) -> Pair<Int, String?>): HttpClient =
        HttpClient(OkHttp) {
            followRedirects = false
            engine {
                preconfigured =
                    OkHttpClient.Builder()
                        .followRedirects(false)
                        .followSslRedirects(false)
                        .addInterceptor { chain ->
                            val request = chain.request()
                            seen += request
                            val (status, location) = reply(seen.lastIndex)
                            Response.Builder()
                                .request(request)
                                .protocol(Protocol.HTTP_1_1)
                                .code(status)
                                .message("Controlled response")
                                .apply { location?.let { header("Location", it) } }
                                .body("response".toResponseBody("text/plain".toMediaType()))
                                .build()
                        }
                        .build()
            }
        }

    @Test
    fun allRedirectCodesResolveRelativeLocationsWithoutChangingGetOrEncodedQuery(): Unit =
        runBlocking {
            val seen = mutableListOf<Request>()
            val locations =
                listOf(
                    "../next?text=%C0%E2%E8%F2%EE",
                    "?page=2",
                    "/forum/third",
                    "//4pda.to/fourth",
                    "/final",
                )
            val codes = listOf(301, 302, 303, 307, 308)
            val client =
                client(seen) { index ->
                    if (index < codes.size) codes[index] to locations[index] else 200 to null
                }
            client.use { client ->
                val response = client.readForumResponse("https://4pda.to/forum/start")
                assertThat(
                    response.code,
                    `is`(
                        200,
                    ),
                )
                assertThat(
                    response.bytes.toString(Charsets.UTF_8),
                    `is`(
                        "response",
                    ),
                )
                assertThat(
                    seen[1].url.toString(),
                    `is`(
                        "https://4pda.to/next?text=%C0%E2%E8%F2%EE",
                    ),
                )
                assertThat(
                    seen[2].url.toString(),
                    `is`(
                        "https://4pda.to/next?page=2",
                    ),
                )
                assertThat(
                    response.url.toString(),
                    `is`(
                        "https://4pda.to/final",
                    ),
                )
                assertThat(seen.all { it.method == "GET" }, `is`(true))
            }
        }

    @Test
    fun credentialsAreCaseInsensitiveAndRestrictedToTheOriginalSchemeHostAndPort(): Unit =
        runBlocking {
            val seen = mutableListOf<Request>()
            val locations =
                listOf(
                    "/same",
                    "https://4pda.to:8443/port",
                    "https://cdn.example/file",
                    "https://4pda.to/back",
                )
            val client =
                client(seen) { index ->
                    if (index < locations.size) 302 to locations[index] else 200 to null
                }
            client.use { client ->
                client.readForumResponse(
                    "https://4pda.to/start",
                    headers =
                        mapOf(
                            "cOoKiE" to "session=fixture",
                            "authorization" to "fixture",
                            "User-Agent" to "test",
                        ),
                )
                for (index in listOf(0, 1, 4)) {
                    assertThat(
                        seen[index].header("Cookie"),
                        `is`(
                            "session=fixture",
                        ),
                    )
                    assertThat(
                        seen[index].header("Authorization"),
                        `is`(
                            "fixture",
                        ),
                    )
                }
                for (index in listOf(2, 3)) {
                    assertThat(seen[index].header("Cookie"), CoreMatchers.nullValue())
                    assertThat(seen[index].header("Authorization"), CoreMatchers.nullValue())
                }
                assertThat(seen.all { it.header("User-Agent") == "test" }, `is`(true))
            }
        }

    @Test
    fun headRemainsHeadAndNeverReadsTheFinalBody(): Unit = runBlocking {
        val seen = mutableListOf<Request>()
        val client = client(seen) { if (it == 0) 303 to "/final" else 200 to null }
        client.use { client ->
            val response = client.readForumResponse("https://4pda.to/start", HttpMethod.Head)
            assertThat(response.bytes.isEmpty(), `is`(true))
            assertThat(seen.all { it.method == "HEAD" }, `is`(true))
        }
    }

    @Test
    fun missingLocationRemainsAnHttpResponseInsteadOfInventingAnotherRequest(): Unit = runBlocking {
        val seen = mutableListOf<Request>()
        val client = client(seen) { 302 to null }
        client.use { client ->
            assertThat(
                client.readForumResponse("https://4pda.to/start").code,
                `is`(
                    302,
                ),
            )
            assertThat(
                seen.size,
                `is`(
                    1,
                ),
            )
        }
    }

    @Test
    fun loopsAndHopLimitsStopBeforeAnotherRequest(): Unit = runBlocking {
        for (loop in listOf(false, true)) {
            val seen = mutableListOf<Request>()
            val client = client(seen) { 302 to if (loop) "/start#same-request" else "/step$it" }
            client.use { client ->
                try {
                    client.readForumResponse("https://4pda.to/start", maxRedirects = 2)
                    fail("Redirect chain accepted")
                } catch (failure: IOException) {
                    assertThat(
                        failure.message,
                        `is`(
                            if (loop) "Forum redirect loop" else "Too many forum redirects",
                        ),
                    )
                }
                assertThat(
                    seen.size,
                    `is`(
                        if (loop) 1 else 3,
                    ),
                )
            }
        }
    }

    @Test
    fun protocolChangesAndNonHttpLocationsNeverMakeAnotherRequest(): Unit = runBlocking {
        for (location in
            listOf(
                "http://4pda.to/insecure",
                "javascript:alert(1)",
                "ftp://example.com/file",
            )) {
            val seen = mutableListOf<Request>()
            val client = client(seen) { 302 to location }
            client.use { client ->
                try {
                    client.readForumResponse("https://4pda.to/start")
                    fail("Invalid redirect accepted")
                } catch (_: IOException) {
                    assertThat(
                        seen.size,
                        `is`(
                            1,
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun intermediateBodyIsNotDownloadedBeforeFollowingTheNextHop(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(302)
                    .addHeader("Location", "/final")
                    .body("ignored")
                    .bodyDelay(10, TimeUnit.SECONDS)
                    .build(),
            )
            server.enqueue(MockResponse.Builder().body("final").build())
            val client = createForumPagesClient()
            client.use { client ->
                val response =
                    withTimeout(2500.milliseconds) {
                        client.readForumResponse(server.url("/start").toString())
                    }
                assertThat(
                    response.bytes.toString(Charsets.UTF_8),
                    `is`(
                        "final",
                    ),
                )
                assertThat(
                    server.requestCount,
                    `is`(
                        2,
                    ),
                )
            }
        }
    }

    @Test
    fun cancellationDuringFinalBodyClosesTheRequestWithoutRetry(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder().body("delayed").bodyDelay(10, TimeUnit.SECONDS).build(),
            )
            val client = createForumPagesClient()
            client.use { client ->
                val loading =
                    async(Dispatchers.IO) {
                        client.readForumResponse(server.url("/slow").toString())
                    }
                assertThat(server.takeRequest(5, TimeUnit.SECONDS), CoreMatchers.notNullValue())
                withTimeout(
                    2500.milliseconds,
                ) {
                    loading.cancelAndJoin()
                }
                assertThat(loading.isCancelled, `is`(true))
                assertThat(
                    server.requestCount,
                    `is`(
                        1,
                    ),
                )
            }
        }
    }
}
