package io.github.ultrafeed.app4pda.core.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared Material spacing scale. Component-owned padding remains with the component. */
object AppLayout {
    const val DIALOG_WINDOW_FRACTION: Float = 0.85f
    val itemGap: Dp = 8.dp
    val content: Dp = 16.dp
    val section: Dp = 24.dp
    val listPadding: PaddingValues
        get() = PaddingValues(content)
}
