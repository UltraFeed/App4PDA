package io.github.ultrafeed.app4pda.feature.catalog.data

import io.github.ultrafeed.app4pda.data.forum.ForumApiException
import io.github.ultrafeed.app4pda.data.forum.ForumFailure
import io.github.ultrafeed.app4pda.data.forum.ForumTopicPreview
import io.github.ultrafeed.app4pda.feature.catalog.CatalogApp
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Per-query visible metadata; no full-result preload or parallel requests. */
internal class CatalogTopicPreviews(
    private val repository: CatalogRepository,
    private val owner: CoroutineScope,
) {
    private val inspected = mutableSetOf<Int>()
    private val lock = Mutex()
    private var blocked = false
    private var scope = newScope()

    private fun newScope() =
        CoroutineScope(
            owner.coroutineContext + Job(owner.coroutineContext[Job]),
        )

    fun reset() {
        scope.coroutineContext[Job]?.cancel()
        scope = newScope()
        inspected.clear()
        blocked = false
    }

    suspend fun loadVisible(
        topics: List<Int>,
        rows: () -> List<CatalogApp>,
        publish: (Map<Int, ForumTopicPreview?>) -> Unit,
    ) {
        val pending = scope.async {
            val result = load(topics, rows)
            currentCoroutineContext().ensureActive()
            if (result.isNotEmpty()) publish(result)
        }
        try {
            pending.await()
        } finally {
            pending.cancel()
        }
    }

    suspend fun load(
        topics: List<Int>,
        rows: () -> List<CatalogApp>,
    ): Map<Int, ForumTopicPreview?> = lock.withLock {
        if (blocked) return@withLock emptyMap()
        val visible = topics.toSet()
        val pending =
            rows()
                .filter { it.topicId in visible && it.topicId !in inspected }
                .distinctBy { it.topicId }
        val previews = mutableMapOf<Int, ForumTopicPreview?>()
        for ((_, topicId) in pending) {
            try {
                previews[topicId] =
                    repository.preview(
                        topicId,
                    )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: ForumApiException) {
                currentCoroutineContext().ensureActive()
                if (
                    failure.statusCode == HttpStatusCode.TooManyRequests.value ||
                        failure.reason == ForumFailure.VERIFICATION_REQUIRED
                ) {
                    blocked = true
                    break
                }
            } catch (_: IOException) {
                currentCoroutineContext().ensureActive()
            } catch (_: IllegalArgumentException) {
                currentCoroutineContext().ensureActive()
            }

            currentCoroutineContext().ensureActive()
            inspected += topicId
        }
        previews
    }
}
