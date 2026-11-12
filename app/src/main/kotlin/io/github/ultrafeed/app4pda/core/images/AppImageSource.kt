package io.github.ultrafeed.app4pda.core.images

import android.content.Context
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import io.github.ultrafeed.app4pda.data.forum.ForumUrls
import io.github.ultrafeed.app4pda.data.forum.forumUserAgent
import io.github.ultrafeed.app4pda.feature.auth.ForumCaptcha
import java.io.File
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed interface AppImageSource {
    data class PackageIcon(val packageName: String, val versionCode: Long = 0) : AppImageSource

    data class ApkIcon(val path: String, val lastModified: Long) : AppImageSource

    data class LocalFile(val path: String, val lastModified: Long) : AppImageSource

    data class Url(val value: String, val origin: ImageOrigin) : AppImageSource
}

/** One remote topic icon shares its disk entry between prefetch and every card context. */
internal fun forumIconCacheKey(topic: Int) = "forum-icon:$topic"

internal fun imageRequest(
    context: Context,
    source: AppImageSource,
    cacheKey: String? = null,
    cacheEnabled: Boolean = true,
): ImageRequest {
    val model: Any =
        when (source) {
            is AppImageSource.LocalFile -> File(source.path)
            is AppImageSource.Url -> source.copy(value = ForumUrls.normalize(source.value))
            else -> source
        }
    val key =
        if (model is AppImageSource.Url) "${model.origin}:${model.value}:${cacheKey.orEmpty()}"
        else "$model:${cacheKey.orEmpty()}"
    val policy = if (cacheEnabled) CachePolicy.ENABLED else CachePolicy.DISABLED
    return ImageRequest.Builder(context)
        .data(model)
        .memoryCacheKey(key)
        .diskCacheKey(key)
        .memoryCachePolicy(policy)
        .diskCachePolicy(policy)
        .apply {
            val host = (model as? AppImageSource.Url)?.value?.toHttpUrlOrNull()?.host
            if (host == ForumUrls.HOST || host?.endsWith(".${ForumUrls.HOST}") == true) {
                httpHeaders(
                    NetworkHeaders.Builder()
                        .set(
                            "User-Agent",
                            forumUserAgent(context),
                        )
                        .build(),
                )
            }
        }
        .build()
}

/** Captcha cookies are scoped by Set-Cookie; CF browser cookies never enter Coil. */
internal fun captchaImageRequest(
    context: Context,
    captcha: ForumCaptcha,
): ImageRequest {
    val url = requireNotNull(captcha.imageUrl).toHttpUrlOrNull()
    val headers = NetworkHeaders.Builder().set("User-Agent", forumUserAgent(context))
    if (
        url?.isHttps == true &&
            url.host == ForumUrls.HOST &&
            url.port == HttpUrl.defaultPort("https")
    ) {
        val cookies =
            captcha.imageCookies.filter {
                it.expiresAt > System.currentTimeMillis() && it.matches(url)
            }
        if (cookies.isNotEmpty())
            headers["Cookie"] = cookies.joinToString("; ") { "${it.name}=${it.value}" }
    }
    return imageRequest(
            context,
            AppImageSource.Url(requireNotNull(captcha.imageUrl), ImageOrigin.FORUM),
            cacheEnabled = false,
        )
        .newBuilder()
        .httpHeaders(headers.build())
        .build()
}
