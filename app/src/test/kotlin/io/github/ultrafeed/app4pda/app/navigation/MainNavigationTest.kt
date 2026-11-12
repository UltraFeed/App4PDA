package io.github.ultrafeed.app4pda.app.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.github.ultrafeed.app4pda.feature.installed.AppSection
import io.github.ultrafeed.app4pda.feature.settings.SettingsPage
import kotlinx.serialization.json.Json
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainNavigationTest {
    private fun navigation() =
        MainNavigation(
            mutableStateOf(MainSection.HOME),
            MainSection.entries.associateWith { NavBackStack<NavKey>(AppEntry(it.root)) },
        )

    @Test
    fun sectionsHaveTheRequestedOrderAndDistinctRootInstances() {
        val nav = navigation()
        assertThat(
            MainSection.entries.map { it.name },
            CoreMatchers.`is`(
                listOf(
                    "HOME",
                    "DIGEST",
                    "CATALOG",
                    "DOWNLOADS",
                    "SETTINGS",
                ),
            ),
        )
        assertThat(
            nav.stacks.values.map { (it.first() as AppEntry).id }.toSet().size,
            CoreMatchers.`is`(
                5,
            ),
        )
    }

    @Test
    fun switchingRetainsEachStackAndReselectOnlyRemovesItsChildren() {
        val nav = navigation()
        nav.select(MainSection.CATALOG)
        val root = nav.current
        nav.open(AppDestination.Catalog(category = 316))
        nav.open(AppDestination.Details(42, null))
        val details = nav.current
        nav.select(MainSection.SETTINGS)
        nav.open(
            AppDestination.Settings(
                SettingsPage.APPEARANCE,
            ),
        )
        val settings = nav.current
        nav.select(MainSection.CATALOG)
        assertThat(
            nav.current,
            CoreMatchers.sameInstance(
                details,
            ),
        )
        nav.select(MainSection.CATALOG)
        assertThat(
            nav.current,
            CoreMatchers.sameInstance(
                root,
            ),
        )
        assertThat(
            nav.stack.size,
            CoreMatchers.`is`(
                1,
            ),
        )
        nav.select(MainSection.SETTINGS)
        assertThat(
            nav.current,
            CoreMatchers.sameInstance(
                settings,
            ),
        )
    }

    @Test
    fun backPopsDialogsAndScreensThenReturnsToHomeBeforeExiting() {
        val nav = navigation()
        nav.open(
            AppDestination.Apps(
                AppSection.UPDATES,
            ),
        )
        nav.select(MainSection.SETTINGS)
        nav.open(
            AppDestination.Settings(
                SettingsPage.ACCOUNTS,
            ),
        )
        nav.open(AppDestination.Auth())
        assertTrue(nav.back())
        assertThat(
            nav.current.destination,
            CoreMatchers.`is`(
                AppDestination.Settings(
                    SettingsPage.ACCOUNTS,
                ),
            ),
        )
        assertTrue(nav.back())
        assertThat(
            nav.current.destination,
            CoreMatchers.`is`(
                AppDestination.Settings(),
            ),
        )
        assertTrue(nav.back())
        assertThat(
            nav.current.destination,
            CoreMatchers.`is`(
                AppDestination.Home,
            ),
        )
        assertFalse(nav.back())
    }

    @Test
    fun normalSearchMovesToCatalogButManualSearchKeepsItsCaller() {
        val nav = navigation()
        nav.open(AppDestination.Catalog(filter = "Авито"))
        assertThat(
            nav.selected,
            CoreMatchers.`is`(
                MainSection.CATALOG,
            ),
        )
        nav.select(MainSection.SETTINGS)
        nav.open(
            AppDestination.Apps(
                AppSection.HIDDEN,
            ),
        )
        val caller = nav.current
        nav.open(AppDestination.Catalog(filter = "Calendar", linked = "example.calendar"))
        assertThat(
            nav.selected,
            CoreMatchers.`is`(
                MainSection.SETTINGS,
            ),
        )
        assertThat(
            nav.current.callerId,
            CoreMatchers.`is`(
                caller.id,
            ),
        )
    }

    @Test
    fun incomingLinksAndDownloadNotificationsChooseTheirSectionOnce() {
        val nav = navigation()
        nav.external(AppDestination.Details(42, null))
        assertThat(
            nav.selected,
            CoreMatchers.`is`(
                MainSection.CATALOG,
            ),
        )
        assertThat(
            nav.current.destination,
            CoreMatchers.`is`(
                AppDestination.Details(
                    42,
                    null,
                ),
            ),
        )
        nav.external(AppDestination.Downloads)
        val root = nav.current
        nav.external(AppDestination.Downloads)
        assertThat(
            nav.current,
            CoreMatchers.sameInstance(
                root,
            ),
        )
        assertThat(
            nav.stack.size,
            CoreMatchers.`is`(
                1,
            ),
        )
        assertThat(
            nav.stacks.getValue(MainSection.CATALOG).size,
            CoreMatchers.`is`(
                2,
            ),
        )
    }

    @Test
    fun sameArgumentsInDifferentSectionsNeverShareEntryIdentity() {
        val nav = navigation()
        nav.open(AppDestination.Details(42, null))
        val first = nav.current
        nav.select(MainSection.CATALOG)
        nav.open(first.destination)
        assertThat(
            nav.current.id,
            CoreMatchers.not(
                CoreMatchers.`is`(
                    first.id,
                ),
            ),
        )
    }

    @Test
    fun serializedStacksRetainIdsCallersAndUnconsumedResults() {
        val nav = navigation()
        nav.select(MainSection.CATALOG)
        nav.open(AppDestination.Post(42, 12, "Post"))
        val caller = nav.current
        nav.open(AppDestination.Attachment(42, "https://example.org/file.zip"))
        assertTrue(nav.stack.complete(nav.current.id, ScreenResult.DownloadStarted))
        val original = nav.stacks.mapValues { (_, entries) -> entries.map { it as AppEntry } }
        val restored =
            Json.decodeFromString<Map<MainSection, List<AppEntry>>>(Json.encodeToString(original))
        val copy =
            MainNavigation(
                mutableStateOf(nav.selected),
                restored.mapValues { (_, entries) ->
                    NavBackStack<NavKey>(*entries.toTypedArray())
                },
            )
        assertThat(
            copy.current.id,
            CoreMatchers.`is`(
                caller.id,
            ),
        )
        assertThat(
            copy.stack.consumeResult(caller.id),
            CoreMatchers.`is`(
                ScreenResult.DownloadStarted,
            ),
        )
        assertThat(
            copy.stack.consumeResult(caller.id),
            CoreMatchers.nullValue(),
        )
        copy.select(MainSection.CATALOG)
        assertThat(
            copy.stack.size,
            CoreMatchers.`is`(
                1,
            ),
        )
    }

    @Test
    fun aRemovedCallerNeverReceivesALateResult() {
        val nav = navigation()
        nav.open(AppDestination.Post(42, 12, "Post"))
        nav.open(AppDestination.Attachment(42, "https://example.org/file.zip"))
        val sender = nav.current
        nav.stack.removeAt(1)
        assertTrue(nav.stack.complete(sender.id, ScreenResult.DownloadStarted))
        assertThat(
            nav.current.result,
            CoreMatchers.nullValue(),
        )
    }
}
