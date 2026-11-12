package io.github.ultrafeed.app4pda.core.images

import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.key.Keyer
import coil3.memory.MemoryCache
import coil3.network.DeDupeConcurrentRequestStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.toUri
import io.github.ultrafeed.app4pda.core.packages.PackageInspector
import io.github.ultrafeed.app4pda.data.forum.ForumUrls
import io.github.ultrafeed.app4pda.data.forum.forumAccessEvent
import io.github.ultrafeed.app4pda.data.preferences.AppSettings
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel as cancelScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okio.buffer

/** Application owner; Coil owns source-specific journals, fetching, decoding and eviction. */
class AppImages(
    private val context: Context,
    packages: PackageInspector,
    client: OkHttpClient,
    private val settings: AppSettings,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AutoCloseable {
    private val caches =
        ImageOrigin.entries.associateWith { SourceImageCache(context, it, ioDispatcher) }

    @OptIn(ExperimentalCoilApi::class)
    private val network =
        ImageOrigin.entries.associateWith { origin ->
            OkHttpNetworkFetcherFactory(
                callFactory = { imageHttpClient(context, client) },
                cacheStrategy = {
                    SourceImageCachePolicy { settings.awaitReady().imageCache(origin).enabled }
                },
                concurrentRequestStrategy = { DeDupeConcurrentRequestStrategy() },
            )
        }
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var closed = false
    private val generation = AtomicLong()
    private val requests = ConcurrentHashMap.newKeySet<Job>()
    private val clearing = MutableStateFlow(false)
    private val imageLoader = lazy {
        ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context).build() }
            .diskCache(null)
            .components {
                add { chain ->
                    val revision = generation.get()
                    val remote = chain.request.data as? AppImageSource.Url
                    val configuration = remote?.let {
                        settings.awaitReady().imageCache(it.origin)
                    }
                    if (remote != null && configuration?.enabled == true) {
                        caches
                            .getValue(remote.origin)
                            .acquire { settings.awaitReady().imageCache(remote.origin) }
                            .use { lease ->
                                if (lease?.expired == true)
                                    loader.memoryCache?.let { memory ->
                                        memory.keys
                                            .filter { it.key.startsWith("${remote.origin}:") }
                                            .forEach(memory::remove)
                                    }
                            }
                    }
                    val request =
                        if (configuration?.enabled == false)
                            chain.request
                                .newBuilder()
                                .memoryCachePolicy(CachePolicy.DISABLED)
                                .build()
                        else chain.request
                    val result = chain.withRequest(request).proceed()
                    if (
                        remote != null &&
                            (revision != generation.get() ||
                                !settings.awaitReady().imageCache(remote.origin).enabled)
                    )
                        (result as? SuccessResult)?.memoryCacheKey?.let {
                            loader.memoryCache?.remove(it)
                        }
                    result
                }
                add(
                    Fetcher.Factory<AppImageSource.Url> { source, options, _ ->
                        Fetcher { fetch(source, options) }
                    },
                )
                add(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                        AnimatedImageDecoder.Factory()
                    else GifDecoder.Factory(),
                )
                add(Keyer<AppImageSource> { data, _ -> "$data" })
                add(localIconFetcher(packages, ioDispatcher))
            }
            .build()
    }
    val loader: ImageLoader
        get() =
            synchronized(this) {
                check(!closed) { "Image loader is closed" }
                imageLoader.value
            }

    init {
        scope.launch {
            settings.changes
                .map { values -> ImageOrigin.entries.associateWith(values::imageCache) }
                .distinctUntilChanged()
                .collect { sizes() }
        }
    }

    suspend fun sizes(): Map<ImageOrigin, Long?> =
        ImageOrigin.entries.associateWith { origin ->
            try {
                val cache = caches.getValue(origin)
                cache.resize { settings.awaitReady().imageCache(origin) }
                cache.size()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                null
            } catch (_: IllegalStateException) {
                null
            } catch (_: SecurityException) {
                null
            }
        }

    internal suspend fun fetch(
        source: AppImageSource.Url,
        options: Options,
    ): SourceFetchResult {
        clearing.first { !it }
        val revision = generation.get()
        val owner = checkNotNull(currentCoroutineContext()[Job])
        val configuration = settings.awaitReady().imageCache(source.origin)
        val lease =
            if (configuration.enabled)
                caches.getValue(source.origin).acquire {
                    settings.awaitReady().imageCache(source.origin)
                } ?: ImageCacheLease(loader, false) {}
            else ImageCacheLease(loader, false) {}
        requests.add(owner)
        var transferred = false
        var fetched: ImageSource? = null
        try {
            if (clearing.value || revision != generation.get())
                throw CancellationException("Image cache cleared")
            val effective =
                options.copy(
                    diskCachePolicy =
                        if (configuration.enabled) options.diskCachePolicy
                        else CachePolicy.DISABLED,
                )
            val fetcher =
                checkNotNull(
                    network
                        .getValue(source.origin)
                        .create(
                            ForumUrls.normalize(source.value).toUri(),
                            effective,
                            lease.loader,
                        ),
                )
            val result = fetcher.fetch() as SourceFetchResult
            val original = result.source
            fetched = original
            val wrapped = leasedImageSource(original, lease)
            transferred = true
            return SourceFetchResult(wrapped, result.mimeType, result.dataSource)
        } finally {
            requests.remove(owner)
            if (!transferred) {
                lease.use {
                    fetched?.close()
                }
            }
        }
    }

    suspend fun clear() {
        clearing.value = true
        try {
            generation.incrementAndGet()
            requests.forEach {
                it.cancel(CancellationException("Image cache cleared"))
            }
            if (imageLoader.isInitialized()) loader.memoryCache?.clear()
            val values = settings.awaitReady()
            caches.forEach { (origin, cache) -> cache.clear(values.imageCache(origin)) }
            File(context.cacheDir, "images")
                .listFiles()
                .orEmpty()
                .filter { it.isFile }
                .forEach {
                    check(it.delete() || !it.exists()) { "Cannot clear image cache files" }
                }
        } finally {
            clearing.value = false
        }
    }

    @Synchronized
    override fun close() {
        if (!closed) {
            closed = true
            scope.cancelScope()
            if (imageLoader.isInitialized()) imageLoader.value.shutdown()
            caches.values.forEach(SourceImageCache::close)
        }
    }
}

