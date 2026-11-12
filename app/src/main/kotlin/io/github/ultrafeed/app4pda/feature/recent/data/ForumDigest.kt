package io.github.ultrafeed.app4pda.feature.recent.data

import android.content.Context
import android.os.SystemClock
import io.github.ultrafeed.app4pda.core.AppPolicy
import io.github.ultrafeed.app4pda.core.images.AppImageSource
import io.github.ultrafeed.app4pda.core.images.AppImages
import io.github.ultrafeed.app4pda.core.images.ImageOrigin
import io.github.ultrafeed.app4pda.core.images.forumIconCacheKey
import io.github.ultrafeed.app4pda.core.images.imageRequest
import io.github.ultrafeed.app4pda.data.forum.ForumApiClient
import io.github.ultrafeed.app4pda.data.forum.ForumTopicPreview
import io.github.ultrafeed.app4pda.data.forum.ForumTopicPreviewResult
import io.github.ultrafeed.app4pda.data.preferences.AppSettings
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
enum class DigestKind {
    NEW,
    UPDATED,
}

@Serializable data class DigestEntry(val topicId: Int, val title: String, val description: String)

@Serializable
data class DigestGroup(
    val period: String,
    val category: String,
    val kind: DigestKind,
    val entries: List<DigestEntry>,
)

