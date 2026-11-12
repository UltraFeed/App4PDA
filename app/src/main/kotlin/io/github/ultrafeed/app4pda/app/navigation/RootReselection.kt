package io.github.ultrafeed.app4pda.app.navigation

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.ultrafeed.app4pda.core.ui.theme.LocalAppAnimations

internal val LocalRootReselection = staticCompositionLocalOf { 0L }

/** Scrolls only the active root's current page; retained screens keep their positions. */
@Composable
internal fun ScrollToTopOnReselection(list: LazyListState, currentPage: Boolean = true) {
    val event = LocalRootReselection.current
    val active = LocalEntryActive.current
    val animations = LocalAppAnimations.current
    var handled by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(event, active, currentPage) {
        if (event < handled) handled = event
        if (event > handled) {
            handled = event
            if (active && currentPage) {
                if (animations) list.animateScrollToItem(0) else list.scrollToItem(0)
            }
        }
    }
}
