package io.github.ultrafeed.app4pda.core.ui.components

import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

@Composable
fun SwitchChoiceItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    index: Int = 0,
    count: Int = 1,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        enabled = enabled,
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = Color.Transparent,
                selectedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                selectedContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        modifier = modifier.semantics { role = Role.Switch },
        content = { Text(title) },
        trailingContent = { Switch(checked, onCheckedChange = null, enabled = enabled) },
    )
}
