package com.xuleo.xmplus.md3.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.AlbumStats
import com.xuleo.xmplus.md3.core.model.BrowseCategory
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.protocol.XmlyBrowse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AllAlbumsState(
    val categories: List<BrowseCategory> = emptyList(),
    val category: Int = 0,
    /** 1 综合, 2 最近更新, 3 最多播放. */
    val sort: Int = 1,
    val albums: List<Album> = emptyList(),
    val stats: Map<String, AlbumStats> = emptyMap(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    /** A reload of page one while the albums already shown stay on screen. */
    val refreshing: Boolean = false,
    val errorCode: String? = null,
) {
    val loaded: Boolean get() = page > 0
}

/**
 * 全部专辑: every album the site lists, by category and sort. Changing either starts again from page one;
 * further pages are appended without repeating an album.
 */
class AllAlbumsViewModel(private val browse: XmlyBrowse) : ViewModel() {
    private val mutable = MutableStateFlow(AllAlbumsState())
    val state: StateFlow<AllAlbumsState> = mutable.asStateFlow()
    private var generation = 0
    private var job: Job? = null
    private var categoriesJob: Job? = null
    private var started = false

    /** Opens the list for [category], with the default sort. */
    fun start(category: Int) {
        started = true
        mutable.update { it.copy(category = category, sort = 1, albums = emptyList(), stats = emptyMap(), page = 0, hasMore = false, errorCode = null) }
        loadCategories()
        load(1)
    }

    /** For a page restored without its state. */
    fun ensureStarted(category: Int) {
        if (!started) start(category)
    }

    fun select(category: Int? = null, sort: Int? = null) {
        val before = mutable.value
        val nextCategory = category ?: before.category
        val nextSort = sort ?: before.sort
        if (nextCategory == before.category && nextSort == before.sort) return
        mutable.update { it.copy(category = nextCategory, sort = nextSort, albums = emptyList(), stats = emptyMap(), page = 0, hasMore = false, errorCode = null) }
        load(1)
    }

    fun refresh() = load(1)

    fun loadMore() {
        val state = mutable.value
        if (state.loaded && state.hasMore && !state.loading && state.errorCode == null) load(state.page + 1)
    }

    /** Tries again from the page that failed. */
    fun retry() {
        if (!mutable.value.loading) load(mutable.value.page + 1)
    }

    fun retryCategories() = loadCategories()

    private fun loadCategories() {
        if (mutable.value.categories.isNotEmpty() || categoriesJob?.isActive == true) return
        categoriesJob = viewModelScope.launch {
            try {
                val categories = withContext(Dispatchers.Default) { browse.categories() }
                mutable.update { it.copy(categories = categories) }
                // A category the site no longer lists falls back to all of them.
                val state = mutable.value
                if (state.category != 0 && categories.none { it.id == state.category }) select(category = 0)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
        }
    }

    private fun load(page: Int) {
        val state = mutable.value
        val current = ++generation
        job?.cancel()
        // Page one on top of a list already shown is a refresh; the list stays until the new page lands.
        val reload = page == 1 && state.loaded
        mutable.update { it.copy(loading = true, refreshing = reload, errorCode = null) }
        job = viewModelScope.launch {
            try {
                val listing = withContext(Dispatchers.Default) { browse.albums(state.category, state.sort, page) }
                if (current != generation) return@launch
                mutable.update { now ->
                    val known = if (page == 1) HashSet() else now.albums.mapTo(HashSet()) { it.id }
                    val added = listing.albums.filter { known.add(it.id) }
                    // A page that adds nothing while the server says there is more would loop forever.
                    if (added.isEmpty() && (listing.hasMore || listing.albums.isNotEmpty()) && page > 1) throw XmlyException("repeated_page")
                    now.copy(
                        albums = (if (page == 1) emptyList() else now.albums) + added,
                        stats = (if (page == 1) emptyMap() else now.stats) + listing.stats,
                        page = listing.page, hasMore = listing.hasMore, loading = false, refreshing = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (current == generation) mutable.update { it.copy(loading = false, refreshing = false, errorCode = codeOf(failure)) }
            }
        }
    }

    companion object {
        fun factory(browse: XmlyBrowse): ViewModelProvider.Factory = viewModelFactory { initializer { AllAlbumsViewModel(browse) } }
    }
}