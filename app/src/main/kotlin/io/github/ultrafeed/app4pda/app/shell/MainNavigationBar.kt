package io.github.ultrafeed.app4pda.app.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.app.navigation.MainSection
import io.github.ultrafeed.app4pda.data.preferences.AppSettings
import org.koin.compose.koinInject

@Composable
internal fun MainNavigationBar(selected: MainSection, onSelect: (MainSection) -> Unit) {
    val settings: AppSettings = koinInject()
    val values by settings.changes.collectAsState(initial = settings.current)
    val hideLabels = values.getBoolean("hide_navigation_labels")
    NavigationBar(Modifier) {
        MainSection.entries.forEach { section ->
            val title =
                stringResource(
                    when (section) {
                        MainSection.HOME -> R.string.home
                        MainSection.DIGEST -> R.string.digest
                        MainSection.CATALOG -> R.string.catalog
                        MainSection.DOWNLOADS -> R.string.downloads
                        MainSection.SETTINGS -> R.string.settings
                    },
                )
            NavigationBarItem(
                selected = selected == section,
                onClick = { onSelect(section) },
                icon = {
                    Icon(
                        when (section) {
                            MainSection.HOME -> Icons.Rounded.Home
                            MainSection.DIGEST -> Icons.Rounded.NewReleases
                            MainSection.CATALOG -> Icons.Rounded.Apps
                            MainSection.DOWNLOADS -> Icons.Rounded.Download
                            MainSection.SETTINGS -> Icons.Rounded.Settings
                        },
                        if (hideLabels) title else null,
                    )
                },
                label =
                    if (hideLabels) null
                    else {
                        { Text(title) }
                    },
            )
        }
    }
}
