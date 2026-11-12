package io.github.ultrafeed.app4pda.feature.installed.ui

import io.github.ultrafeed.app4pda.core.images.AppImageSource
import io.github.ultrafeed.app4pda.core.images.ImageOrigin
import io.github.ultrafeed.app4pda.core.packages.LocalPackage
import io.github.ultrafeed.app4pda.feature.installed.AppSection
import io.github.ultrafeed.app4pda.feature.installed.ForumApp
import io.github.ultrafeed.app4pda.feature.installed.InstalledApp
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class AppCardIconSourceTest {
    private val local = LocalPackage("App", "2", 2, 0, false, 24, 0)
    private val url = "https://example.org/icon.png"

    private fun app(installed: Boolean = true, icon: String = url) =
        InstalledApp(
            "example.app",
            if (installed) local else null,
            ForumApp(iconUrl = icon),
        )

    @Test
    fun installedListsAndUpdatesPreferTheVersionedPackageIcon() {
        listOf(
                AppSection.APPLICATIONS,
                AppSection.UNLINKED,
                AppSection.PENDING_LINKING,
                AppSection.FAVORITES,
                AppSection.UPDATES,
            )
            .forEach {
                assertThat(
                    appCardIconSource(app(), it),
                    `is`(
                        AppImageSource.PackageIcon(
                            "example.app",
                            2,
                        ),
                    ),
                )
            }
    }

    @Test
    fun catalogSearchAndHiddenKeepForumPriorityEvenForAnInstalledPackage() {
        listOf(null, AppSection.HIDDEN).forEach {
            assertThat(
                appCardIconSource(app(), it),
                `is`(
                    AppImageSource.Url(
                        url,
                        ImageOrigin.FORUM,
                    ),
                ),
            )
        }
    }

    @Test
    fun missingLocalPackageUsesForumIconInEverySection() {
        (AppSection.entries + listOf(null)).forEach {
            assertThat(
                appCardIconSource(app(false), it),
                `is`(
                    AppImageSource.Url(
                        url,
                        ImageOrigin.FORUM,
                    ),
                ),
            )
        }
    }

    @Test
    fun absentOrInvalidForumAddressFallsBackToLocalIconOrPlaceholder() {
        listOf("", "javascript:invalid").forEach { icon ->
            (AppSection.entries + listOf(null)).forEach { section ->
                assertThat(
                    appCardIconSource(app(icon = icon), section),
                    `is`(
                        AppImageSource.PackageIcon(
                            "example.app",
                            2,
                        ),
                    ),
                )
                assertThat(
                    appCardIconSource(app(false, icon), section),
                    nullValue(),
                )
            }
        }
    }
}
