package com.xuleo.xmplus.md3.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.media.ListenRecord
import com.xuleo.xmplus.md3.core.media.MusicLibrary
import com.xuleo.xmplus.md3.core.media.SongResume
import com.xuleo.xmplus.md3.core.media.UserSheet
import com.xuleo.xmplus.md3.core.model.ContentKind
import com.xuleo.xmplus.md3.core.model.MusicItem
import com.xuleo.xmplus.md3.core.model.MusicSheet
import com.xuleo.xmplus.md3.library.LibraryViewModel
import com.xuleo.xmplus.md3.ui.components.SongRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** How long typing has to rest before the list is searched, so a word is not looked up letter by letter. */
private const val SearchDelayMs = 180L

/** A song could be in 我喜欢 or in one of the user's sheets; the list keeps the first place it was found. */
private class SheetHit(val id: String, val name: String, val cover: String?, val count: Int, val starred: MusicSheet?)

/** What the whole library holds for one keyword: this device's 听书 history, 我喜欢, the user's sheets and 收藏. */
private class Found(
    val episodes: List<ListenRecord>,
    val albums: List<ListenRecord>,
    val songs: List<MusicItem>,
    val sheets: List<SheetHit>,
) {
    val empty get() = episodes.isEmpty() && albums.isEmpty() && songs.isEmpty() && sheets.isEmpty()
}

private fun String.matches(keyword: String) = contains(keyword, ignoreCase = true)

private fun search(keyword: String, likesName: String, history: List<ListenRecord>, liked: List<MusicItem>, sheets: List<UserSheet>, starred: List<MusicSheet>): Found {
    if (keyword.isBlank()) return Found(emptyList(), emptyList(), emptyList(), emptyList())
    val heard = history.filter { it.kind == ContentKind.Episode }
    val episodes = heard.filter { it.trackTitle.matches(keyword) || it.albumTitle.matches(keyword) }
    // One row per album: the newest episode heard from it stands for the album.
    val albums = heard.filter { it.albumTitle.matches(keyword) || it.anchor.matches(keyword) }.distinctBy { it.albumId }
    val songs = (liked + sheets.flatMap { it.songs }).distinctBy { it.key }
        .filter { it.title.matches(keyword) || it.artist.matches(keyword) || it.album.matches(keyword) }
    val own = buildList {
        if (likesName.matches(keyword)) add(SheetHit(MusicLibrary.LikesId, likesName, liked.firstOrNull()?.artwork, liked.size, null))
        sheets.filter { it.name.matches(keyword) }.forEach { add(SheetHit(it.id, it.name, it.coverUrl, it.songs.size, null)) }
        starred.filter { it.title.matches(keyword) }.forEach { add(SheetHit("", it.title, it.coverUrl, it.worksNum ?: 0, it)) }
    }
    return Found(episodes, albums, songs, own)
}

/**
 * 资料库搜索: one field for everything 听过 holds — episodes and albums heard on this device, songs in 我喜欢 and the
 * user's sheets, 我喜欢 itself, 自建 and 收藏 sheets — grouped by kind with counts. Everything is local, so each
 * keystroke searches at once; a tap opens or plays the item.
 */
@Composable
internal fun LibrarySearchScreen(
    viewModel: LibraryViewModel,
    music: MusicLibrary,
    nowPlaying: NowPlaying,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenSheet: (String) -> Unit,
    onOpenStarred: (MusicSheet) -> Unit,
    onPlayEpisode: (String, String, Long) -> Unit,
    onPlaySong: (SongResume) -> Unit,
    onPlay: (MusicItem) -> Unit,
    onPlayNext: (MusicItem) -> Unit,
) {
    val history by viewModel.listenHistory.collectAsStateWithLifecycle()
    val liked by music.liked.collectAsStateWithLifecycle()
    val sheets by music.sheets.collectAsStateWithLifecycle()
    val starred by music.starred.collectAsStateWithLifecycle()
    val query = rememberTextFieldState()
    var keyword by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val list = rememberLazyListState()
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(query) {
        snapshotFlow { query.text.toString().trim() }.collectLatest { text ->
            delay(SearchDelayMs)
            keyword = text
        }
    }
    val likesName = stringResource(R.string.music_favorites)
    val found = remember(keyword, history, liked, sheets, starred) {
        search(keyword, likesName, history.orEmpty(), liked, sheets, starred)
    }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        SearchFieldRow(query, focusRequester, invalid = false, R.string.library_search, onBack, { keyword = "" }) { focus.clearFocus() }
        if (keyword.isEmpty()) return@Column
        if (found.empty) {
            Note(stringResource(R.string.search_empty))
            return@Column
        }
        LazyColumn(
            Modifier.fillMaxSize(), list,
            contentPadding = PaddingValues(bottom = XmLayout.SectionGap),
        ) {
            if (found.episodes.isNotEmpty()) {
                item(key = "episodes-head") { SectionHead(R.string.tab_discover, found.episodes.size) }
                items(found.episodes, key = { "episode:${it.albumId}" }) { record ->
                    ListingRow(
                        record.coverUrl, record.trackTitle.ifEmpty { record.albumTitle }, record.albumTitle, record.anchor,
                        onOpen = { viewModel.resume(record, onPlayEpisode, onPlaySong) },
                        highlighted = nowPlaying.trackId == record.trackId,
                    )
                }
            }
            if (found.albums.isNotEmpty()) {
                item(key = "albums-head") { SectionHead(R.string.music_tab_albums, found.albums.size) }
                items(found.albums, key = { "album:${it.albumId}" }) { record ->
                    ListingRow(record.coverUrl, record.albumTitle, record.anchor, "", onOpen = { onOpenAlbum(record.albumId) }, large = true)
                }
            }
            if (found.songs.isNotEmpty()) {
                item(key = "songs-head") { SectionHead(R.string.music_tab_songs, found.songs.size) }
                items(found.songs, key = { "song:${it.key}" }) { song ->
                    SongRow(song, playing = song.key == nowPlaying.songKey, onPlayNext = { onPlayNext(song) }) { onPlay(song) }
                }
            }
            if (found.sheets.isNotEmpty()) {
                item(key = "sheets-head") { SectionHead(R.string.music_type_sheet, found.sheets.size) }
                items(found.sheets, key = { "sheet:${it.starred?.let { s -> "starred:${s.platform}:${s.id}" } ?: it.id}" }) { hit ->
                    val starredSheet = hit.starred
                    ListingRow(
                        hit.cover, hit.name, "",
                        if (starredSheet == null) pluralStringResource(R.plurals.music_song_count, hit.count, hit.count) else "",
                        music = true,
                        large = true,
                        onOpen = { if (starredSheet == null) onOpenSheet(hit.id) else onOpenStarred(starredSheet) },
                    )
                }
            }
        }
    }
}

/** A group's name and how many it found, quiet above its rows. */
@Composable
private fun SectionHead(@StringRes title: Int, count: Int) {
    Text(
        stringResource(R.string.library_search_section, stringResource(title), count),
        Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding).padding(top = 8.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}
