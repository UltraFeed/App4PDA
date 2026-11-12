package io.github.ultrafeed.app4pda.core.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/** Shared Material spacing scale. Component-owned padding remains with the component. */
object AppLayout {
    const val DIALOG_WINDOW_FRACTION = 0.85f
    val itemGap = 8.dp
    val content = 16.dp
    val section = 24.dp
    val listPadding
        get() = PaddingValues(content)
}