internal fun imageHttpClient(context: Context, client: OkHttpClient): OkHttpClient {
    return client
        .newBuilder()
        .addNetworkInterceptor { chain ->
            val original = chain.call().request().url
            val current = chain.request().url
            val sameOrigin =
                original.scheme == current.scheme &&
                    original.host == current.host &&
                    original.port == current.port
            chain
                .proceed(
                    if (sameOrigin) chain.request()
                    else chain.request().newBuilder().removeHeader("Cookie").build(),
                )
                .also {
                    if (current.host == ForumUrls.HOST || current.host == "turing.4pda.to")
                        forumAccessEvent(
                            context,
                            "image-http",
                            "$current",
                            it.code,
                        )
                }
        }
        .build()
}

internal fun localIconFetcher(
    packages: PackageInspector,
    ioDispatcher: CoroutineDispatcher,
): Fetcher.Factory<AppImageSource> = Fetcher.Factory { source, _, _ ->
    if (source !is AppImageSource.PackageIcon && source !is AppImageSource.ApkIcon) null
    else
        Fetcher {
            val drawable =
                withContext(ioDispatcher) {
                    when (source) {
                        is AppImageSource.PackageIcon ->
                            packages.artifacts.loadPackageIcon(source.packageName)

                        is AppImageSource.ApkIcon ->
                            packages.artifacts.readApk(source.path, 0)?.let { info ->
                                info.applicationInfo?.apply {
                                    sourceDir = source.path
                                    publicSourceDir = source.path
                                }
                                packages.artifacts.loadApplicationIcon(info)
                            }
                    }
                }
            ImageFetchResult(
                requireNotNull(drawable) { "Application icon is unavailable" }.asImage(),
                false,
                DataSource.DISK,
            )
        }
}

internal fun leasedImageSource(
    original: ImageSource,
    lease: ImageCacheLease,
): ImageSource {
    val close = AutoCloseable {
        lease.use {
            original.close()
        }
    }
    val file = original.fileOrNull()
    return if (file != null) ImageSource(file, original.fileSystem, closeable = close)
    else
        ImageSource(
            object : okio.ForwardingSource(original.source()) {
                    override fun close() {
                        try {
                            super.close()
                        } finally {
                            close.close()
                        }
                    }
                }
                .buffer(),
            original.fileSystem,
        )
}
