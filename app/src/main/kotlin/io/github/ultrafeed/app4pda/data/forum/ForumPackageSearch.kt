package io.github.ultrafeed.app4pda.data.forum

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

fun interface ForumPackageLookup {
    suspend fun find(packageName: String): ForumInfo?
}

/** Returns a match only after the entire search has proved its uniqueness. */
internal suspend fun findForumPackage(
    packageName: String,
    search: suspend (String, String?) -> ForumTopicSearchPage,
    load: suspend (Int) -> ForumInfo,
    isCurrent: suspend () -> Boolean,
): ForumInfo? {
    return when (val result = resolveForumPackage(packageName, search, load, isCurrent)) {
        is ForumBindingResult.Unique -> result.info
        else -> null
    }
}

internal sealed interface ForumBindingResult {
    data class Unique(val info: ForumInfo) : ForumBindingResult

    data object NotFound : ForumBindingResult

    data object Ambiguous : ForumBindingResult

    data object Interrupted : ForumBindingResult
}

internal suspend fun resolveForumPackage(
    packageName: String,
    search: suspend (String, String?) -> ForumTopicSearchPage,
    load: suspend (Int) -> ForumInfo,
    isCurrent: suspend () -> Boolean,
): ForumBindingResult = ForumPackageCandidates(packageName, search, load, isCurrent).resolve()

/** Request-local evidence: no match is final until server pagination is exhausted. */
private class ForumPackageCandidates(
    private val packageName: String,
    private val search: suspend (String, String?) -> ForumTopicSearchPage,
    private val load: suspend (Int) -> ForumInfo,
    private val isCurrent: suspend () -> Boolean,
) {
    private val topics = mutableSetOf<Int>()
    private val pages = mutableSetOf<String>()
    private var candidate: ForumInfo? = null
    private var outcome: ForumBindingResult? = null

    suspend fun resolve(): ForumBindingResult {
        var next: String? = null
        do {
            currentCoroutineContext().ensureActive()
            if (!isCurrent()) outcome = ForumBindingResult.Interrupted
            if (outcome == null) {
                val page = search(packageName, next)
                checkCandidates(page)
                if (outcome == null) {
                    next = page.nextUrl
                    check(next == null || pages.add(next)) { "Forum pagination cycle" }
                }
            }
        } while (outcome == null && next != null)
        return outcome ?: completedResult()
    }

    private suspend fun checkCandidates(page: ForumTopicSearchPage) {
        for (topic in page.topics) {
            if (outcome == null && topics.add(topic.id)) checkCandidate(topic.id)
        }
    }

    private suspend fun checkCandidate(id: Int) {
        if (!isCurrent()) {
            outcome = ForumBindingResult.Interrupted
        } else {
            val info = existingTopic(id)
            if (info != null && packageName in info.packageNames) {
                if (candidate == null) candidate = info else outcome = ForumBindingResult.Ambiguous
            }
        }
    }

    private suspend fun existingTopic(id: Int): ForumInfo? {
        val info =
            try {
                load(id)
            } catch (failure: ForumApiException) {
                // Deleted search hits cannot confirm a package; other failures interrupt the
                // search.
                if (
                    failure.reason == ForumFailure.HTTP &&
                        failure.statusCode == HttpStatusCode.NotFound.value
                )
                    null
                else throw failure
            }
        check(info == null || info.topicId == id) { "Forum topic mismatch" }
        return info
    }

    private suspend fun completedResult(): ForumBindingResult =
        if (!isCurrent()) ForumBindingResult.Interrupted
        else candidate?.let(ForumBindingResult::Unique) ?: ForumBindingResult.NotFound
}
