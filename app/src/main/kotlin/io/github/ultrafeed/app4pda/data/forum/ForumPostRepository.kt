package io.github.ultrafeed.app4pda.data.forum

import io.github.ultrafeed.app4pda.data.preferences.AppSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Selected posts use the same anonymous HTTP transport as other public forum pages. */
class ForumPostRepository(
    private val settings: AppSettings,
    private val api: ForumApiClient,
    private val cache: ForumCache,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun loadPost(
        topicId: Int,
        postId: Int,
        onCached: suspend (ForumPost) -> Unit = {},
    ): ForumPost =
        withContext(ioDispatcher) {
            require(topicId >= 0 && postId > 0)
            val snapshot = settings.awaitReady()
            val revision = cache.revision()
            cache.readPost(topicId, postId)?.let { cached ->
                ensureActive()
                onCached(cached)
            }
            val source = api.loadTopicHtml(topicId, postId)
            ensureActive()
            val post =
                ForumPostParser(rule = { key -> snapshot.rules[key] })
                    .parse(source, topicId, postId)
                    ?: throw ForumApiException(ForumFailure.PARSING, "Forum post not found")
            cache.savePost(post, revision)
            ensureActive()
            post
        }
}
