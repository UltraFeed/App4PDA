package io.github.ultrafeed.app4pda.feature.downloads.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.feature.downloads.DownloadEntry

@Composable
internal fun DownloadMenu(
    entry: DownloadEntry,
    busy: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (DownloadEntry, DownloadAction) -> Unit,
    onRename: (DownloadEntry) -> Unit,
    onInfo: (DownloadEntry) -> Unit,
    onDelete: (DownloadEntry) -> Unit,
) {
    val files = buildList {
        add(R.string.downloads_save_copy to { onAction(entry, DownloadAction.SAVE) })
        add(R.string.action_open to { onAction(entry, DownloadAction.OPEN) })
        if (!entry.group && !entry.path.contains("/.play/"))
            add(
                R.string.menu_downloads_rename to
                    {
                        onRename(
                            entry,
                        )
                    },
            )
        if (!entry.group)
            add(
                R.string.action_share_file to
                    {
                        onAction(
                            entry,
                            DownloadAction.SHARE,
                        )
                    },
            )
    }
    val groups =
        listOf(
            files,
            listOf(R.string.menu_info to { onInfo(entry) }),
            listOf(R.string.menu_delete to { onDelete(entry) }),
        )
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismiss) {
        groups.forEachIndexed { index, actions ->
            if (index > 0) Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            DownloadMenuGroup(actions, index, groups.size, busy, onDismiss)
        }
    }
}

@Composable
private fun DownloadMenuGroup(
    actions: List<Pair<Int, () -> Unit>>,
    index: Int,
    count: Int,
    busy: Boolean,
    onDismiss: () -> Unit,
) {
    DropdownMenuGroup(shapes = MenuDefaults.groupShape(index, count)) {
        actions.forEachIndexed { itemIndex, (title, action) ->
            DropdownMenuItem(
                text = { Text(stringResource(title)) },
                shape = MenuDefaults.itemShape(itemIndex, actions.size).shape,
                enabled = title != R.string.downloads_save_copy || !busy,
                onClick = {
                    onDismiss()
                    action()
                },
            )
        }
    }
}
