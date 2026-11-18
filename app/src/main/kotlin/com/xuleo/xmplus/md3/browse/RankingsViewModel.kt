package com.xuleo.xmplus.md3.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xuleo.xmplus.md3.core.model.RankBlock
import com.xuleo.xmplus.md3.core.model.RankCategory
import com.xuleo.xmplus.md3.core.model.RankTab
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

/** The charts of one ranking tab. [blocks] is null until they first arrive. */
data class RankCharts(
    val blocks: List<RankBlock>? = null,
    val loading: Boolean = false,
    val errorCode: String? = null,
)

data class RankingsState(
    /** Only categories that have tabs; null until they arrive. */
    val categories: List<RankCategory>? = null,
    val loading: Boolean = false,
    val errorCode: String? = null,
    val categoryId: Int? = null,
    val rankingId: Int? = null,
    val charts: Map<Int, RankCharts> = emptyMap(),
    val refreshing: Boolean = false,
) {
    val category: RankCategory? get() = categories?.firstOrNull { it.id == categoryId }
    val tab: RankTab? get() = category?.tabs?.firstOrNull { it.rankingId == rankingId }
    val current: RankCharts? get() = charts[rankingId]
}

/** 排行: the site's charts, a category and a tab at a time. Charts already seen stay until refreshed. */
class RankingsViewModel(private val discovery: XmlyDiscovery) : ViewModel() {
    private val mutable = MutableStateFlow(RankingsState())
    val state: StateFlow<RankingsState> = mutable.asStateFlow()
    private var generation = 0
    private var tabsJob: Job? = null
    private val chartJobs = HashMap<Int, Job>()

    fun ensureLoaded() {
        val state = mutable.value
        if (state.categories == null && !state.loading) loadTabs()
    }

    fun selectCategory(id: Int) {
        val category = mutable.value.categories?.firstOrNull { it.id == id } ?: return
        if (mutable.value.categoryId == id) return
        val first = category.tabs.first().rankingId
        mutable.update { it.copy(categoryId = id, rankingId = first) }
        if (mutable.value.charts[first]?.blocks == null) loadCharts(first)
    }

    fun selectTab(rankingId: Int) {
        if (mutable.value.rankingId == rankingId) return
        mutable.update { it.copy(rankingId = rankingId) }
        if (mutable.value.charts[rankingId]?.blocks == null) loadCharts(rankingId)
    }

    /** Reloads the chart on screen. */
    fun refresh() {
        mutable.value.rankingId?.let { loadCharts(it, refresh = true) } ?: retry()
    }

    fun retry() {
        val state = mutable.value
        val id = state.rankingId
        if (state.categories == null || id == null) {
            if (!state.loading) loadTabs()
        } else if (state.charts[id]?.loading != true) {
            loadCharts(id)
        }
    }

    private fun loadTabs() {
        val current = ++generation
        tabsJob?.cancel()
        mutable.update { it.copy(loading = true, errorCode = null) }
        tabsJob = viewModelScope.launch {
            try {
                val categories = withContext(Dispatchers.Default) { discovery.rankTabs() }.filter { it.tabs.isNotEmpty() }
                if (current != generation) return@launch
                val category = categories.firstOrNull { it.id == mutable.value.categoryId } ?: categories.firstOrNull()
                val tab = category?.tabs?.firstOrNull { it.rankingId == mutable.value.rankingId } ?: category?.tabs?.first()
                mutable.update { it.copy(categories = categories, loading = false, categoryId = category?.id, rankingId = tab?.rankingId) }
                tab?.let { loadCharts(it.rankingId) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (current == generation) mutable.update { it.copy(loading = false, errorCode = codeOf(failure)) }
            }
        }
    }

    private fun loadCharts(rankingId: Int, refresh: Boolean = false) {
        val current = generation
        chartJobs[rankingId]?.cancel()
        update(rankingId) { it.copy(loading = true, errorCode = null) }
        if (refresh) mutable.update { it.copy(refreshing = true) }
        chartJobs[rankingId] = viewModelScope.launch {
            try {
                val blocks = withContext(Dispatchers.Default) { discovery.rankElements(rankingId) }
                if (current != generation) return@launch
                update(rankingId) { it.copy(blocks = blocks, loading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (current == generation) update(rankingId) { it.copy(loading = false, errorCode = codeOf(failure)) }
            } finally {
                if (refresh && current == generation) mutable.update { it.copy(refreshing = false) }
            }
        }
    }

    private fun update(rankingId: Int, change: (RankCharts) -> RankCharts) = mutable.update { state ->
        state.copy(charts = state.charts + (rankingId to change(state.charts[rankingId] ?: RankCharts())))
    }

    companion object {
        fun factory(discovery: XmlyDiscovery): ViewModelProvider.Factory = viewModelFactory { initializer { RankingsViewModel(discovery) } }
    }
}