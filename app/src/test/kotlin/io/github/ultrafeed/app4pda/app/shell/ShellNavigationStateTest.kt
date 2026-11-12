package io.github.ultrafeed.app4pda.app.shell

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.github.ultrafeed.app4pda.app.navigation.AppDestination
import io.github.ultrafeed.app4pda.app.navigation.AppDestination.Gallery
import io.github.ultrafeed.app4pda.app.navigation.AppDestination.Home
import io.github.ultrafeed.app4pda.app.navigation.AppDestination.Settings
import io.github.ultrafeed.app4pda.app.navigation.AppEntry
import io.github.ultrafeed.app4pda.app.navigation.pop
import io.github.ultrafeed.app4pda.app.navigation.push
import io.github.ultrafeed.app4pda.core.images.ImageOrigin
import io.github.ultrafeed.app4pda.feature.details.DetailsTab
import kotlinx.serialization.json.Json
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellNavigationStateTest {
    @Test
    fun activityReferenceUsesActualActiveStackAndDetaches() {
        val navigation = ShellNavigationState()
        val stack =
            NavBackStack<NavKey>(
                AppEntry(
                    Home,
                ),
            )
        navigation.stack = stack
        stack.push(
            AppDestination.Settings(),
        )
        assertThat(
            navigation.stack,
            `is`(
                stack,
            ),
        )
        navigation.detach()
        assertThat(
            navigation.stack,
            nullValue(),
        )
    }

    @Test
    fun galleryRestoresImagesAndPageWithoutAnotherActivity() {
        val storePage: AppDestination =
            AppDestination.Details(0, "org.example.app", initialSource = DetailsTab.RUSTORE)
        assertThat(
            Json.decodeFromString<AppDestination>(Json.encodeToString(storePage)),
            `is`(storePage),
        )
        val original: AppDestination =
            AppDestination.Gallery(
                listOf(
                    "https://example.org/1.png",
                    "https://example.org/2.png",
                ),
                ImageOrigin.FORUM,
                1,
            )
        val restored =
            Json.decodeFromString<AppDestination>(
                Json.encodeToString(
                    original,
                ),
            )
        assertThat(
            restored,
            `is`(
                original,
            ),
        )
        val stack =
            NavBackStack<NavKey>(
                AppEntry(
                    Home,
                ),
            )
        stack.push(
            restored,
        )
        assertThat(
            (stack.last() as AppEntry).destination,
            `is`(
                restored,
            ),
        )
        assertTrue(
            stack.pop(),
        )
        assertThat(
            (stack.last() as AppEntry).destination,
            `is`(
                Home,
            ),
        )
    }
}
