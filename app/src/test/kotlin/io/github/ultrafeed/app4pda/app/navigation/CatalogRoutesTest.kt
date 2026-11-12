package io.github.ultrafeed.app4pda.app.navigation

import androidx.navigation3.runtime.NavKey
import io.github.ultrafeed.app4pda.feature.installed.AppSection
import io.github.ultrafeed.app4pda.feature.settings.SettingsPage
import kotlinx.serialization.json.Json
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
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
                `is`(
                    entry,
                ),
            )
            assertThat(
                restored.result,
                `is`(
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
            `is`(
                entry,
            ),
        )
    }

    @Test
    fun emptySearchDiffersFromAbsentAndPreservesBindingContext() {
        val search = AppDestination.Catalog(filter = "", linked = "example.package")
        assertThat(
            search.filter,
            `is`(
                "",
            ),
        )
        assertThat(AppDestination.Catalog().filter, CoreMatchers.nullValue())
        assertThat(
            search.linked,
            `is`(
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
            `is`(
                first.destination,
            ),
        )
        assertThat(
            second.id,
            CoreMatchers.not(
                `is`(
                    first.id,
                ),
            ),
        )
        assertThat(
            second.callerId,
            `is`(
                first.id,
            ),
        )
        assertThat(stack.pop(), `is`(true))
        assertThat(
            stack.last(),
            `is`(
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
        assertThat(stack.complete(sender.id, ScreenResult.AuthChanged), `is`(true))
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
            `is`(
                ScreenResult.AuthChanged,
            ),
        )
        assertThat(savedStack.consumeResult(root.id), CoreMatchers.nullValue())
        assertThat(savedStack.pop(), `is`(false))
    }

    @Test
    fun lateResultsCannotReachAnotherOrRemovedReceiver() {
        val stack = mutableListOf<NavKey>(AppEntry(AppDestination.Home))
        stack.push(AppDestination.Auth())
        val sender = stack.last() as AppEntry
        stack.push(AppDestination.Downloads)
        assertThat(stack.complete(sender.id, ScreenResult.AuthChanged), `is`(false))
        stack.pop()
        stack.removeAt(0)
        assertThat(stack.complete(sender.id, ScreenResult.AuthChanged), `is`(false))
        assertThat((stack.last() as AppEntry).result, CoreMatchers.nullValue())
    }
}
