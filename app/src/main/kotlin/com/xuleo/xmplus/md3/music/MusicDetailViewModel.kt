package com.xuleo.xmplus.md3.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xuleo.xmplus.md3.core.media.MusicPlaySource
import com.xuleo.xmplus.md3.core.model.MusicAlbum
import com.xuleo.xmplus.md3.core.model.MusicArtist
import com.xuleo.xmplus.md3.core.model.MusicItem
import com.xuleo.xmplus.md3.core.model.MusicSheet
import com.xuleo.xmplus.md3.core.model.PluginMedia
import com.xuleo.xmplus.md3.core.plugin.MusicCatalog
import com.xuleo.xmplus.md3.core.plugin.PluginPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** The kinds of plugin page; 榜单 and 歌单 look the same but ask the plugin different methods. */
enum class MusicDetailKind { Sheet, TopList, Album, Artist }

/** A list that loads page by page. [page] is 0 before the first page arrives. */
data class Paged<out T>(
    val items: List<T> = emptyList(),
    val page: Int = 0,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val isEnd: Boolean = false,
)

/** Rebuilds the opened item from what its route kept. Null when the route no longer parses. */
fun musicDetailItem(kind: MusicDetailKind, platform: String, raw: String): PluginMedia? {
    val json = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
    return when (kind) {
        MusicDetailKind.Sheet, MusicDetailKind.TopList -> MusicSheet.from(json, platform)
        MusicDetailKind.Album -> MusicAlbum.from(json, platform)
        MusicDetailKind.Artist -> MusicArtist.from(json, platform)
    }
}

/**
 * A song's artist ([name]) or album to find in the song's plugin before its page can load: a song carries only names,
 * and a plugin opens only the objects it returned itself.
 */
class MusicLookup(val song: MusicItem, val name: String)

/** Rebuilds a lookup from what its route kept: the item the page shows at first and what to find. */
fun musicLookupRoute(kind: MusicDetailKind, platform: String, song: String, name: String): Pair<PluginMedia, MusicLookup>? {
    val json = runCatching { Json.parseToJsonElement(song).jsonObject }.getOrNull() ?: return null
    val item = MusicItem.from(json, platform) ?: return null
    val shown = musicLookupItem(kind, item, name) ?: return null
    return shown to MusicLookup(item, name)
}

/** What the page shows while [MusicLookup] runs: the name, and for an album the song's cover. */
fun musicLookupItem(kind: MusicDetailKind, song: MusicItem, name: String): PluginMedia? = when (kind) {
    MusicDetailKind.Artist -> MusicArtist(song.platform, "", name, null, null, "", null, JsonObject(emptyMap()))
    MusicDetailKind.Album -> MusicAlbum(song.platform, "", name, song.artist, song.artwork, "", "", null, JsonObject(emptyMap()))
    else -> null
}

/**
 * A sheet, ranking, album or artist page. The header shows the item as opened and takes what the plugin adds on page
 * 1. Every page is asked with the item as opened, so later pages never depend on what an earlier one returned.
 * With a [lookup] the page opens at once on the name and finds the plugin's own object first; the lists wait for it.
 */