class ForumDigest(
    private val api: ForumApiClient,
    context: Context,
    private val settings: AppSettings,
    private val images: AppImages,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val context = context.applicationContext
    private val cache = DigestCache(context.cacheDir, ioDispatcher, System::currentTimeMillis)
    private val lock = Mutex()
    private val mutableContent = MutableStateFlow(DigestSnapshot())
    val content: StateFlow<DigestSnapshot> = mutableContent
    private val previewLock = Mutex()
    private val previewCheckedAt = mutableMapOf<Int, Long>()
    private val missingTopics = mutableMapOf<Int, Long>()
    private val generation = AtomicLong()
    private val requests = ConcurrentHashMap.newKeySet<Job>()

    suspend fun clearCache() {
        generation.incrementAndGet()
        requests.forEach {
            it.cancel(CancellationException("Digest cache cleared"))
        }
        previewLock.withLock {
            cache.clear()
            previewCheckedAt.clear()
            missingTopics.clear()
            mutableContent.value = DigestSnapshot()
        }
    }

    val coverageChanges: Flow<DigestCoverage> =
        settings.changes
            .map {
                DigestCoverage(it.digestNewIssues, it.digestUpdatedIssues)
            }
            .distinctUntilChanged()

    private suspend fun coverage(): DigestCoverage =
        settings.awaitReady().let {
            DigestCoverage(it.digestNewIssues, it.digestUpdatedIssues)
        }

    suspend fun cached(): List<DigestGroup> = previewLock.withLock {
        val snapshot = cache.snapshot()
        val selected = withoutMissing(coverage().select(snapshot))
        if (selected.issues.isNotEmpty()) mutableContent.value = selected
        selected.groups
    }

    suspend fun preview(topic: Int): ForumTopicPreview? = request {
        val revision = generation.get()
        val cached = previewLock.withLock {
            missingTopics.entries.removeAll {
                System.currentTimeMillis() - it.value !in 0 until AppPolicy.CACHE_TTL_MILLIS
            }
            if (topic in missingTopics) return@request null
            val checkedAt = previewCheckedAt[topic]
            if (
                checkedAt != null &&
                    SystemClock.elapsedRealtime() - checkedAt <
                        AppPolicy.DIGEST_METADATA_INTERVAL_MILLIS
            )
                mutableContent.value.previews[topic]
            else cache.freshPreview(topic)
        }
        if (cached != null) return@request cached
        val result = api.topicPreview(topic, refresh = true)
        val preview = previewLock.withLock {
            if (revision != generation.get()) throw CancellationException("Digest cache cleared")
            when (result) {
                is ForumTopicPreviewResult.Found -> {
                    previewCheckedAt[topic] = SystemClock.elapsedRealtime()
                    cache.savePreview(topic, result.preview)
                    mutableContent.update {
                        it.copy(previews = it.previews + (topic to result.preview))
                    }
                    result.preview
                }

                ForumTopicPreviewResult.Missing -> {
                    removeMissingTopic(topic)
                    null
                }
            }
        }
        if (preview != null) prefetchIcon(topic, preview)
        preview
    }

    private suspend fun <T> request(block: suspend () -> T): T = coroutineScope {
        val job = checkNotNull(currentCoroutineContext()[Job])
        requests.add(job)
        try {
            block()
        } finally {
            requests.remove(job)
        }
    }

    private suspend fun prefetchIcon(
        topic: Int,
        preview: ForumTopicPreview,
    ) {
        val origin = ImageOrigin.FORUM
        val url =
            preview.icon?.takeIf {
                it.startsWith("https://") && settings.current.imageCache(origin).enabled
            } ?: return
        images.loader.execute(
            imageRequest(
                context,
                AppImageSource.Url(url, origin),
                forumIconCacheKey(topic),
            ),
        )
    }

    private suspend fun removeMissingTopic(topic: Int) {
        previewCheckedAt.remove(topic)
        missingTopics[topic] = System.currentTimeMillis()
        cache.removeTopic(topic)
        mutableContent.update { withoutMissing(it) }
    }

    private fun withoutMissing(snapshot: DigestSnapshot) =
        snapshot.copy(
            groups =
                snapshot.groups
                    .map { group ->
                        group.copy(
                            entries = group.entries.filterNot { it.topicId in missingTopics },
                        )
                    }
                    .filter { it.entries.isNotEmpty() },
            previews = snapshot.previews.filterKeys { it !in missingTopics },
        )

    suspend fun load(userInitiated: Boolean = true): List<DigestGroup> = request {
        withContext(ioDispatcher) {
            val revision = generation.get()
            lock.withLock {
                val requested = coverage()
                val snapshot = fetch(userInitiated)
                requested.requireComplete(snapshot.issues)
                previewLock.withLock {
                    if (revision != generation.get())
                        throw CancellationException("Digest cache cleared")
                    val filtered = withoutMissing(snapshot)
                    cache.save(filtered)
                    val stored = cache.snapshot().previews
                    mutableContent.update {
                        withoutMissing(
                            requested.select(filtered.copy(previews = stored + it.previews)),
                        )
                    }
                    mutableContent.value.groups
                }
            }
        }
    }

    private suspend fun fetch(userInitiated: Boolean): DigestSnapshot {
        var next: String? = ForumDigestParser.URL
        val pages = mutableSetOf<String>()
        val groups = mutableListOf<DigestGroup>()
        val issues = linkedMapOf<String, Set<DigestKind>>()
        while (next != null) {
            check(pages.add(next)) { "Digest pagination cycle" }
            val page =
                ForumDigestParser.parse(api.loadListing(next, userInitiated = userInitiated), next)
            groups += page.groups
            page.issues.forEach { issue ->
                issues[issue.period] = issues[issue.period].orEmpty() + issue.sections
            }
            next = page.nextUrl
        }
        val snapshot =
            DigestSnapshot(
                groups =
                    groups
                        .groupBy { Triple(it.period, it.category, it.kind) }
                        .map { (_, rows) ->
                            rows
                                .first()
                                .copy(
                                    entries = rows.flatMap { it.entries }.distinctBy { it.topicId },
                                )
                        },
                issues = issues.map { (period, kinds) -> DigestIssue(period, kinds) },
            )
        return snapshot
    }
}

/** Source periods and categories are grouped once without manufacturing dates or labels. */
internal fun List<DigestGroup>.forDisplay(kind: DigestKind): List<DigestGroup> = filter {
    it.kind == kind
}
    .groupBy { it.period }
    .flatMap { (period, groups) ->
        groups
            .groupBy { it.category }
            .map { (category, matches) ->
                DigestGroup(period, category, kind, matches.flatMap { it.entries })
            }
    }
