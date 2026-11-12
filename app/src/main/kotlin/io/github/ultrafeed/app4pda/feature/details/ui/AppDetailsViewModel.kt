package io.github.ultrafeed.app4pda.feature.details.ui

import android.database.SQLException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ultrafeed.app4pda.data.forum.ForumFileInfo
import io.github.ultrafeed.app4pda.feature.details.AppDetails
import io.github.ultrafeed.app4pda.feature.details.AppDetailsUiState
import io.github.ultrafeed.app4pda.feature.details.DetailsFile
import io.github.ultrafeed.app4pda.feature.details.DetailsTab
import io.github.ultrafeed.app4pda.feature.details.data.AppDetailsRepository
import io.github.ultrafeed.app4pda.feature.details.data.DetailsContext
import io.github.ultrafeed.app4pda.feature.details.data.LoadedDetails
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppDetailsViewModel(
    private val loadDetails:
        suspend (LoadedDetails?, Boolean, suspend (LoadedDetails) -> Unit) -> LoadedDetails,
    private val saved: SavedStateHandle,
    private val repository: AppDetailsRepository,
) : ViewModel() {
    constructor(
        repository: AppDetailsRepository,
        saved: SavedStateHandle,
        topic: Int,
        pack: String,
        initialSource: DetailsTab? = null,
    ) : this(
        { previous, refreshStores, onContent ->
            val requested =
                if (saved.contains("details_tab")) DetailsTab.fromSaved(saved["details_tab"])
                else initialSource
            repository.load(topic, pack, previous, onContent, requested, refreshStores)
        },
        saved,
        repository,
    ) {
        if (!saved.contains("details_tab") && initialSource != null) selectTab(initialSource)
    }

    private val mutableState =
        MutableStateFlow(AppDetailsUiState(tab = DetailsTab.fromSaved(saved["details_tab"])))

    @OptIn(ExperimentalCoroutinesApi::class)
    private val favorites =
        mutableState
            .map { it.data?.let { data -> data.topicId to data.packageName } }
            .distinctUntilChanged()
            .flatMapLatest { identity ->
                if (identity == null) flowOf(null)
                else
                    repository.actions.observeFavorite(identity.first, identity.second).map {
                        DetailsFavorite(identity, it)
                    }
            }
            .onStart { emit(null) }
    val state: StateFlow<AppDetailsUiState> =
        combine(mutableState, favorites) { ui, favorite ->
                val data = ui.data
                if (data != null && favorite?.identity == (data.topicId to data.packageName))
                    ui.copy(
                        data =
                            data.copy(
                                favorite = favorite.value,
                            ),
                    )
                else ui
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, mutableState.value)
    private var job: Job? = null
    private var languageJob: Job? = null
    private var retryAction: (() -> Unit)? = null
    private var context: DetailsContext? = null

    private val snapshot: LoadedDetails?
        get() = state.value.data?.let { data -> context?.let { LoadedDetails(data, it) } }

    private fun isShowing(selected: LoadedDetails): Boolean =
        snapshot?.let {
            it.data.topicId == selected.data.topicId &&
                it.data.packageName == selected.data.packageName &&
                it.context.userId == selected.context.userId
        } == true

    init {
        refresh()
    }

    fun refresh() {
        val refreshStores = snapshot != null
        languageJob?.cancel()
        job?.cancel()
        mutableState.value =
            mutableState.value.copy(loading = true, playLoading = false, error = false)
        job = viewModelScope.launch {
            try {
                val requestContext = currentCoroutineContext()
                val loaded =
                    loadDetails(snapshot, refreshStores) { content ->
                        withContext(Dispatchers.Main.immediate) {
                            requestContext.ensureActive()
                            context = content.context
                            mutableState.value =
                                mutableState.value.copy(
                                    data = content.data,
                                    loading = false,
                                    playLoading = true,
                                )
                        }
                    }
                currentCoroutineContext().ensureActive()
                val data = loaded.data
                context = loaded.context
                val shown = state.value.data.takeIf { state.value.playLoading }
                val completed =
                    if (shown == null) data
                    else
                        data.copy(
                            files =
                                (data.files + shown.files.filter { it.id.startsWith("external:") })
                                    .distinctBy { it.id },
                        )
                mutableState.value =
                    mutableState.value.copy(data = completed, loading = false, playLoading = false)
            } catch (_: IOException) {
                // A child operation's timeout does not cancel this screen's loading job.
                currentCoroutineContext().ensureActive()
                mutableState.value =
                    mutableState.value.copy(loading = false, playLoading = false, error = true)
            } catch (_: IllegalStateException) {
                // A child operation's timeout does not cancel this screen's loading job.
                currentCoroutineContext().ensureActive()
                mutableState.value =
                    mutableState.value.copy(loading = false, playLoading = false, error = true)
            } catch (_: IllegalArgumentException) {
                // A child operation's timeout does not cancel this screen's loading job.
                currentCoroutineContext().ensureActive()
                mutableState.value =
                    mutableState.value.copy(loading = false, playLoading = false, error = true)
            }
        }
    }

    fun refreshPlayLanguage() {
        languageJob?.cancel()
        languageJob = viewModelScope.launch {
            job?.join()
            val loaded = snapshot ?: return@launch
            if (loaded.context.playLanguage == repository.playLanguage()) return@launch
            mutableState.value =
                mutableState.value.copy(
                    data =
                        loaded.data.copy(
                            market = null,
                            files = loaded.data.files.filterNot { it.market },
                        ),
                    playLoading = true,
                )
            try {
                val localized = repository.refreshPlay(loaded)
                currentCoroutineContext().ensureActive()
                if (isShowing(loaded)) {
                    context = localized.context
                    val current = state.value.data
                    mutableState.value =
                        mutableState.value.copy(
                            data =
                                localized.data.copy(
                                    files =
                                        (localized.data.files +
                                                current?.files.orEmpty().filter {
                                                    it.id.startsWith("external:")
                                                })
                                            .distinctBy { it.id },
                                ),
                            playLoading = false,
                        )
                }
            } finally {
                if (currentCoroutineContext().isActive)
                    mutableState.value = mutableState.value.copy(playLoading = false)
            }
        }
    }

    fun selectTab(tab: DetailsTab) {
        saved["details_tab"] = tab.savedId
        mutableState.value = mutableState.value.copy(tab = tab)
    }

    fun retryError() {
        retryAction?.invoke() ?: refresh()
    }

    private fun action(source: String = "Downloads", block: suspend () -> Unit) {
        if (mutableState.value.actionBusy) return
        retryAction = { action(source, block) }
        mutableState.value =
            mutableState.value.copy(actionBusy = true, actionError = false, actionProblem = null)
        viewModelScope.launch {
            try {
                block()
                retryAction = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IOException) {
                currentCoroutineContext().ensureActive()
                mutableState.value =
                    mutableState.value.copy(
                        actionError = true,
                        actionProblem = repository.problem(source, failure),
                    )
            } catch (failure: IllegalStateException) {
                currentCoroutineContext().ensureActive()
                mutableState.value =
                    mutableState.value.copy(
                        actionError = true,
                        actionProblem = repository.problem(source, failure),
                    )
            } catch (failure: IllegalArgumentException) {
                currentCoroutineContext().ensureActive()
                mutableState.value =
                    mutableState.value.copy(
                        actionError = true,
                        actionProblem = repository.problem(source, failure),
                    )
            } catch (failure: SQLException) {
                currentCoroutineContext().ensureActive()
                mutableState.value =
                    mutableState.value.copy(
                        actionError = true,
                        actionProblem = repository.problem(source, failure),
                    )
            } finally {
                if (currentCoroutineContext().isActive)
                    mutableState.value = mutableState.value.copy(actionBusy = false)
            }
        }
    }

    fun favorite() {
        val selected = snapshot ?: return
        val favorite = !selected.data.favorite
        action("Favorites") {
            repository.actions.setFavorite(selected.context, favorite)
        }
    }

    fun binding(pack: String, choice: DetailsBinding): Unit = action {
        when (choice) {
            DetailsBinding.BIND -> repository.actions.bind(checkNotNull(context), pack)
            DetailsBinding.UNLINK -> repository.actions.unlink(pack)
        }
    }

    fun consumeResult(result: DetailsResult) {
        if (result == DetailsResult.ERROR) retryAction = null
        mutableState.value =
            when (result) {
                DetailsResult.FILE -> mutableState.value.copy(selectedFileId = null)
                DetailsResult.ERROR ->
                    mutableState.value.copy(
                        actionError = false,
                        actionProblem = null,
                    )
            }
    }

    fun openAttachment(url: String) {
        val data = state.value.data ?: return
        val known = data.fileForUrl(url)
        if (known != null) {
            mutableState.value = mutableState.value.copy(selectedFileId = known.id)
            return
        }
        val file = ForumFileInfo(url, data.topicId)
        val item =
            DetailsFile(
                "external:$url",
                file.fileName,
                "",
                "",
                url,
                data.topicId,
                0,
                url,
                false,
            )
        mutableState.value =
            mutableState.value.copy(
                data = data.copy(files = (data.files + item).distinctBy { it.id }),
                selectedFileId = item.id,
            )
    }
}

private data class DetailsFavorite(val identity: Pair<Int, String>, val value: Boolean)

internal fun AppDetails.fileForUrl(
    url: String,
): DetailsFile? = files.find {
    !it.market && (it.url == url || it.url.substringBefore('?') == url.substringBefore('?'))
}
