package io.github.ultrafeed.app4pda.feature.forum.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.formatting.informationDate
import io.github.ultrafeed.app4pda.core.network.SourceProblem
import io.github.ultrafeed.app4pda.core.ui.components.AppErrorCard
import io.github.ultrafeed.app4pda.core.ui.components.AppLoadingIndicator
import io.github.ultrafeed.app4pda.core.ui.theme.AppLayout
import io.github.ultrafeed.app4pda.data.forum.ForumPost

@Composable
fun ForumPostScreen(
    title: String,
    postId: Int,
    state: ForumPostUiState,
    onRetry: () -> Unit,
    onOpenForum: () -> Unit,
    onClose: () -> Unit,
    content: @Composable (ForumPost, Modifier) -> Unit,
) {
    val problem = state.error?.let { SourceProblem.from(LocalContext.current, "4PDA", it) }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(
                    max =
                        with(LocalDensity.current) {
                                LocalWindowInfo.current.containerSize.height.toDp() *
                                    AppLayout.DIALOG_WINDOW_FRACTION
                            }
                            .coerceAtLeast(1.dp),
                )
                .padding(20.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                state.post?.updatedAt?.takeIf { it > 0 }?.let(::informationDate)
                    ?: stringResource(R.string.forum_post_number, postId),
                style = MaterialTheme.typography.bodySmall,
            )
            Box(
                Modifier.weight(1f, fill = false).fillMaxWidth().padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (state.post != null) content(state.post, Modifier.fillMaxWidth())
                if (state.loading && state.post == null) AppLoadingIndicator()
                if (problem != null && state.post == null)
                    AppErrorCard(
                        problem,
                        onRetry = onRetry,
                        onDismiss = onClose,
                    )
            }
            if (problem != null && state.post != null)
                AppErrorCard(
                    problem.copy(
                        description =
                            stringResource(
                                R.string.forum_cached_check_failed,
                                problem.description,
                            ),
                    ),
                    onRetry = onRetry,
                )
            ForumPostActions(
                showForum = problem?.forumPaused != true,
                showClose = problem == null || state.post != null,
                onOpenForum = onOpenForum,
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun ForumPostActions(
    showForum: Boolean,
    showClose: Boolean,
    onOpenForum: () -> Unit,
    onClose: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        if (showForum) TextButton(onOpenForum) { Text(stringResource(R.string.menu_open_forum)) }
        if (showClose) TextButton(onClose) { Text(stringResource(R.string.action_close)) }
    }
}
