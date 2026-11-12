package io.github.ultrafeed.app4pda.data.rustore

import android.os.Build
import android.os.SystemClock
import android.util.Log
import io.github.ultrafeed.app4pda.core.AppPolicy
import io.github.ultrafeed.app4pda.core.network.HttpStatusFailure
import io.github.ultrafeed.app4pda.core.network.SharedRequests
import io.github.ultrafeed.app4pda.core.network.SourceHttpException
import io.github.ultrafeed.app4pda.core.network.SourceRateLimited
import io.github.ultrafeed.app4pda.core.network.retryAfterMillis
import io.github.ultrafeed.app4pda.data.database.LocalDatabase
import io.github.ultrafeed.app4pda.data.database.RuStoreCacheRecord
import io.github.ultrafeed.app4pda.data.preferences.AppSettings
import io.ktor.http.HttpStatusCode
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

@Serializable
data class RuStoreDetails(
    val appId: Int,
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val shortDescription: String = "",
    val fullDescription: String = "",
    val whatsNew: String = "",
    val iconUrl: String = "",
    val fileSize: Long = 0,
    val fileUrls: List<RuStoreMedia> = emptyList(),
)

@Serializable data class RuStoreMedia(val fileUrl: String, val ordinal: Int, val type: String)

@Serializable private data class DetailsResponse(val code: String, val body: RuStoreDetails? = null)

@Serializable data class RuStoreDownload(val url: String, val size: Long, val hash: String = "")

@Serializable private data class DownloadResponse(val downloadUrls: List<RuStoreDownload>)

@Serializable
private data class DownloadRequest(
    val appId: Int,
    val firstInstall: Boolean,
    val screenDensity: Int,
    val sdkVersion: Int,
    val supportedAbis: List<String>,
    val withoutSplits: Boolean,
)

class RuStoreUnavailable : IOException("RuStore: application unavailable"), HttpStatusFailure {
    private companion object {
        private const val serialVersionUID = 1L
    }

    override val httpStatus: Int = 404
}

