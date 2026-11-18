package com.xuleo.xmplus.md3.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.AnchorProfile
import com.xuleo.xmplus.md3.core.model.HotWord
import com.xuleo.xmplus.md3.core.model.SearchKind
import com.xuleo.xmplus.md3.core.model.SearchPage
import com.xuleo.xmplus.md3.core.model.Track
import com.xuleo.xmplus.md3.core.model.dartTrim
import com.xuleo.xmplus.md3.core.protocol.XmlyDiscovery
import com.xuleo.xmplus.md3.core.protocol.XmlySearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What one kind of result has loaded so far. [page] is 0 until the first page arrives. */
data class SearchResult(
    val albums: List<Album> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val anchors: List<AnchorProfile> = emptyList(),
    val totals: Map<SearchKind, Int> = emptyMap(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val errorCode: String? = null,
) {
    val loaded: Boolean get() = page > 0
    val empty: Boolean get() = albums.isEmpty() && tracks.isEmpty() && anchors.isEmpty()
}

data class SearchState(
    val hotWords: List<HotWord> = emptyList(),
    val keyword: String? = null,
    val kind: SearchKind = SearchKind.All,
    val results: Map<SearchKind, SearchResult> = emptyMap(),
    /** The last keyword was empty or too long, so nothing was sent. */
    val invalid: Boolean = false,
)

/**
 * Hot words and site-wide search. Each kind keeps its own results and paging for the submitted keyword, so
 * switching back to a kind is instant; a new keyword starts every kind over.
 */
class SearchViewModel(
    private val search: XmlySearch,
    private val discovery: XmlyDiscovery,
) : ViewModel() {
    private val mutable = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = mutable.asStateFlow()
    private var generation = 0
    private val jobs = HashMap<SearchKind, Job>()
    private var hotJob: Job? = null

    init {
        loadHotWords()
    }

    /** Fetches hot words if there are none yet; failing is quiet because they only decorate. */
    fun loadHotWords() {
        if (mutable.value.hotWords.isNotEmpty() || hotJob?.isActive == true) return
        hotJob = viewModelScope.launch {
            try {
                val words = withContext(Dispatchers.Default) { discovery.hotWords() }
                mutable.update { it.copy(hotWords = words) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
        }
    }

    fun submit(raw: String) {
        val keyword = raw.dartTrim()
        cancelAll()
        if (keyword.isEmpty() || raw.length > MAX_KEYWORD) {
            mutable.update { it.copy(keyword = null, results = emptyMap(), invalid = true) }
            return
        }
        mutable.update { it.copy(keyword = keyword, results = emptyMap(), invalid = false) }
        load(mutable.value.kind, 1)
    }

    fun selectKind(kind: SearchKind) {
        if (mutable.value.kind == kind) return
        mutable.update { it.copy(kind = kind) }
        if (mutable.value.keyword != null && mutable.value.results[kind] == null) load(kind, 1)
    }

    fun loadMore() {
        val kind = mutable.value.kind
        val result = mutable.value.results[kind] ?: return
        if (kind != SearchKind.All && result.hasMore && !result.loading && result.errorCode == null) load(kind, result.page + 1)
    }

    /** Tries again from the page that failed. */
    fun retry(kind: SearchKind) {
        val result = mutable.value.results[kind]
        if (result?.loading != true) load(kind, (result?.page ?: 0) + 1)
    }

    fun clear() {
        cancelAll()
        mutable.update { it.copy(keyword = null, kind = SearchKind.All, results = emptyMap(), invalid = false) }
    }

    fun dismissInvalid() = mutable.update { it.copy(invalid = false) }

    private fun cancelAll() {
        generation++
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }

    private fun load(kind: SearchKind, page: Int) {
        val keyword = mutable.value.keyword ?: return
        val current = generation
        val size = if (kind == SearchKind.All) ALL_PAGE_SIZE else PAGE_SIZE
        jobs[kind]?.cancel()
        update(kind) { it.copy(loading = true, errorCode = null) }
        jobs[kind] = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) { search.search(keyword, kind, page, size) }
                if (current == generation) update(kind) { it.merged(result, kind) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (current == generation) update(kind) { it.copy(loading = false, errorCode = codeOf(failure)) }
            }
        }
    }

    private fun update(kind: SearchKind, change: (SearchResult) -> SearchResult) = mutable.update { state ->
        state.copy(results = state.results + (kind to change(state.results[kind] ?: SearchResult())))
    }

    companion object {
        const val MAX_KEYWORD = 200
        /** 全部 shows a short preview of each kind; the kind's own tab pages through all of it. */
        const val ALL_PAGE_SIZE = 6
        const val PAGE_SIZE = 30

        fun factory(search: XmlySearch, discovery: XmlyDiscovery): ViewModelProvider.Factory = viewModelFactory {
            initializer { SearchViewModel(search, discovery) }
        }
    }
}

/** Adds a page to what is already shown; a row that appears twice (across pages, say) is kept once. */
private fun SearchResult.merged(page: SearchPage, kind: SearchKind): SearchResult {
    val first = page.page == 1
    val albums = (if (first) emptyList() else albums).plusNew(page.albums) { it.id }
    val tracks = (if (first) emptyList() else tracks).plusNew(page.tracks) { it.id }
    val anchors = (if (first) emptyList() else anchors).plusNew(page.anchors) { it.uid }
    val received = when (kind) {
        SearchKind.Album -> page.albums.size
        SearchKind.Track -> page.tracks.size
        SearchKind.Anchor -> page.anchors.size
        SearchKind.All -> 0
    }
    return SearchResult(
        albums = albums, tracks = tracks, anchors = anchors, totals = page.totals, page = page.page,
        hasMore = kind != SearchKind.All && received > 0 && page.page * page.pageSize < page.total,
    )
}

private fun <T> List<T>.plusNew(more: List<T>, key: (T) -> String): List<T> {
    val seen = mapTo(HashSet(), key)
    return this + more.filter { seen.add(key(it)) }
}