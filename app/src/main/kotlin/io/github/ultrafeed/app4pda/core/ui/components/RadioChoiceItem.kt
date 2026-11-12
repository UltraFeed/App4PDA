package io.github.ultrafeed.app4pda.core.ui.components

import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** The whole row is the single selection target. */
@Composable
fun RadioChoiceItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ListItem(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors =
            ListItemDefaults.colors(
                containerColor = Color.Transparent,
                selectedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                selectedContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        content = { Text(title) },
        leadingContent = { RadioButton(selected, onClick = null, enabled = enabled) },
    )
}
