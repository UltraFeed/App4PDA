package io.github.ultrafeed.app4pda.app.navigation

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.ultrafeed.app4pda.R

/** Effects resume on return, but retained ViewModels and their requests are not recreated. */
@Composable
internal fun OnScreenResume(action: () -> Unit) {
    val latest by rememberUpdatedState(action)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) latest()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

internal fun Activity.openExternal(intent: Intent) {
    try {
        val ownHandler =
            intent.action == Intent.ACTION_VIEW &&
                intent.component == null &&
                intent
                    .resolveActivity(
                        packageManager,
                    )
                    ?.packageName == packageName
        if (ownHandler) {
            // An external link must not resolve back into MainActivity and enqueue itself again.
            val chooser = Intent.createChooser(intent, null)
            chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(componentName))
            startActivity(chooser)
        } else startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.action_failed, Toast.LENGTH_SHORT).show()
    }
}

internal val LocalEntryActive = staticCompositionLocalOf { false }
internal val LocalCanGoBack = staticCompositionLocalOf { false }

/** A saved result is consumed only while its addressed receiver is resumed. */
@Composable
internal fun PendingScreenResult(entry: AppEntry, consume: () -> Unit) {
    val active = LocalEntryActive.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latest by rememberUpdatedState(consume)
    LaunchedEffect(entry.id, entry.result, active, lifecycle) {
        if (active && entry.result != null)
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { latest() }
    }
}
