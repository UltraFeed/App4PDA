package io.github.ultrafeed.app4pda.feature.auth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.ultrafeed.app4pda.app.navigation.AppDestination
import io.github.ultrafeed.app4pda.app.navigation.AppEntry
import io.github.ultrafeed.app4pda.app.navigation.LocalEntryActive
import io.github.ultrafeed.app4pda.app.navigation.ScreenResult
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun AuthDestination(
    entry: AppEntry,
    onBack: () -> Unit,
    onResult: (ScreenResult) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val active = LocalEntryActive.current
    val model: ForumAuthViewModel = koinViewModel()
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(entry.id) {
        if ((entry.destination as? AppDestination.Auth)?.addAccount == true) model.addAccount()
    }
    LaunchedEffect(model, active) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            model.state.collect {
                if (it.change != null && active) onResult(ScreenResult.AuthChanged)
            }
        }
    }
    ForumAuthScreen(
        state,
        model::username,
        model::password,
        model::answer,
        model::hidden,
        model::refreshCaptcha,
        model::login,
        model::logout,
    ) {
        model.cancelRequests()
        onBack()
    }
}
