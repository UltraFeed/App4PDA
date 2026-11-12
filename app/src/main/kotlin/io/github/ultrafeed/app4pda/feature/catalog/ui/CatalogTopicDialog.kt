package io.github.ultrafeed.app4pda.feature.catalog.ui

import android.R as AndroidR
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.ultrafeed.app4pda.R

@Composable
fun CatalogTopicDialog(onDismiss: () -> Unit, onTopic: (Int) -> Unit) {
    var topic by rememberSaveable { mutableStateOf("") }
    val id =
        Regex("(?:showtopic=)(\\d+)").find(topic)?.groupValues?.get(1)?.toIntOrNull()
            ?: topic.trim().toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bind_topic_id)) },
        text = {
            OutlinedTextField(
                topic,
                { topic = it },
                Modifier,
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                {
                    id?.takeIf { it > 0 }?.let(onTopic)
                    onDismiss()
                },
                enabled = (id ?: 0) > 0,
            ) {
                Text(stringResource(AndroidR.string.ok))
            }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(AndroidR.string.cancel)) } },
    )
}
