package io.github.ultrafeed.app4pda.feature.gallery

data class ScreenshotInfo(
    val previewUrl: String? = null,
    val fullUrl: String? = null,
    val excludedFromGallery: Boolean = false,
) {
    val galleryUrl: String?
        get() = fullUrl ?: previewUrl
}
