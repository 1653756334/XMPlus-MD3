package com.xuleo.xmplus.md3.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xuleo.xmplus.md3.XmApplication
import com.xuleo.xmplus.md3.browse.SearchHistoryStore
import com.xuleo.xmplus.md3.core.model.PluginMedia
import com.xuleo.xmplus.md3.core.model.SearchHistory
import com.xuleo.xmplus.md3.core.plugin.InstalledPlugin
import com.xuleo.xmplus.md3.core.plugin.MusicCatalog
import com.xuleo.xmplus.md3.core.plugin.SearchType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Results are kept per plugin and type, so switching back to one already searched shows it again at once. */
data class MusicSearchState(
    val keyword: String? = null,
    val history: List<String> = emptyList(),
    val results: Map<Pair<String, SearchType>, Paged<PluginMedia>> = emptyMap(),
    /** A search opened for one plugin and type, as when a song's artist or album was not found: shown first. */
    val pinned: Pair<String, SearchType>? = null,
)

/**
 * The types a plugin searches, as it declares them; all of them when it does not say. Its `defaultSearchType` comes
 * first, so it is the one opened.
 */
fun InstalledPlugin.searchTypes(): List<SearchType> {
    val declared = (info.declared["supportedSearchType"] as? JsonArray)
        ?.mapNotNull { SearchType.fromKey((it as? JsonPrimitive)?.contentOrNull) }?.distinct()
        ?.takeIf { it.isNotEmpty() } ?: SearchType.entries
    val first = SearchType.fromKey((info.declared["defaultSearchType"] as? JsonPrimitive)?.contentOrNull)
    return if (first != null && first in declared) listOf(first) + (declared - first) else declared
}

/** 音乐's search: one keyword across the enabled plugins, each searched only when its tab and type are opened. */
class MusicSearchViewModel(private val catalog: MusicCatalog, private val historyStore: SearchHistoryStore) : ViewModel() {
    private val mutable = MutableStateFlow(MusicSearchState())
    val state: StateFlow<MusicSearchState> = mutable.asStateFlow()
    private val jobs = HashMap<Pair<String, SearchType>, Job>()

    init {
        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) { historyStore.read() }
            mutable.update { it.copy(history = history) }
        }
    }

    /** Starts a new search for [raw]; false when it is not a keyword, so nothing changes. */
    fun submit(raw: String): Boolean {
        val keyword = SearchHistory.keyword(raw) ?: return false
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        mutable.update { it.copy(keyword = keyword, results = emptyMap(), history = SearchHistory.record(it.history, keyword)) }
        saveHistory()
        return true
    }

    /** Back to the history, as when the field is cleared. */
    fun clear() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        mutable.update { it.copy(keyword = null, results = emptyMap(), pinned = null) }
    }

    /** Searches [raw] on [platform] and opens it on [type]; the user's chosen source stays as it was. */
    fun searchFor(raw: String, platform: String, type: SearchType) {
        if (submit(raw)) mutable.update { it.copy(pinned = platform to type) }
    }

    /** The user picked a source, so a pinned plugin no longer applies. */
    fun unpin() = mutable.update { it.copy(pinned = null) }

    fun removeHistory(keyword: String) {
        mutable.update { it.copy(history = it.history - keyword) }
        saveHistory()
    }

    fun clearHistory() {
        mutable.update { it.copy(history = emptyList()) }
        saveHistory()
    }

    /** Searches [platform] for [type] if it has not been yet. */
    fun open(platform: String, type: SearchType) {
        val key = platform to type
        val current = mutable.value.results[key]
        if (mutable.value.keyword == null || (current != null && (current.page > 0 || current.loading))) return
        load(key)
    }

    fun loadMore(platform: String, type: SearchType) {
        val current = mutable.value.results[platform to type] ?: return
        if (current.page > 0 && !current.loading && !current.failed && !current.isEnd) load(platform to type)
    }

    fun retry(platform: String, type: SearchType) {
        if (mutable.value.results[platform to type]?.loading != true) load(platform to type)
    }

    private fun load(key: Pair<String, SearchType>) {
        val keyword = mutable.value.keyword ?: return
        val page = (mutable.value.results[key]?.page ?: 0) + 1
        edit(key) { it.copy(loading = true, failed = false) }
        jobs[key] = viewModelScope.launch {
            try {
                val result = catalog.search(key.first, keyword, page, key.second)
                if (mutable.value.keyword != keyword) return@launch
                edit(key) {
                    // Plugins repeat items across pages now and then; each one is shown once. A page that adds nothing
                    // new ends the list: some plugins ignore the page and return the first one again, which would
                    // otherwise ask for page after page with the footer spinning forever.
                    val items = (it.items + result.items).distinctBy { item -> item.id }
                    val added = items.size > it.items.size
                    it.copy(items = items, page = page, loading = false, isEnd = result.isEnd || !added)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                if (mutable.value.keyword == keyword) edit(key) { it.copy(loading = false, failed = true) }
            }
        }
    }

    private fun edit(key: Pair<String, SearchType>, change: (Paged<PluginMedia>) -> Paged<PluginMedia>) =
        mutable.update { it.copy(results = it.results + (key to change(it.results[key] ?: Paged()))) }

    private fun saveHistory() {
        val history = mutable.value.history
        viewModelScope.launch(Dispatchers.IO) { historyStore.write(history) }
    }

    companion object {
        fun factory(app: XmApplication) = viewModelFactory {
            initializer { MusicSearchViewModel(app.musicCatalog, app.musicSearchHistory) }
        }
    }
}
