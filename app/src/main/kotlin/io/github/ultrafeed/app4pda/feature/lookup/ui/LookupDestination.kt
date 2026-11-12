package io.github.ultrafeed.app4pda.feature.lookup.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.app.MainActivity
import io.github.ultrafeed.app4pda.app.navigation.AppDestination
import io.github.ultrafeed.app4pda.app.navigation.AppEntry
import io.github.ultrafeed.app4pda.app.navigation.LocalEntryActive
import io.github.ultrafeed.app4pda.app.shell.ScreenFrame
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun LookupDestination(
    activity: MainActivity,
    entry: AppEntry,
    onFound: (AppDestination) -> Unit,
    onBack: () -> Unit,
) {
    val input = entry.destination as AppDestination.Lookup
    val model: AppLookupViewModel = koinViewModel { parametersOf(input.packageName.orEmpty()) }
    val state by model.state.collectAsStateWithLifecycle()
    val active = LocalEntryActive.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestFound by rememberUpdatedState(onFound)
    LaunchedEffect(model, active, lifecycle) {
        if (active)
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.state.collect {
                    model.consumeResult()?.let { match ->
                        latestFound(
                            if (match.topicId > 0)
                                AppDestination.Details(match.topicId, match.packageName)
                            else AppDestination.Catalog(linked = match.packageName),
                        )
                    }
                }
            }
    }
    ScreenFrame(
        onBack,
        activity.getString(R.string.lookup_title),
    ) {
        AppLookupScreen(state, model::retry)
    }
}
