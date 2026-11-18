package com.xuleo.xmplus.md3.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xuleo.xmplus.md3.core.model.CategoryChannel
import com.xuleo.xmplus.md3.core.model.CategoryGroup
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

/** The channels of one category group, loaded a page at a time. */
data class ChannelList(
    val items: List<CategoryChannel> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val errorCode: String? = null,
) {
    val loaded: Boolean get() = page > 0
}

data class CategoriesState(
    val groups: List<CategoryGroup>? = null,
    val groupsLoading: Boolean = false,
    val groupsError: String? = null,
    val groupId: Int? = null,
    val lists: Map<Int, ChannelList> = emptyMap(),
    /** Pull to refresh is running over groups already shown. */
    val refreshing: Boolean = false,
) {
    val group: CategoryGroup? get() = groups?.firstOrNull { it.id == groupId }
    val list: ChannelList? get() = lists[groupId]
}

/** 分类: the category groups, and the channels of the selected group. */
class CategoriesViewModel(private val discovery: XmlyDiscovery) : ViewModel() {
    private val mutable = MutableStateFlow(CategoriesState())
    val state: StateFlow<CategoriesState> = mutable.asStateFlow()
    private var generation = 0
    private var groupsJob: Job? = null
    private val channelJobs = HashMap<Int, Job>()

    /** Loads the groups the first time 分类 is shown. */
    fun ensureLoaded() {
        val state = mutable.value
        if (state.groups == null && !state.groupsLoading) loadGroups(refresh = false)
    }

    fun refresh() = loadGroups(refresh = true)

    fun selectGroup(id: Int) {
        if (mutable.value.groupId == id) return
        mutable.update { it.copy(groupId = id) }
        if (mutable.value.lists[id] == null) loadChannels(id, 1)
    }

    fun loadMore() {
        val id = mutable.value.groupId ?: return
        val list = mutable.value.lists[id] ?: return
        if (list.loaded && list.hasMore && !list.loading && list.errorCode == null) loadChannels(id, list.page + 1)
    }

    /** Tries again: the groups if they never arrived, otherwise the channel page that failed. */
    fun retry() {
        val state = mutable.value
        val id = state.groupId
        if (state.groups == null || id == null) {
            if (!state.groupsLoading) loadGroups(refresh = false)
        } else if (state.lists[id]?.loading != true) {
            loadChannels(id, (state.lists[id]?.page ?: 0) + 1)
        }
    }

    private fun loadGroups(refresh: Boolean) {
        val current = ++generation
        groupsJob?.cancel()
        channelJobs.values.forEach { it.cancel() }
        channelJobs.clear()
        mutable.update { it.copy(groupsLoading = true, groupsError = null, refreshing = refresh && it.groups != null) }
        groupsJob = viewModelScope.launch {
            try {
                val groups = withContext(Dispatchers.Default) { discovery.categoryGroups() }
                if (current != generation) return@launch
                // Keep the group the user was on when it still exists.
                val selected = mutable.value.groupId?.takeIf { id -> groups.any { it.id == id } } ?: groups.firstOrNull()?.id
                mutable.update { it.copy(groups = groups, groupsLoading = false, refreshing = false, groupId = selected, lists = emptyMap()) }
                if (selected != null) loadChannels(selected, 1)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (current == generation) mutable.update { it.copy(groupsLoading = false, refreshing = false, groupsError = codeOf(failure)) }
            }
        }
    }

    private fun loadChannels(groupId: Int, page: Int) {
        val current = generation
        channelJobs[groupId]?.cancel()
        update(groupId) { it.copy(loading = true, errorCode = null) }
        channelJobs[groupId] = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) { discovery.categoryChannels(groupId, page) }
                if (current != generation) return@launch
                update(groupId) { old ->
                    val known = if (page == 1) HashSet() else old.items.mapTo(HashSet()) { it.channelId }
                    old.copy(
                        items = (if (page == 1) emptyList() else old.items) + result.items.filter { known.add(it.channelId) },
                        page = result.page, hasMore = result.hasMore && result.items.isNotEmpty(), loading = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (current == generation) update(groupId) { it.copy(loading = false, errorCode = codeOf(failure)) }
            }
        }
    }

    private fun update(groupId: Int, change: (ChannelList) -> ChannelList) = mutable.update { state ->
        state.copy(lists = state.lists + (groupId to change(state.lists[groupId] ?: ChannelList())))
    }

    companion object {
        fun factory(discovery: XmlyDiscovery): ViewModelProvider.Factory = viewModelFactory { initializer { CategoriesViewModel(discovery) } }
    }
}