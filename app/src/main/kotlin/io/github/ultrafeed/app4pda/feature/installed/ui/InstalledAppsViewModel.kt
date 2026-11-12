package io.github.ultrafeed.app4pda.feature.installed.ui

import android.database.SQLException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ultrafeed.app4pda.core.packages.PackageInspector
import io.github.ultrafeed.app4pda.feature.installed.AppSection
import io.github.ultrafeed.app4pda.feature.installed.InstalledApp
import io.github.ultrafeed.app4pda.feature.installed.InstalledAppsState
import io.github.ultrafeed.app4pda.feature.installed.InstalledSort
import io.github.ultrafeed.app4pda.feature.installed.UpdateProvider
import io.github.ultrafeed.app4pda.feature.installed.UpdateSource
import io.github.ultrafeed.app4pda.feature.installed.data.InstalledAppsRepository
import io.github.ultrafeed.app4pda.feature.installed.data.InstalledAppsStore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InstalledAppsUiState(
    val source: InstalledAppsState,
    val section: AppSection,
    val query: String,
    val sorting: InstalledSort,
    val rows: List<InstalledApp>,
    val actionBusy: Boolean = false,
)

class InstalledAppsViewModel(
    private val repository: InstalledAppsRepository,
    private val saved: SavedStateHandle,
    initial: AppSection,
    initialSorting: InstalledSort,
    private val records: InstalledAppsStore,
    private val packages: PackageInspector,
) : ViewModel() {
    private val mutableMutation = MutableStateFlow(false)

    init {
        if (!saved.contains("selected_section")) saved["selected_section"] = initial.name
        if (!saved.contains("sorting")) saved["sorting"] = initialSorting.storageValue
        repository.refresh()
    }

    val state =
        combine(
                repository.state,
                saved.getStateFlow("selected_section", initial.name),
                saved.getStateFlow("query", ""),
                saved
                    .getStateFlow("sorting", initialSorting.storageValue)
                    .map(InstalledSort::fromStorage),
                mutableMutation,
            ) { source, section, query, sorting, busy ->
                val selectedSection = AppSection.valueOf(section)
                InstalledAppsUiState(
                    source,
                    selectedSection,
                    query,
                    sorting,
                    filterApps(source.apps, selectedSection, sorting),
                    busy,
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                InstalledAppsUiState(
                    repository.state.value,
                    initial,
                    saved["query"] ?: "",
                    InstalledSort.fromStorage(saved["sorting"] ?: initialSorting.storageValue),
                    emptyList(),
                ),
            )

    private val mutableBindingSearch = MutableStateFlow<String?>(null)
    val bindingSearch = mutableBindingSearch.asStateFlow()
    private var bindingRequest: Job? = null

    suspend fun bindingQuery(app: InstalledApp): String? {
        if (bindingRequest?.isActive == true) return null
        val request =
            viewModelScope.async(start = CoroutineStart.LAZY) {
                packages.bindingSearchName(app.packageName, app.name)
            }
        bindingRequest = request
        mutableBindingSearch.value = app.key
        request.start()
        return try {
            request.await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: IOException) {
            repository.reportFailure(UpdateSource.OPERATION, failure)
            null
        } catch (failure: IllegalStateException) {
            repository.reportFailure(UpdateSource.OPERATION, failure)
            null
        } catch (failure: SQLException) {
            repository.reportFailure(UpdateSource.OPERATION, failure)
            null
        } finally {
            request.cancel()
            mutableBindingSearch.value = null
        }
    }

    fun search(value: String) {
        saved["query"] = value
    }

    fun section(value: AppSection) {
        saved["selected_section"] = value.name
    }

    fun sort(value: InstalledSort) {
        saved["sorting"] = value.storageValue
    }

    fun refresh() = repository.requestUserRefresh()

    fun dismissErrors() = repository.dismissErrors()

    private var mutation: Job? = null

    private fun update(action: suspend () -> Boolean): Job {
        mutation
            ?.takeIf { it.isActive }
            ?.let {
                return it
            }
        mutableMutation.value = true
        val job =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                try {
                    check(action())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: IOException) {
                    repository.reportFailure(UpdateSource.OPERATION, failure)
                } catch (failure: IllegalStateException) {
                    repository.reportFailure(UpdateSource.OPERATION, failure)
                } catch (failure: SQLException) {
                    repository.reportFailure(UpdateSource.OPERATION, failure)
                } finally {
                    mutableMutation.value = false
                }
            }
        mutation = job
        job.start()
        return job
    }

    fun hidden(app: InstalledApp, hidden: Boolean) = update {
        records.choices.setHidden(app.key, hidden).also { changed ->
            if (changed && !hidden && !app.bindingSearchCompleted)
                repository.refresh(forceSources = setOf(UpdateSource.FORUM))
        }
    }

    fun ignoreSource(
        app: InstalledApp,
        source: UpdateProvider,
    ) = update {
        when (source) {
            UpdateProvider.FORUM ->
                records.choices
                    .setForumUpdatesIgnored(app.key, !app.preferences.ignoreForum)
                    .also { changed ->
                        if (changed && app.preferences.ignoreForum && !app.bindingSearchCompleted)
                            repository.refresh(forceSources = setOf(UpdateSource.FORUM))
                    }
            UpdateProvider.PLAY ->
                records.choices.setMarketUpdatesIgnored(app.key, !app.preferences.ignoreMarket)
            UpdateProvider.RUSTORE ->
                records.choices.setRuStoreUpdatesIgnored(app.key, !app.preferences.ignoreRuStore)
        }
    }

    companion object {
        fun filterApps(
            apps: List<InstalledApp>,
            section: AppSection,
            sorting: InstalledSort,
        ): List<InstalledApp> {
            val filtered = apps.filter { it.belongsTo(section) }
            val byName =
                compareBy<InstalledApp, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                    .thenBy { it.key }
            val byDate = compareByDescending<InstalledApp> { it.forum.updatedAt }.then(byName)
            return filtered.sortedWith(
                when (sorting) {
                    InstalledSort.NAME -> byName
                    InstalledSort.UPDATES_BY_DATE ->
                        compareByDescending<InstalledApp> { it.visibleUpdate }.then(byDate)

                    InstalledSort.UPDATES_BY_NAME ->
                        compareByDescending<InstalledApp> { it.visibleUpdate }.then(byName)

                    InstalledSort.UPDATED -> byDate
                },
            )
        }
    }
}