class MusicDetailViewModel(
    private val catalog: MusicCatalog,
    val kind: MusicDetailKind,
    initial: PluginMedia,
    lookup: MusicLookup? = null,
) : ViewModel() {
    private val mutableItem = MutableStateFlow(initial)
    val item: StateFlow<PluginMedia> = mutableItem.asStateFlow()

    /** The item every call is made with: the one opened, or the one the lookup found. */
    private var target: PluginMedia = initial
    private val ready = CompletableDeferred<Boolean>()

    private val mutableNotFound = MutableStateFlow(false)
    /** The lookup found nothing that matches, so the page should give way to a search. */
    val notFound: StateFlow<Boolean> = mutableNotFound.asStateFlow()

    init {
        if (lookup == null) {
            ready.complete(true)
        } else {
            viewModelScope.launch {
                val found = try {
                    withTimeoutOrNull(LookupTimeoutMs) {
                        when (kind) {
                            MusicDetailKind.Artist -> catalog.findArtist(lookup.song, lookup.name)
                            MusicDetailKind.Album -> catalog.findAlbum(lookup.song)
                            else -> null
                        }
                    }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    null
                }
                if (found != null) {
                    target = found
                    mutableItem.value = found
                }
                ready.complete(found != null)
                if (found == null) mutableNotFound.value = true
            }
        }
    }

    private val mutableSongs = MutableStateFlow(Paged<MusicItem>())
    val songs: StateFlow<Paged<MusicItem>> = mutableSongs.asStateFlow()

    /** 专辑 of an artist; unused on other pages. */
    private val mutableAlbums = MutableStateFlow(Paged<MusicAlbum>())
    val albums: StateFlow<Paged<MusicAlbum>> = mutableAlbums.asStateFlow()

    fun start() = startList(mutableSongs, ::songPage)
    fun loadMoreSongs() = loadMore(mutableSongs, ::songPage)
    fun retrySongs() = retry(mutableSongs, ::songPage)

    /** Reads the pages after those loaded so far, for a queue played from this list; it pages on its own. */
    fun queuePages(): suspend () -> List<MusicItem>? {
        var page = mutableSongs.value.page
        var ended = mutableSongs.value.isEnd
        return {
            if (ended) {
                emptyList()
            } else {
                val result = songPage(page + 1)
                page += 1
                ended = result.isEnd || result.items.isEmpty()
                result.items
            }
        }
    }

    /** The list as 最近播放 keeps it, so a song played from here can bring the list back. */
    fun playSource(): MusicPlaySource = MusicPlaySource.of(
        when (kind) {
            MusicDetailKind.Sheet -> MusicPlaySource.Type.PluginSheet
            MusicDetailKind.TopList -> MusicPlaySource.Type.TopList
            MusicDetailKind.Album -> MusicPlaySource.Type.Album
            MusicDetailKind.Artist -> MusicPlaySource.Type.Artist
        },
        target,
    )

    fun startAlbums() = startList(mutableAlbums, ::albumPage)
    fun loadMoreAlbums() = loadMore(mutableAlbums, ::albumPage)
    fun retryAlbums() = retry(mutableAlbums, ::albumPage)

    // Not found: the lists keep their skeleton while the page gives way to the search.
    private suspend fun songPage(page: Int): PluginPage<MusicItem> {
        if (!ready.await()) awaitCancellation()
        val item = target
        return when (kind) {
            MusicDetailKind.Sheet -> catalog.sheetDetail(item as MusicSheet, page).also { if (page <= 1) mutableItem.value = it.item }.songs
            MusicDetailKind.TopList -> catalog.topListDetail(item as MusicSheet, page).also { if (page <= 1) mutableItem.value = it.item }.songs
            MusicDetailKind.Album -> catalog.albumDetail(item as MusicAlbum, page).also { if (page <= 1) mutableItem.value = it.item }.songs
            MusicDetailKind.Artist -> catalog.artistSongs(item as MusicArtist, page)
        }
    }

    private suspend fun albumPage(page: Int): PluginPage<MusicAlbum> {
        if (!ready.await()) awaitCancellation()
        return if (kind == MusicDetailKind.Artist) catalog.artistAlbums(target as MusicArtist, page) else PluginPage.End
    }

    private fun <T> startList(list: MutableStateFlow<Paged<T>>, fetch: suspend (Int) -> PluginPage<T>) {
        val state = list.value
        if (state.page == 0 && !state.loading && !state.failed) load(list, fetch)
    }

    private fun <T> loadMore(list: MutableStateFlow<Paged<T>>, fetch: suspend (Int) -> PluginPage<T>) {
        val state = list.value
        if (state.page > 0 && !state.loading && !state.failed && !state.isEnd) load(list, fetch)
    }

    private fun <T> retry(list: MutableStateFlow<Paged<T>>, fetch: suspend (Int) -> PluginPage<T>) {
        if (!list.value.loading) load(list, fetch)
    }

    private fun <T> load(list: MutableStateFlow<Paged<T>>, fetch: suspend (Int) -> PluginPage<T>) {
        val page = list.value.page + 1
        list.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            try {
                val result = fetch(page)
                // Each item is shown once, and a page that adds nothing new ends the list even when the plugin says
                // there is more: some plugins ignore the page and send the first one again, which would otherwise
                // repeat the same songs (the playing one among them) for as long as the list is scrolled.
                list.update {
                    val items = (it.items + result.items).distinctBy { item -> (item as? PluginMedia)?.let { media -> media.platform to media.id } ?: item }
                    val added = items.size > it.items.size
                    it.copy(items = items, page = page, loading = false, isEnd = result.isEnd || !added)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                list.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    companion object {
        /** A search that finds nothing within this long gives way to the search page. */
        private const val LookupTimeoutMs = 10_000L

        fun factory(catalog: MusicCatalog, kind: MusicDetailKind, item: PluginMedia, lookup: MusicLookup? = null) =
            viewModelFactory { initializer { MusicDetailViewModel(catalog, kind, item, lookup) } }
    }
}

/** What a route keeps of an item: the plugin's object as text. */
fun PluginMedia.routeRaw(): String = raw.toString()
