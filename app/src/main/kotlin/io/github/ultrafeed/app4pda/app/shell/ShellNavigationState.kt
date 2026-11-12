package io.github.ultrafeed.app4pda.app.shell

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/** Incoming commands and a reference to the active section's actual NavBackStack. */
internal class ShellNavigationState {
    var stack: NavBackStack<NavKey>? = null
    val pendingIntents = mutableStateListOf<Intent>()
    var downloadsRequested by mutableStateOf(false)

    fun detach() {
        stack = null
    }
}
