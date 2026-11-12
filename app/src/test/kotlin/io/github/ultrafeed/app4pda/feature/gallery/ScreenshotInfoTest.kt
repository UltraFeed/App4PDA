package io.github.ultrafeed.app4pda.feature.gallery

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotInfoTest {
    @Test
    fun galleryPrefersFullImageWithoutMutatingPreview() {
        val image = ScreenshotInfo("preview", "full")
        assertThat(
            image.galleryUrl,
            `is`(
                "full",
            ),
        )
        assertThat(
            image.previewUrl,
            `is`(
                "preview",
            ),
        )
    }

    @Test
    fun missingFullImageFallsBackToPreview() {
        assertThat(
            ScreenshotInfo(previewUrl = "preview").galleryUrl,
            `is`(
                "preview",
            ),
        )
        assertThat(
            ScreenshotInfo().galleryUrl,
            nullValue(),
        )
    }

    @Test
    fun excludedImageKeepsItsDataWithoutParcelableOrUiState() {
        val image = ScreenshotInfo("preview", "full", true)
        assertTrue(image.excludedFromGallery)
        assertThat(
            image.copy(),
            `is`(
                image,
            ),
        )
    }
}