/** Public package API; no forum credentials, borrowed signatures or custom TLS trust. */
class RuStoreRepository(
    private val client: OkHttpClient,
    private val database: LocalDatabase,
    private val settings: AppSettings,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val requests = Mutex()
    private var nextRequestAt = 0L
    private val cacheOperations = Mutex()
    private var cacheGeneration = 0L
    private var clearedAt = 0L
    private val removedAt = mutableMapOf<String, Long>()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun cached(
        pack: String,
        maxAgeMillis: Long = AppPolicy.CACHE_TTL_MILLIS,
    ): RuStoreDetails? =
        withContext(ioDispatcher) {
            database
                .users()
                .ruStoreCache(pack)
                ?.takeIf {
                    now() - it.fetchedAt in 0 until maxAgeMillis
                }
                ?.let {
                    try {
                        json.decodeFromString<RuStoreDetails>(it.document)
                    } catch (_: SerializationException) {
                        database.users().deleteRuStoreCache(pack)
                        null
                    }
                }
        }

    private val pageRequests = SharedRequests<String, RuStoreDetails>()

    suspend fun details(pack: String, refresh: Boolean = false): RuStoreDetails {
        if (!refresh)
            cached(pack, AppPolicy.PLAY_CHECK_INTERVAL_MILLIS)?.let {
                return it
            }
        return pageRequests.await(pack) { fetchDetails(pack) }
    }

    internal suspend fun versions(packages: List<String>): RuStoreBatch {
        val requested = packages.distinct()
        require(requested.isNotEmpty() && requested.size <= AppPolicy.RUSTORE_BATCH_SIZE)
        // The package API rejects single-segment Android identifiers with HTTP 400.
        val names = requested.filter { '.' in it }
        if (names.isEmpty()) return RuStoreBatch(emptyMap(), requested.toSet())
        Log.d("RuStore", "Batch packages=${names.joinToString()}")
        val found =
            json.decodeFromString<List<RuStoreListing>>(
                request(
                    Request.Builder()
                        .url("https://backapi.rustore.ru/v2/showcase/store-app")
                        .post(
                            json
                                .encodeToString(RuStoreBatchRequest(names))
                                .toRequestBody("application/json".toMediaType()),
                        ),
                ),
            )
        check(
            found.all { it.packageName in names && it.versionCode > 0 } &&
                found.map { it.packageName }.distinct().size == found.size,
        ) {
            "RuStore: invalid batch response"
        }
        return RuStoreBatch(
            found.associateBy { it.packageName },
            requested.toSet() - found.map { it.packageName }.toSet(),
        )
    }

    private suspend fun fetchDetails(pack: String): RuStoreDetails {
        requireStorePackage(pack)
        val url =
            "https://backapi.rustore.ru/applicationData/overallInfo/"
                .toHttpUrl()
                .newBuilder()
                .addPathSegment(pack)
                .build()
        val revision = cacheOperations.withLock { cacheGeneration }
        val response =
            try {
                json.decodeFromString<DetailsResponse>(request(Request.Builder().url(url)))
            } catch (missing: RuStoreUnavailable) {
                withContext(ioDispatcher) {
                    cacheOperations.withLock {
                        cacheGeneration++
                        removedAt[pack] = cacheGeneration
                        database.users().deleteRuStoreCache(pack)
                    }
                }
                throw missing
            }
        val info = response.body
        if (response.code != "OK" || info == null)
            throw IOException("RuStore: invalid metadata response")
        check(info.packageName == pack && info.versionCode > 0) {
            "RuStore: package/version mismatch"
        }
        withContext(ioDispatcher) {
            cacheOperations.withLock {
                currentCoroutineContext().ensureActive()
                if (revision >= clearedAt && revision >= (removedAt[pack] ?: 0L))
                    database
                        .users()
                        .saveRuStoreCache(
                            RuStoreCacheRecord(
                                pack,
                                info.versionName,
                                info.versionCode,
                                now(),
                                json.encodeToString(info),
                            ),
                        )
            }
        }
        return info
    }

    private fun requireStorePackage(pack: String) {
        require(pack.isNotBlank() && '/' !in pack)
        // Package lookup rejects single-segment system identifiers with HTTP 400.
        // They cannot have a store entry; do not abort the remaining installed packages.
        if ('.' !in pack) throw RuStoreUnavailable()
    }

    suspend fun download(
        pack: String,
        density: Int,
        firstInstall: Boolean,
    ): Pair<RuStoreDetails, List<RuStoreDownload>> {
        val info = details(pack, refresh = true)
        val body =
            json.encodeToString(
                DownloadRequest(
                    info.appId,
                    firstInstall,
                    density,
                    Build.VERSION.SDK_INT,
                    Build.SUPPORTED_ABIS.toList(),
                    true,
                ),
            )
        var result =
            json.decodeFromString<DownloadResponse>(
                request(
                    Request.Builder()
                        .url("https://backapi.rustore.ru/v3/showcase/apps/download-link")
                        .post(body.toRequestBody("application/json".toMediaType())),
                ),
            )
        if (result.downloadUrls.isEmpty()) {
            result =
                json.decodeFromString<DownloadResponse>(
                    request(
                        Request.Builder()
                            .url("https://backapi.rustore.ru/v3/showcase/apps/download-link")
                            .post(
                                json
                                    .encodeToString(
                                        DownloadRequest(
                                            info.appId,
                                            firstInstall,
                                            density,
                                            Build.VERSION.SDK_INT,
                                            Build.SUPPORTED_ABIS.toList(),
                                            false,
                                        ),
                                    )
                                    .toRequestBody("application/json".toMediaType()),
                            ),
                    ),
                )
        }
        check(result.downloadUrls.isNotEmpty()) { "RuStore: empty delivery" }
        result.downloadUrls.forEach {
            check(it.url.toHttpUrl().isHttps && it.size > 0) { "RuStore: invalid APK response" }
        }
        return info to result.downloadUrls
    }

    suspend fun cacheSize(): Long =
        withContext(ioDispatcher) { database.users().ruStoreCacheBytes() }

    suspend fun clearCache(): Unit =
        withContext(ioDispatcher) {
            cacheOperations.withLock {
                cacheGeneration++
                clearedAt = cacheGeneration
                removedAt.clear()
                database.users().clearRuStoreCache()
            }
        }

    private suspend fun request(builder: Request.Builder): String = requests.withLock {
        val retryAt = settings.ruStoreRetryAt()
        if (retryAt > now()) throw SourceRateLimited(retryAt)
        delay((nextRequestAt - SystemClock.elapsedRealtime()).coerceAtLeast(0).milliseconds)
        currentCoroutineContext().ensureActive()
        nextRequestAt = SystemClock.elapsedRealtime() + AppPolicy.RUSTORE_REQUEST_INTERVAL_MILLIS
        try {
            execute(builder)
        } catch (limited: SourceRateLimited) {
            settings.postponeRuStore(limited.retryAt)
            throw limited
        }
    }

    private suspend fun execute(builder: Request.Builder): String =
        suspendCancellableCoroutine { continuation ->
            val request =
                builder
                    .header("deviceType", "mobile")
                    .header("ruStoreVerCode", AppPolicy.RUSTORE_CLIENT_CODE)
                    .header("ruStoreVerName", AppPolicy.RUSTORE_CLIENT_NAME)
                    .header("androidSdkVer", Build.VERSION.SDK_INT.toString())
                    .build()
            Log.d(
                "RuStore",
                "Start ${request.method} ${request.url.encodedPath} elapsed=${SystemClock.elapsedRealtime()}",
            )
            val call = client.newCall(request)
            call
                .timeout()
                .timeout(
                    AppPolicy.DETAILS_PLAY_TIMEOUT_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        Log.d(
                            "RuStore",
                            "HTTP ${call.request().method} ${call.request().url.encodedPath} status=${response.code}",
                        )
                        try {
                            val content = response.use {
                                val failure =
                                    when {
                                        it.code == HttpStatusCode.TooManyRequests.value ->
                                            rateLimit(it)
                                        it.code == HttpStatusCode.NotFound.value ->
                                            RuStoreUnavailable()
                                        !it.isSuccessful -> SourceHttpException(it.code)
                                        else -> null
                                    }
                                if (failure != null) throw failure
                                it.body.string()
                            }
                            if (continuation.isActive) continuation.resume(content)
                        } catch (failure: IOException) {
                            if (continuation.isActive) continuation.resumeWithException(failure)
                        }
                    }
                },
            )
        }

    internal fun rateLimit(response: Response): SourceRateLimited {
        val receivedAt = now()
        val duration =
            retryAfterMillis(response.header("Retry-After"), receivedAt)?.takeIf { it > 0 }
                ?: AppPolicy.RUSTORE_RETRY_DEFAULT_MILLIS
        return SourceRateLimited(receivedAt + duration.coerceAtMost(Long.MAX_VALUE - receivedAt))
    }
}
