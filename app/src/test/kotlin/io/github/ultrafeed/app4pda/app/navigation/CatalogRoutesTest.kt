package io.github.ultrafeed.app4pda.app.navigation

import androidx.navigation3.runtime.NavKey
import io.github.ultrafeed.app4pda.feature.installed.AppSection
import io.github.ultrafeed.app4pda.feature.settings.SettingsPage
import kotlinx.serialization.json.Json
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRoutesTest {
    @Test
    fun allDestinationsAndPendingResultsAreSerializable() {
        val destinations =
            listOf(
                AppDestination.Home,
                AppDestination.Empty,
                AppDestination.Categories,
                AppDestination.Downloads,
                AppDestination.Auth(),
                AppDestination.Apps(
                    AppSection.UPDATES,
                ),
                AppDestination.Catalog(42, 9, "Игры & tools", "", "test.package"),
                AppDestination.Details(42, "test.package", "linked"),
                AppDestination.Digest,
                AppDestination.Settings(
                    SettingsPage.APPEARANCE,
                ),
                AppDestination.Lookup("test.package"),
                AppDestination.Post(42, 21, "FAQ & помощь"),
                AppDestination.Attachment(42, "https://4pda.to/forum/dl/post/21/file.apk?a=b&x=2"),
            )
        for (destination in destinations) {
            val entry =
                AppEntry(destination, callerId = "caller").apply {
                    result = ScreenResult.DownloadStarted
                }
            val restored = Json.decodeFromString<AppEntry>(Json.encodeToString(entry))
            assertThat(
                restored,
                CoreMatchers.`is`(
                    entry,
                ),
            )
            assertThat(
                restored.result,
                CoreMatchers.`is`(
                    entry.result,
                ),
            )
        }
    }

    @Test
    fun topicAndCategoryArgumentsRoundTripWithoutUrlEncoding() {
        val entry =
            AppEntry(AppDestination.Catalog(topic = 42, category = 9, name = "Games & tools/Игры"))
        assertThat(
            Json.decodeFromString<AppEntry>(Json.encodeToString(entry)),
            CoreMatchers.`is`(
                entry,
            ),
        )
    }

    @Test
    fun emptySearchDiffersFromAbsentAndPreservesBindingContext() {
        val search = AppDestination.Catalog(filter = "", linked = "example.package")
        assertThat(
            search.filter,
            CoreMatchers.`is`(
                "",
            ),
        )
        assertThat(AppDestination.Catalog().filter, CoreMatchers.nullValue())
        assertThat(
            search.linked,
            CoreMatchers.`is`(
                "example.package",
            ),
        )
    }

    @Test
    fun duplicateDestinationsHaveIndependentSavedIdentities() {
        val stack = mutableListOf<NavKey>(AppEntry(AppDestination.Home))
        repeat(2) { stack.push(AppDestination.Details(42, "example")) }
        val first = stack[1] as AppEntry
        val second = stack[2] as AppEntry
        assertThat(
            second.destination,
            CoreMatchers.`is`(
                first.destination,
            ),
        )
        assertThat(
            second.id,
            CoreMatchers.not(
                CoreMatchers.`is`(
                    first.id,
                ),
            ),
        )
        assertThat(
            second.callerId,
            CoreMatchers.`is`(
                first.id,
            ),
        )
        assertTrue(stack.pop())
        assertThat(
            stack.last(),
            CoreMatchers.`is`(
                first,
            ),
        )
    }

    @Test
    fun resultsAreAddressedSavedAndConsumedOnceOnlyByTheActiveReceiver() {
        val root = AppEntry(AppDestination.Home)
        val stack = mutableListOf<NavKey>(root)
        stack.push(AppDestination.Auth())
        val sender = stack.last() as AppEntry
        assertTrue(stack.complete(sender.id, ScreenResult.AuthChanged))
        assertThat(
            "Result changes do not replace the entry identity",
            stack.last(),
            CoreMatchers.sameInstance(
                root,
            ),
        )
        val restored =
            Json.decodeFromString<AppEntry>(Json.encodeToString(stack.last() as AppEntry))
        val savedStack = mutableListOf<NavKey>(restored)
        assertThat(savedStack.consumeResult("wrong-id"), CoreMatchers.nullValue())
        assertThat(
            savedStack.consumeResult(root.id),
            CoreMatchers.`is`(
                ScreenResult.AuthChanged,
            ),
        )
        assertThat(savedStack.consumeResult(root.id), CoreMatchers.nullValue())
        assertFalse(savedStack.pop())
    }

    @Test
    fun lateResultsCannotReachAnotherOrRemovedReceiver() {
        val stack = mutableListOf<NavKey>(AppEntry(AppDestination.Home))
        stack.push(AppDestination.Auth())
        val sender = stack.last() as AppEntry
        stack.push(AppDestination.Downloads)
        Assert.assertFalse(stack.complete(sender.id, ScreenResult.AuthChanged))
        stack.pop()
        stack.removeAt(0)
        Assert.assertFalse(stack.complete(sender.id, ScreenResult.AuthChanged))
        assertThat((stack.last() as AppEntry).result, CoreMatchers.nullValue())
    }
}
