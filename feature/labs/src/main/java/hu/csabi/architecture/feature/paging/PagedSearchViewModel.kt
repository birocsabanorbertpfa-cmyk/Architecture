package hu.csabi.architecture.feature.paging

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.csabi.architecture.domain.model.Repo
import hu.csabi.architecture.domain.usecase.SearchRepositoriesUseCase
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * Lesson 09 — paging keeps its own state, so the ViewModel holds almost none.
 *
 * There is no `isLoading`, no list and no error field here: `LazyPagingItems` carries both
 * the items and the load states, and the screen reads them directly. The only state worth
 * owning is the query.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class PagedSearchViewModel @Inject constructor(
    private val searchRepositories: SearchRepositoriesUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, INITIAL_QUERY)

    /**
     * `cachedIn` is not optional, and leaving it out is the most common paging bug.
     *
     * Without it every new collector — a rotation, a tab switch, any recomposition that
     * restarts collection — starts a fresh `PagingData`, so the list jumps to the top and
     * re-fetches page 1. `cachedIn(viewModelScope)` makes the stream multicast and keeps the
     * loaded pages alive as long as the ViewModel.
     *
     * It must be the **last** operator: anything applied after it runs per collector again.
     */
    val repos: Flow<PagingData<Repo>> = query
        .debounce(DEBOUNCE_MILLIS)
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { text -> searchRepositories.paged(text) }
        .cachedIn(viewModelScope)

    fun onQueryChange(value: String) {
        savedStateHandle[KEY_QUERY] = value
    }

    private companion object {
        const val KEY_QUERY = "paged_query"
        const val DEBOUNCE_MILLIS = 350L

        /** A non-empty default, so the tab shows a real paged list on first open. */
        const val INITIAL_QUERY = "kotlin"
    }
}
