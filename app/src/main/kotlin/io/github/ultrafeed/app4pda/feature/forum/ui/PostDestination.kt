package io.github.ultrafeed.app4pda.feature.forum.ui

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ultrafeed.app4pda.app.MainActivity
import io.github.ultrafeed.app4pda.app.navigation.AppDestination
import io.github.ultrafeed.app4pda.app.navigation.AppEntry
import io.github.ultrafeed.app4pda.app.navigation.openCustomTab
import io.github.ultrafeed.app4pda.app.navigation.openExternal
import io.github.ultrafeed.app4pda.core.images.ImageOrigin
import io.github.ultrafeed.app4pda.core.ui.document.DocumentContent
import io.github.ultrafeed.app4pda.data.forum.ForumPostLink
import io.github.ultrafeed.app4pda.data.forum.ForumUrls
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun PostDestination(
    activity: MainActivity,
    entry: AppEntry,
    onOpen: (AppDestination) -> Unit,
    onBack: () -> Unit,
) {
    val args = entry.destination as AppDestination.Post
    val topic = args.topic
    val postId = args.post
    val title = args.title
    val baseUrl = ForumUrls.post(topic, postId)
    val model: ForumPostViewModel = koinViewModel { parametersOf(topic, postId) }
    val state by model.state.collectAsStateWithLifecycle()
    fun external(url: String) {
        activity.openExternal(Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    fun link(url: String) {
        when (val parsed = ForumPostLink.parse(url, baseUrl, topic)) {
            is ForumPostLink.Image ->
                onOpen(
                    AppDestination.Gallery(
                        listOf(parsed.url),
                        ImageOrigin.FORUM,
                    ),
                )

            is ForumPostLink.Attachment -> onOpen(AppDestination.Attachment(topic, parsed.url))
            is ForumPostLink.Post ->
                onOpen(
                    AppDestination.Post(
                        parsed.address.topicId,
                        parsed.address.postId,
                        title,
                    ),
                )

            is ForumPostLink.Topic -> onOpen(AppDestination.Details(parsed.topicId, null))
            is ForumPostLink.External -> external(parsed.url)
            null -> Unit
        }
    }
    ForumPostScreen(
        title,
        postId,
        state,
        model::refresh,
        { activity.openCustomTab(baseUrl) },
        { onBack() },
    ) { post, modifier ->
        DocumentContent(
            origin = ImageOrigin.FORUM,
            document = post.document,
            modifier = modifier,
            compact = true,
            fitContent = true,
            onImages = { images, page ->
                onOpen(
                    AppDestination.Gallery(
                        images,
                        ImageOrigin.FORUM,
                        page,
                    ),
                )
            },
            onLink = ::link,
        )
    }
}
