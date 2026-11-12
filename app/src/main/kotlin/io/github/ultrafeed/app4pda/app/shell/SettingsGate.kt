package io.github.ultrafeed.app4pda.app.shell

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.appearance.AppearanceDefaults
import io.github.ultrafeed.app4pda.core.appearance.applyLauncherIcon
import io.github.ultrafeed.app4pda.core.ui.components.AppLoadingIndicator
import io.github.ultrafeed.app4pda.core.ui.theme.AppTheme
import io.github.ultrafeed.app4pda.data.preferences.AppSettings
import io.github.ultrafeed.app4pda.data.preferences.SettingsLoad
import io.github.ultrafeed.app4pda.data.preferences.withAppLanguage
import org.koin.compose.koinInject

/** No screen owner or network-dependent repository is created before persisted settings load. */
@Composable
fun SettingsGate(content: @Composable () -> Unit) {
    val settings: AppSettings = koinInject()
    val loaded by settings.state.collectAsState()
    when (val state = loaded) {
        SettingsLoad.Loading ->
            AppTheme(
                isSystemInDarkTheme(),
                AppearanceDefaults.ACCENT,
                animationsEnabled = false,
                applySystemBarContrast = false,
            ) {
                Surface(Modifier.fillMaxSize()) { Box { AppLoadingIndicator() } }
            }

        is SettingsLoad.Failed ->
            AppTheme(
                isSystemInDarkTheme(),
                AppearanceDefaults.ACCENT,
                animationsEnabled = false,
                applySystemBarContrast = false,
            ) {
                Surface(Modifier.fillMaxSize()) {
                    Column {
                        Text(stringResource(R.string.action_failed))
                        Button(onClick = settings::retry) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
            }

        is SettingsLoad.Ready -> {
            val base = LocalContext.current
            LaunchedEffect(base, state.settings.usesAccentIcon) {
                base.applyLauncherIcon(state.settings.usesAccentIcon)
            }
            val configuration = LocalConfiguration.current
            val localized =
                remember(base, configuration, state.settings.language) {
                    base.withAppLanguage(state.settings.language)
                }
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides localized.resources.configuration,
            ) {
                content()
            }
        }
    }
}
