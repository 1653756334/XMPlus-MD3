package com.xuleo.xmplus.md3.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xuleo.xmplus.md3.core.account.AccountRepository
import com.xuleo.xmplus.md3.core.account.AccountsState
import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.HomeChannel
import com.xuleo.xmplus.md3.core.model.HomeContent
import com.xuleo.xmplus.md3.core.model.HomeTab
import com.xuleo.xmplus.md3.core.model.HotRanking
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.protocol.XmlyBrowse
import com.xuleo.xmplus.md3.core.protocol.XmlyHome
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ChannelsState {
    data object Loading : ChannelsState
    data class Failed(val code: String) : ChannelsState
    data class Ready(val channels: List<HomeChannel>) : ChannelsState
}

/** What one channel has loaded. Each sub-tab keeps its own content so switching back is instant. */
data class ChannelState(
    val tabs: List<HomeTab> = emptyList(),
    val tabId: Int? = null,
    val contents: Map<Int?, HomeContent> = emptyMap(),
    val loading: Boolean = false,
    val errorCode: String? = null,
    val ranking: HotRanking? = null,
    val rankingFailed: Boolean = false,
) {
    val content: HomeContent? get() = contents[tabId]
}

data class DiscoverState(
    val channels: ChannelsState = ChannelsState.Loading,
    val pages: Map<Int, ChannelState> = emptyMap(),
)

/**
 * Loads 发现: the channels, and each channel's content when it is first shown. Content depends on the primary
 * account, so changing it starts over; an expired login quietly falls back to the public content.
 */
class DiscoverViewModel(
    private val home: XmlyHome,
    private val browse: XmlyBrowse,
    private val accounts: AccountRepository,
) : ViewModel() {
    private val mutable = MutableStateFlow(DiscoverState())
    val state: StateFlow<DiscoverState> = mutable.asStateFlow()
    private var generation = 0
    private var channelsJob: Job? = null
    private val pageJobs = HashMap<Int, Job>()
    private val rankingJobs = HashMap<Int, Job>()

    init {
        viewModelScope.launch {
            accounts.state.first { it !is AccountsState.Loading }
            accounts.state.map { (it as? AccountsState.Ready)?.accounts?.firstOrNull { account -> account.primary }?.identity }
                .distinctUntilChanged().collect { reload() }
        }
    }

    fun reload() {
        generation++
        channelsJob?.cancel()
        pageJobs.values.forEach { it.cancel() }
        rankingJobs.values.forEach { it.cancel() }
        pageJobs.clear()
        rankingJobs.clear()
        mutable.value = DiscoverState()
        val current = generation
        channelsJob = viewModelScope.launch {
            try {
                val channels = withAccount { home.channels(it) }
                if (channels.isEmpty()) throw XmlyException("empty_channels")
                if (current == generation) mutable.update { it.copy(channels = ChannelsState.Ready(channels)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (current == generation) mutable.update { it.copy(channels = ChannelsState.Failed(codeOf(failure))) }
            }
        }
    }

    /** Shows [pageId]: loads it if it has nothing yet, or reloads it when [refresh] is set. */
    fun load(pageId: Int, refresh: Boolean = false) {
        val page = mutable.value.pages[pageId]
        if (refresh || (page?.content == null && pageJobs[pageId]?.isActive != true)) fetch(pageId, page?.tabId)
        if (refresh || page?.ranking == null) fetchRanking(pageId)
    }

    fun selectTab(pageId: Int, tabId: Int) {
        val page = mutable.value.pages[pageId] ?: return
        if (page.tabId == tabId) return
        update(pageId) { it.copy(tabId = tabId, errorCode = null) }
        if (page.contents[tabId] == null) fetch(pageId, tabId)
    }

    fun retryRanking(pageId: Int) = fetchRanking(pageId)

    private fun fetch(pageId: Int, requestedTab: Int?) {
        val current = generation
        pageJobs[pageId]?.cancel()
        update(pageId) { it.copy(loading = true, errorCode = null) }
        pageJobs[pageId] = viewModelScope.launch {
            try {
                var tab = requestedTab
                var content = withAccount { home.home(it, pageId, tab) }
                // A channel with sub-tabs opens on its first one.
                if (content.tabs.isNotEmpty() && tab == null) {
                    tab = content.tabs.first().tabId
                    content = withAccount { home.home(it, pageId, tab) }
                }
                if (current != generation) return@launch
                update(pageId) { page ->
                    val tabs = content.tabs.ifEmpty { if (tab != null) page.tabs else emptyList() }
                    page.copy(tabs = tabs, tabId = tab, contents = page.contents + (tab to content), loading = false, errorCode = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (current == generation) update(pageId) { it.copy(loading = false, errorCode = codeOf(failure)) }
            }
        }
    }

    private fun fetchRanking(pageId: Int) {
        val current = generation
        val categoryId = (mutable.value.channels as? ChannelsState.Ready)?.channels?.firstOrNull { it.pageId == pageId }?.categoryId ?: 0
        rankingJobs[pageId]?.cancel()
        rankingJobs[pageId] = viewModelScope.launch {
            try {
                val ranking = withContext(Dispatchers.Default) { browse.hotRanking(categoryId) }
                if (current == generation) update(pageId) { it.copy(ranking = ranking, rankingFailed = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (current == generation) update(pageId) { it.copy(rankingFailed = true) }
            }
        }
    }

    private fun update(pageId: Int, change: (ChannelState) -> ChannelState) = mutable.update { state ->
        state.copy(pages = state.pages + (pageId to change(state.pages[pageId] ?: ChannelState())))
    }

    private suspend fun <T> withAccount(block: suspend (Credentials?) -> T): T = withContext(Dispatchers.Default) {
        val credentials = accounts.primaryCredentials()
        try {
            block(credentials)
        } catch (failure: XmlyException) {
            if (credentials != null && failure.code == "login_expired") block(null) else throw failure
        }
    }

    companion object {
        fun factory(home: XmlyHome, browse: XmlyBrowse, accounts: AccountRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { DiscoverViewModel(home, browse, accounts) }
        }
    }
}

/** The machine-readable reason a load failed; the screen turns it into words. */
internal fun codeOf(failure: Throwable): String = (failure as? XmlyException)?.code ?: if (failure is IOException) "network" else "unknown"