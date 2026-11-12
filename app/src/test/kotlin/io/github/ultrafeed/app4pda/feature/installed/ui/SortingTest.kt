package io.github.ultrafeed.app4pda.feature.installed.ui

import io.github.ultrafeed.app4pda.core.packages.LocalPackage
import io.github.ultrafeed.app4pda.feature.installed.AppSection
import io.github.ultrafeed.app4pda.feature.installed.ForumApp
import io.github.ultrafeed.app4pda.feature.installed.InstalledApp
import io.github.ultrafeed.app4pda.feature.installed.InstalledSort
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class SortingTest {
    @Test
    fun storageCodesAreExplicitAndInvalidCodesUseTheExistingDefault() {
        assertThat(
            InstalledSort.entries.map { it.storageValue },
            `is`(
                listOf(
                    0,
                    1,
                    2,
                    3,
                ),
            ),
        )
        InstalledSort.entries.forEach {
            assertThat(
                InstalledSort.fromStorage(it.storageValue),
                sameInstance(
                    it,
                ),
            )
        }
        for (invalid in listOf(-1, 4, Int.MAX_VALUE)) {
            assertThat(
                InstalledSort.fromStorage(invalid),
                sameInstance(
                    InstalledSort.NAME,
                ),
            )
        }
    }

    @Test
    fun installedOrdersPreserveUpdatePriorityDateNameAndKeyTies() {
        fun app(key: String, name: String, date: Long, update: Boolean) =
            InstalledApp(
                key,
                LocalPackage(name, "1", 1, 0, false, 24, 0),
                ForumApp(42, name, if (update) "2" else "1", updatedAt = date),
            )

        val rows =
            listOf(
                app("z", "alpha", 100, false),
                app("b", "Alpha", 100, false),
                app("u", "Zulu", 10, true),
                app("n", "Beta", 200, false),
            )

        fun keys(sort: InstalledSort) =
            InstalledAppsViewModel.filterApps(rows, AppSection.APPLICATIONS, sort).map {
                it.key
            }
        assertThat(
            keys(InstalledSort.UPDATED),
            `is`(
                listOf(
                    "n",
                    "b",
                    "z",
                    "u",
                ),
            ),
        )
        assertThat(
            keys(InstalledSort.NAME),
            `is`(
                listOf(
                    "b",
                    "z",
                    "n",
                    "u",
                ),
            ),
        )
        assertThat(
            keys(InstalledSort.UPDATES_BY_DATE),
            `is`(
                listOf(
                    "u",
                    "n",
                    "b",
                    "z",
                ),
            ),
        )
        assertThat(
            keys(InstalledSort.UPDATES_BY_NAME),
            `is`(
                listOf(
                    "u",
                    "b",
                    "z",
                    "n",
                ),
            ),
        )
    }
}
