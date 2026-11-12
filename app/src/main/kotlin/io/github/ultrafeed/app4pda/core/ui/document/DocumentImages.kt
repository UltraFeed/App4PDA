package io.github.ultrafeed.app4pda.core.ui.document

import android.content.Context
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import coil3.network.HttpException
import coil3.request.Options
import io.github.ultrafeed.app4pda.core.images.AppImageSource
import io.github.ultrafeed.app4pda.core.images.AppImages
import io.github.ultrafeed.app4pda.core.images.ImageOrigin
import io.github.ultrafeed.app4pda.core.images.imageRequest
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Only document image URLs enter Coil. WebView never maintains an independent image cache. */
internal class DocumentImages(
    private val context: Context,
    private val images: AppImages,
    private val origin: ImageOrigin,
    allowedImages: Set<String>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AutoCloseable {
    private val job = SupervisorJob()
    private val streams = mutableSetOf<InputStream>()
    private val allowed = allowedImages.mapNotNull { it.toHttpUrlOrNull()?.toString() }.toSet()

    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString().toHttpUrlOrNull()?.toString()
        if (url == null || !isDocumentImage(request, url)) return null
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "Document image fetch must run on WebView's resource thread"
        }
        return try {
            runBlocking(ioDispatcher + job) {
                val source = AppImageSource.Url(url, origin)
                val requestOptions = imageRequest(context, source)
                val result =
                    images.fetch(
                        source,
                        Options(
                            context,
                            diskCacheKey = requestOptions.diskCacheKey,
                            extras = requestOptions.extras,
                        ),
                    )
                val closed = AtomicBoolean()
                val stream =
                    object : FilterInputStream(result.source.source().inputStream()) {
                        override fun close() {
                            if (closed.compareAndSet(false, true)) {
                                try {
                                    super.close()
                                } finally {
                                    try {
                                        result.source.close()
                                    } finally {
                                        synchronized(streams) { streams.remove(this) }
                                    }
                                }
                            }
                        }
                    }
                synchronized(streams) {
                    if (job.isActive) streams.add(stream)
                    else {
                        stream.close()
                        throw CancellationException("Document closed")
                    }
                }
                WebResourceResponse(result.mimeType, null, stream).apply {
                    responseHeaders = mapOf("Cache-Control" to "no-store")
                }
            }
        } catch (_: IOException) {
            unavailable()
        } catch (_: HttpException) {
            unavailable()
        } catch (_: CancellationException) {
            unavailable()
        }
    }

    private fun isDocumentImage(request: WebResourceRequest, url: String): Boolean =
        !request.isForMainFrame && request.method == "GET" && url in allowed

    override fun close() {
        job.cancel()
        var failure: IOException? = null
        synchronized(streams) { streams.toList() }
            .forEach {
                try {
                    it.close()
                } catch (error: IOException) {
                    if (failure == null) failure = error else failure.addSuppressed(error)
                }
            }
        failure?.let { throw it }
    }

    private fun unavailable() =
        WebResourceResponse(
            "image/*",
            null,
            io.ktor.http.HttpStatusCode.ServiceUnavailable.value,
            "Image unavailable",
            emptyMap(),
            ByteArrayInputStream(byteArrayOf()),
        )
}
