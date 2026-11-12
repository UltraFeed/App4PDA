package io.github.ultrafeed.app4pda.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import io.github.ultrafeed.app4pda.core.network.SourceProblem
import io.github.ultrafeed.app4pda.core.ui.theme.AppLayout

/** The same recoverable source error used by catalog and local feeds. */
@Composable
fun AppErrorCard(
    problem: SourceProblem,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    var dismissed by
        rememberSaveable(problem.code, problem.description) {
            mutableStateOf(false)
        }
    if (dismissed) return
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        val actionColors =
            ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        Column(Modifier.padding(AppLayout.content)) {
            Text(
                problem.code,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(problem.description, color = MaterialTheme.colorScheme.onErrorContainer)
            ErrorActions(actionColors, onRetry) {
                dismissed = true
                onDismiss?.invoke()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ErrorActions(
    colors: ButtonColors,
    onRetry: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    FlowRow {
        if (onRetry != null)
            TextButton(onRetry, colors = colors) {
                Text(stringResource(R.string.action_retry))
            }
        TextButton(onDismiss, colors = colors) {
            Text(stringResource(R.string.action_close))
        }
    }
}
