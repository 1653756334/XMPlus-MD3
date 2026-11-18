package com.xuleo.xmplus.md3.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.CategoryChannelInfo
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.protocol.XmlyDiscovery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CategoryAlbumsState(
    /** The filter rows of the channel; absent for a group's own 精选 and until they arrive. */
    val info: CategoryChannelInfo? = null,
    val infoFailed: Boolean = false,
    /** Filter row id to the chosen value id. */
    val filters: Map<Int, Int> = emptyMap(),
    /** 1 综合, 2 最新, 3 最多播放. */
    val sort: Int = 1,
    val albums: List<Album> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val errorCode: String? = null,
) {
    val loaded: Boolean get() = page > 0
}

/**
 * The albums of a category group, or of one channel in it, 60 a page. Changing the sort or the filters starts
 * again from page one; further pages are appended without repeating an album.
 */
class CategoryAlbumsViewModel(private val discovery: XmlyDiscovery) : ViewModel() {
    private val mutable = MutableStateFlow(CategoryAlbumsState())
    val state: StateFlow<CategoryAlbumsState> = mutable.asStateFlow()
    private var generation = 0
    private var job: Job? = null
    private var infoJob: Job? = null
    private var groupId = 0
    private var channelId: Int? = null
    private var started = false

    fun start(groupId: Int, channelId: Int?) {
        started = true
        this.groupId = groupId
        this.channelId = channelId
        infoJob?.cancel()
        mutable.value = CategoryAlbumsState()
        if (channelId != null) loadInfo()
        load(1)
    }

    /** For a page restored without its state. */
    fun ensureStarted(groupId: Int, channelId: Int?) {
        if (!started) start(groupId, channelId)
    }

    fun selectSort(sort: Int) {
        if (mutable.value.sort == sort) return
        mutable.update { it.copy(sort = sort, albums = emptyList(), page = 0, hasMore = false, errorCode = null) }
        load(1)
    }

    fun applyFilters(filters: Map<Int, Int>) {
        if (mutable.value.filters == filters) return
        mutable.update { it.copy(filters = filters, albums = emptyList(), page = 0, hasMore = false, errorCode = null) }
        load(1)
    }

    fun refresh() = load(1)

    fun loadMore() {
        val state = mutable.value
        if (state.loaded && state.hasMore && !state.loading && state.errorCode == null) load(state.page + 1)
    }

    fun retry() {
        if (!mutable.value.loading) load(mutable.value.page + 1)
    }

    fun retryInfo() = loadInfo()

    private fun loadInfo() {
        val id = channelId ?: return
        if (infoJob?.isActive == true) return
        mutable.update { it.copy(infoFailed = false) }
        val current = generation
        infoJob = viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.Default) { discovery.categoryInfo(id) }
                if (current == generation) mutable.update { it.copy(info = info) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (current == generation) mutable.update { it.copy(infoFailed = true) }
            }
        }
    }

    private fun load(page: Int) {
        val state = mutable.value
        val current = ++generation
        job?.cancel()
        mutable.update { it.copy(loading = true, refreshing = page == 1 && it.loaded, errorCode = null) }
        job = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    discovery.categoryAlbums(groupId, channelId, page, state.sort, state.filters)
                }
                if (current != generation) return@launch
                mutable.update { now ->
                    val known = if (page == 1) HashSet() else now.albums.mapTo(HashSet()) { it.id }
                    val added = result.items.filter { known.add(it.id) }
                    if (added.isEmpty() && page > 1 && (result.hasMore || result.items.isNotEmpty())) throw XmlyException("repeated_page")
                    now.copy(
                        albums = (if (page == 1) emptyList() else now.albums) + added,
                        page = result.page, hasMore = result.hasMore && result.items.isNotEmpty(), loading = false, refreshing = false,
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
        fun factory(discovery: XmlyDiscovery): ViewModelProvider.Factory = viewModelFactory { initializer { CategoryAlbumsViewModel(discovery) } }
    }
}