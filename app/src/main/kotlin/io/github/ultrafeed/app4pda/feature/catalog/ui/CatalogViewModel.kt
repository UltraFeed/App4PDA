package io.github.ultrafeed.app4pda.feature.catalog.ui

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ultrafeed.app4pda.data.forum.ForumApiException
import io.github.ultrafeed.app4pda.data.forum.ForumSections
import io.github.ultrafeed.app4pda.data.forum.ForumTopicPreview
import io.github.ultrafeed.app4pda.feature.catalog.CatalogApp
import io.github.ultrafeed.app4pda.feature.catalog.CatalogCategory
import io.github.ultrafeed.app4pda.feature.catalog.CatalogPage
import io.github.ultrafeed.app4pda.feature.catalog.CatalogQuery
import io.github.ultrafeed.app4pda.feature.catalog.CatalogRow
import io.github.ultrafeed.app4pda.feature.catalog.CatalogSort
import io.github.ultrafeed.app4pda.feature.catalog.CatalogUiState
import io.github.ultrafeed.app4pda.feature.catalog.data.CatalogRepository
import io.github.ultrafeed.app4pda.feature.catalog.data.CatalogTopicPreviews
import io.github.ultrafeed.app4pda.feature.catalog.withTopicPreviews
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CatalogViewModel(
    private val repository: CatalogRepository,
    private val savedState: SavedStateHandle,
    initial: CatalogQuery,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(
            CatalogUiState(
                    initial.copy(
                        search =
                            if (
                                savedState.contains(
                                    "catalog_search",
                                )
                            )
                                savedState["catalog_search"]
                            else initial.search,
                        sorting =
                            CatalogSort.fromStorage(
                                savedState["catalog_sorting"] ?: initial.sorting.storageValue,
                            ),
                        topicId = savedState["catalog_topic"] ?: initial.topicId,
                        groupId = savedState["catalog_section"] ?: initial.groupId,
                    ),
                )
                .let {
                    it.copy(
                        draft = savedState["catalog_draft"] ?: it.draft,
                    )
                },
        )
    val state: StateFlow<CatalogUiState> = mutableState.asStateFlow()

    private val queryScope =
        CoroutineScope(
            viewModelScope.coroutineContext +
                Job(
                    viewModelScope.coroutineContext[Job],
                ),
        )

    private val previews =
        CatalogTopicPreviews(
            repository,
            viewModelScope,
        )
    private var nextPageUrl: String? = null
    private var failedPage = 0
    private var failedContinuation: String? = null
    private var failedRows = emptyList<CatalogRow>()
    private val knownPreviews = mutableMapOf<Int, ForumTopicPreview?>()

    suspend fun visibleTopics(
        topics: List<Int>,
    ) {
        previews.loadVisible(
            topics,
            { state.value.rows.filterIsInstance<CatalogApp>() },
        ) { loaded ->
            knownPreviews.putAll(loaded)
            mutableState.value =
                state.value.copy(
                    rows =
                        state.value.rows.withTopicPreviews(
                            loaded,
                        ),
                )
        }
    }

    init {
        refresh()
    }

    fun changeDraft(
        value: String,
    ) {
        savedState["catalog_draft"] = value
        mutableState.value =
            state.value.copy(
                draft = value,
            )
    }

    fun search() {
        val query = state.value.draft
        replaceQuery(
            state.value.query.copy(
                search = query,
                topicId = 0,
            ),
        )
    }

    private fun replaceQuery(
        query: CatalogQuery,
    ) {
        savedState["catalog_search"] = query.search
        savedState["catalog_sorting"] = query.sorting.storageValue
        savedState["catalog_topic"] = query.topicId
        mutableState.value =
            state.value.copy(
                query = query,
                draft = query.search.orEmpty(),
            )
        refresh()
    }

    fun section(
        id: Int,
    ) {
        savedState["catalog_section"] = id
        replaceQuery(
            state.value.query.copy(
                groupId = id,
                search = null,
                topicId = 0,
            ),
        )
    }

    fun refresh() {
        queryScope.coroutineContext.cancelChildren()
        previews.reset()
        knownPreviews.clear()
        nextPageUrl = null
        mutableState.value =
            state.value.copy(
                rows =
                    if (state.value.query.search == null)
                        ForumSections.children(
                                state.value.query.groupId.takeIf { it != 0 }
                                    ?: ForumSections.APPLICATIONS,
                            )
                            .map {
                                CatalogCategory(
                                    it.id,
                                    it.name,
                                    it.icon,
                                )
                            }
                    else emptyList(),
                page = -1,
                total = 0,
                hasMore = false,
                loading = false,
                error = false,
                failure = null,
            )
        load(
            0,
        )
    }

    fun nextPage() {
        if (!state.value.loading && state.value.hasMore && !state.value.error)
            load(
                state.value.page + 1,
            )
    }

    fun retry() {
        if (!state.value.loading)
            load(
                failedPage,
                failedContinuation,
                failedRows,
            )
    }

    private fun load(
        page: Int,
        next: String? = nextPageUrl,
        previousRows: List<CatalogRow> = if (page == 0) emptyList() else state.value.rows,
    ) {
        if (state.value.loading) return
        val query = state.value.query
        val continuation = if (page == 0) null else next
        failedPage = page
        failedContinuation = continuation
        failedRows = previousRows
        mutableState.value =
            state.value.copy(
                loading = true,
                error = false,
                failure = null,
                searchLimited = false,
            )
        queryScope.launch {
            try {
                val cached = repository.cached(query, page, continuation)
                cached?.let {
                    publishPage(it, previousRows, refreshing = true)
                }
                val result =
                    repository.load(
                        query,
                        page,
                        continuation,
                        background = cached != null,
                    )
                currentCoroutineContext().ensureActive()
                publishPage(result, previousRows, refreshing = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                searchFailed(error)
            } catch (error: IllegalArgumentException) {
                searchFailed(error)
            } catch (error: IllegalStateException) {
                searchFailed(error)
            }
        }
    }

    private fun publishPage(
        result: CatalogPage,
        previousRows: List<CatalogRow>,
        refreshing: Boolean,
    ) {
        nextPageUrl = result.nextPageUrl
        mutableState.value =
            state.value.copy(
                rows =
                    (previousRows + result.rows)
                        .distinctBy { it.key }
                        .withTopicPreviews(knownPreviews),
                page = result.page,
                total = result.total,
                hasMore = result.hasMore,
                loading = refreshing,
                error = false,
                failure = null,
            )
    }

    private suspend fun searchFailed(error: Throwable) {
        currentCoroutineContext().ensureActive()
        if (error is ForumApiException)
            Log.w(
                "ForumSearch",
                "Failure ${error.reason}, HTTP ${error.statusCode}",
            )
        else
            Log.w(
                "ForumSearch",
                "Failure type ${error.javaClass.simpleName}",
            )
        mutableState.value =
            state.value.copy(
                loading = false,
                error = true,
                failure = error,
                searchLimited =
                    (error as? ForumApiException)?.statusCode ==
                        HttpStatusCode.TooManyRequests.value,
            )
    }
}
