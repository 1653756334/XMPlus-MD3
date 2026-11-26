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
 * A sheet, ranking, album or artist page. The header shows the item as opened and takes what the plugin adds on page
 * 1. Every page is asked with the item as opened, so later pages never depend on what an earlier one returned.
 */
class MusicDetailViewModel(private val catalog: MusicCatalog, val kind: MusicDetailKind, private val initial: PluginMedia) : ViewModel() {
    private val mutableItem = MutableStateFlow(initial)
    val item: StateFlow<PluginMedia> = mutableItem.asStateFlow()

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
        initial,
    )

    fun startAlbums() = startList(mutableAlbums, ::albumPage)
    fun loadMoreAlbums() = loadMore(mutableAlbums, ::albumPage)
    fun retryAlbums() = retry(mutableAlbums, ::albumPage)

    private suspend fun songPage(page: Int): PluginPage<MusicItem> = when (kind) {
        MusicDetailKind.Sheet -> catalog.sheetDetail(initial as MusicSheet, page).also { if (page <= 1) mutableItem.value = it.item }.songs
        MusicDetailKind.TopList -> catalog.topListDetail(initial as MusicSheet, page).also { if (page <= 1) mutableItem.value = it.item }.songs
        MusicDetailKind.Album -> catalog.albumDetail(initial as MusicAlbum, page).also { if (page <= 1) mutableItem.value = it.item }.songs
        MusicDetailKind.Artist -> catalog.artistSongs(initial as MusicArtist, page).also { if (page <= 1) fillArtist() }
    }

    /**
     * An artist opened from a song brings only a name, so the page looks them up once to show the avatar and numbers.
     * What the plugin gives never replaces the id the page is already reading.
     */
    private suspend fun fillArtist() {
        val artist = mutableItem.value as? MusicArtist ?: return
        if (artist.avatar != null) return
        val found = try {
            catalog.artistInfo(artist)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            null
        } ?: return
        mutableItem.value = artist.copy(
            avatar = found.avatar ?: artist.avatar,
            fans = found.fans ?: artist.fans,
            description = found.description.ifBlank { artist.description },
            worksNum = found.worksNum ?: artist.worksNum,
        )
    }

    private suspend fun albumPage(page: Int): PluginPage<MusicAlbum> =
        if (kind == MusicDetailKind.Artist) catalog.artistAlbums(initial as MusicArtist, page) else PluginPage.End

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
                // An empty page ends the list even when the plugin says there is more, so scrolling never loops.
                list.update { it.copy(items = it.items + result.items, page = page, loading = false, isEnd = result.isEnd || result.items.isEmpty()) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                list.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    companion object {
        fun factory(catalog: MusicCatalog, kind: MusicDetailKind, item: PluginMedia) =
            viewModelFactory { initializer { MusicDetailViewModel(catalog, kind, item) } }
    }
}

/** What a route keeps of an item: the plugin's object as text. */
fun PluginMedia.routeRaw(): String = raw.toString()
