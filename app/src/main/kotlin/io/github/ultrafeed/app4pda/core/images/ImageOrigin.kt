package io.github.ultrafeed.app4pda.core.images

import kotlinx.serialization.Serializable

/** The document owning an image, independent of the CDN serving its bytes. */
@Serializable
enum class ImageOrigin {
    FORUM,
    GOOGLE_PLAY,
    RUSTORE,
}

data class ImageCacheSettings(val enabled: Boolean, val limitBytes: Long) {
    companion object {
        val LIMITS_MIB = listOf(0, 100, 200, 500, 1000)
        const val DEFAULT_LIMIT_MIB = 100
    }
}
