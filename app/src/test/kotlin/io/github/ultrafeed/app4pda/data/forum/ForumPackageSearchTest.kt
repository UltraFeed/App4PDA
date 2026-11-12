package io.github.ultrafeed.app4pda.data.forum

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.fail
import org.junit.Test

class ForumPackageSearchTest {
    private fun info(id: Int, pack: String = "org.example") =
        ForumInfo(
            ForumTopicHeader(
                id,
                "Model $id",
                listOf(pack),
                "",
                "1",
                "",
                "",
                0,
                0,
            ),
            emptyList(),
            "",
            emptyList(),
        )

    private fun topic(id: Int) = ForumSearchTopic(id, "Model $id", "", false)

    @Test
    fun completedNegativeAndAmbiguousSearchesHaveDifferentOutcomes(): Unit = runTest {
        assertThat(
            resolveForumPackage(
                "org.example",
                { _, _ -> ForumTopicSearchPage(emptyList(), null) },
                { error("No topic expected") },
                { true },
            ),
            CoreMatchers.`is`(
                ForumBindingResult.NotFound,
            ),
        )
        assertThat(
            resolveForumPackage(
                "org.example",
                { _, _ -> ForumTopicSearchPage(listOf(topic(1), topic(2)), null) },
                { info(it) },
                { true },
            ),
            CoreMatchers.`is`(
                ForumBindingResult.Ambiguous,
            ),
        )
        assertThat(
            resolveForumPackage(
                "org.example",
                { _, _ -> error("Must not search") },
                { info(it) },
                { false },
            ),
            CoreMatchers.`is`(
                ForumBindingResult.Interrupted,
            ),
        )
    }

    @Test
    fun quotaIsNotACompletedNegativeSearch(): Unit = runTest {
        val quota =
            ForumApiException(
                ForumFailure.HTTP,
                "Controlled quota",
                429,
                retryAfterMillis = 910000,
                searchQuota = true,
            )
        try {
            resolveForumPackage("org.example", { _, _ -> throw quota }, { info(it) }, { true })
            fail("Quota must propagate without a negative outcome")
        } catch (failure: ForumApiException) {
            assertThat(
                failure,
                CoreMatchers.sameInstance(
                    quota,
                ),
            )
        }
    }

    @Test
    fun deletedCandidateDoesNotAbortRemainingPages(): Unit = runTest {
        val loaded = mutableListOf<Int>()
        val result =
            resolveForumPackage(
                "org.example",
                { _, next ->
                    if (next == null) ForumTopicSearchPage(listOf(topic(1)), "next")
                    else ForumTopicSearchPage(listOf(topic(2)), null)
                },
                {
                    loaded += it
                    if (it == 1) throw ForumApiException(ForumFailure.HTTP, "Deleted topic", 404)
                    info(it)
                },
                { true },
            )
        assertThat(
            loaded,
            CoreMatchers.`is`(
                listOf(
                    1,
                    2,
                ),
            ),
        )
        assertThat(
            (result as ForumBindingResult.Unique).info.topicId,
            CoreMatchers.`is`(
                2,
            ),
        )
    }

    @Test
    fun onlyDeletedCandidatesAreACompletedNegativeSearch(): Unit = runTest {
        assertThat(
            resolveForumPackage(
                "org.example",
                { _, _ -> ForumTopicSearchPage(listOf(topic(1)), null) },
                { throw ForumApiException(ForumFailure.HTTP, "Deleted topic", 404) },
                { true },
            ),
            CoreMatchers.`is`(
                ForumBindingResult.NotFound,
            ),
        )
    }

    @Test
    fun search404AndOtherCandidateFailuresStillPropagate(): Unit = runTest {
        for (status in listOf(403, 404, 429, 500)) {
            val failure = ForumApiException(ForumFailure.HTTP, "Controlled failure", status)
            try {
                resolveForumPackage(
                    "org.example",
                    { _, _ -> throw failure },
                    { info(it) },
                    { true },
                )
                fail("A failed search is not an absent topic")
            } catch (actual: ForumApiException) {
                assertThat(
                    actual,
                    CoreMatchers.sameInstance(
                        failure,
                    ),
                )
            }
            if (status == 404) continue
            try {
                resolveForumPackage(
                    "org.example",
                    { _, _ -> ForumTopicSearchPage(listOf(topic(1)), null) },
                    { throw failure },
                    { true },
                )
                fail("Only a missing candidate may be skipped")
            } catch (actual: ForumApiException) {
                assertThat(
                    actual,
                    CoreMatchers.sameInstance(
                        failure,
                    ),
                )
            }
        }
    }

    @Test
    fun aSingleMatchingNameCannotReplaceExactPackageEvidence(): Unit = runTest {
        assertThat(
            findForumPackage(
                "org.example",
                { _, _ -> ForumTopicSearchPage(listOf(topic(1)), null) },
                { info(it, "different.package") },
                { true },
            ),
            CoreMatchers.nullValue(),
        )
    }

    @Test
    fun uniquenessRequiresEveryPageIncludingAnEmptyPageWithContinuation(): Unit = runTest {
        val calls = mutableListOf<String?>()
        val result =
            findForumPackage(
                "org.example",
                { _, next ->
                    calls += next
                    when (next) {
                        null -> ForumTopicSearchPage(listOf(topic(1)), "second")
                        "second" -> ForumTopicSearchPage(emptyList(), "third")
                        else -> ForumTopicSearchPage(listOf(topic(2)), null)
                    }
                },
                { info(it) },
                { true },
            )
        assertThat(
            result,
            CoreMatchers.nullValue(),
        )
        assertThat(
            calls,
            CoreMatchers.`is`(
                listOf(
                    null,
                    "second",
                    "third",
                ),
            ),
        )
    }

    @Test
    fun aFailedFollowingPageCannotCommitTheFirstCandidate(): Unit = runTest {
        try {
            findForumPackage(
                "org.example",
                { _, next ->
                    if (next == null) ForumTopicSearchPage(listOf(topic(1)), "next")
                    else throw IOException("Controlled failure")
                },
                { info(it) },
                { true },
            )
            fail("Incomplete search cannot establish a binding")
        } catch (_: IOException) {}
    }

    @Test
    fun aModeChangeDuringTheResponseDiscardsTheCandidate(): Unit = runTest {
        var current = true
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val result = async {
            findForumPackage(
                "org.example",
                { _, _ -> ForumTopicSearchPage(listOf(topic(1)), null) },
                {
                    entered.complete(Unit)
                    release.await()
                    info(it)
                },
                { current },
            )
        }
        entered.await()
        current = false
        release.complete(Unit)
        assertThat(
            result.await(),
            CoreMatchers.nullValue(),
        )
    }

    @Test
    fun repeatedTopicAcrossPagesIsOneCandidate(): Unit = runTest {
        assertThat(
            findForumPackage(
                    "org.example",
                    { _, next ->
                        ForumTopicSearchPage(listOf(topic(1)), if (next == null) "next" else null)
                    },
                    { info(it) },
                    { true },
                )
                ?.topicId,
            CoreMatchers.`is`(
                1,
            ),
        )
    }
}
