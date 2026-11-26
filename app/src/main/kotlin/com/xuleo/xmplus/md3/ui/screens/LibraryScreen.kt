package com.xuleo.xmplus.md3.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.activity.compose.BackHandler
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.xuleo.xmplus.md3.ui.components.ReorderHandle
import com.xuleo.xmplus.md3.ui.components.ReorderState
import com.xuleo.xmplus.md3.ui.components.rememberReorderState
import com.xuleo.xmplus.md3.ui.components.reorderItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.xuleo.xmplus.md3.core.model.MusicSheet
import com.xuleo.xmplus.md3.library.LibraryViewModel
import com.xuleo.xmplus.md3.ui.Destination
import com.xuleo.xmplus.md3.ui.components.Cover
import com.xuleo.xmplus.md3.ui.components.pressable
import com.xuleo.xmplus.md3.ui.components.ModeSwitch
import com.xuleo.xmplus.md3.ui.components.MusicCover
import com.xuleo.xmplus.md3.ui.components.SheetNameDialog
import com.xuleo.xmplus.md3.ui.icons.XmIcons

/** How many items 继续听 shows. */
private const val ContinueCount = 10
private const val GridColumns = 4

/** What is playing now, so 继续听 can mark it: a song's key or an episode's track id. */
data class NowPlaying(val songKey: String? = null, val trackId: String? = null) {
    fun matches(record: ListenRecord): Boolean = when (record.kind) {
        ContentKind.Song -> songKey != null && record.albumId == songKey
        ContentKind.Episode -> trackId != null && record.trackId == trackId
    }
}

/** Where the 听过 overview leads. Null hooks hide what they would open until it exists. */
class LibraryLinks(
    val onOpenAlbum: (String) -> Unit,
    val onPlay: (String, String, Long) -> Unit,
    val onPlaySong: (SongResume) -> Unit,
    val onOpenRecent: () -> Unit,
    val onOpenSection: (LibrarySection) -> Unit,
    val onOpenDownloads: () -> Unit,
    val onOpenStarred: (MusicSheet) -> Unit,
    /** 我喜欢 ([MusicLibrary.LikesId]) and 自建 sheets. */
    val onOpenSheet: ((String) -> Unit)? = null,
    val onSearch: (() -> Unit)? = null,
    val onImportSheet: (() -> Unit)? = null,
)

private enum class SheetGroup { Mine, Starred }

private class Tile(@StringRes val label: Int, val icon: ImageVector, val count: Int?, val onClick: () -> Unit)

/**
 * 听过, after QQ 音乐's 我的: 继续听 with the latest of both kinds, a grid of shortcuts to each collection, and the
 * user's own and starred sheets. Account collections are left out when no account is signed in.
 */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    music: MusicLibrary?,
    signedIn: Boolean,
    downloadCount: Int?,
    nowPlaying: NowPlaying,
    links: LibraryLinks,
) {
    val history by viewModel.listenHistory.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val liked = music?.liked?.collectAsStateWithLifecycle()?.value.orEmpty()
    val sheets = music?.sheets?.collectAsStateWithLifecycle()?.value.orEmpty()
    val starred = music?.starred?.collectAsStateWithLifecycle()?.value.orEmpty()
    var group by rememberSaveable { mutableStateOf(SheetGroup.Mine) }
    var creating by rememberSaveable { mutableStateOf(false) }
    // 管理歌单: check boxes and drag handles on the shown group, in place.
    var managing by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val animated = rememberAnimationsEnabled()
    val haptics = LocalHapticFeedback.current
    val sheetOrder = rememberReorderState<UserSheet>(list, { sheetKey(it) }) { from, to -> music?.moveSheet(from, to) }
    val starredOrder = rememberReorderState<MusicSheet>(list, { starredKey(it) }) { from, to -> music?.moveStarred(from, to) }
    val keys = if (group == SheetGroup.Mine) sheets.map(::sheetKey) else starred.map(::starredKey)
    fun endManage() {
        managing = false
        selected = emptySet()
    }
    fun toggle(key: String) {
        selected = if (key in selected) selected - key else selected + key
    }
    fun startManage(key: String) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        managing = true
        selected = setOf(key)
    }
    BackHandler(enabled = managing, onBack = ::endManage)
    LaunchedEffect(Unit) { viewModel.loadBookmarks() }
    val recent = history.orEmpty().take(ContinueCount)
    val tiles = buildList {
        val openSheet = links.onOpenSheet
        if (music != null && openSheet != null) add(Tile(R.string.music_favorites, XmIcons.Outlined.FavoriteBorder, liked.size) { openSheet(MusicLibrary.LikesId) })
        if (signedIn) {
            add(Tile(R.string.listen_tab_updates, XmIcons.Outlined.AutoAwesome, null) { links.onOpenSection(LibrarySection.Updates) })
            add(Tile(R.string.listen_tab_subscriptions, XmIcons.Outlined.LibraryAdd, null) { links.onOpenSection(LibrarySection.Subscriptions) })
        }
        add(Tile(R.string.listen_tab_bookmarks, XmIcons.Outlined.BookmarkBorder, bookmarks?.size) { links.onOpenSection(LibrarySection.Bookmarks) })
        if (signedIn) {
            add(Tile(R.string.library_favorites, XmIcons.Outlined.Star, null) { links.onOpenSection(LibrarySection.Favorites) })
            add(Tile(R.string.library_following, XmIcons.Outlined.Person, null) { links.onOpenSection(LibrarySection.Anchors) })
            add(Tile(R.string.listen_tab_cloud, XmIcons.Outlined.Sync, null) { links.onOpenSection(LibrarySection.Cloud) })
        }
        add(Tile(R.string.tab_downloads, XmIcons.Outlined.Download, downloadCount, links.onOpenDownloads))
    }
    TabScaffold(
        Destination.Library.label,
        headline = if (managing) stringResource(R.string.history_selected, selected.size) else null,
        navigation = {
            if (managing) IconButton(onClick = ::endManage) { Icon(XmIcons.Outlined.Close, stringResource(R.string.action_cancel)) }
        },
        actions = {
            if (managing) {
                val all = keys.isNotEmpty() && keys.all { it in selected }
                IconButton(onClick = { selected = if (all) emptySet() else keys.toSet() }, enabled = keys.isNotEmpty()) {
                    Icon(
                        XmIcons.Filled.DoneAll, stringResource(R.string.history_select_all),
                        tint = if (all) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
                IconButton(onClick = { confirmDelete = true }, enabled = selected.isNotEmpty()) {
                    Icon(XmIcons.Outlined.DeleteOutline, stringResource(R.string.downloads_delete))
                }
            } else {
                links.onSearch?.let { search ->
                    IconButton(onClick = search) { Icon(XmIcons.Outlined.Search, stringResource(R.string.library_search)) }
                }
                if (music != null) OverflowMenu { managing = true }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), list, contentPadding = PaddingValues(bottom = XmLayout.SectionGap)) {
            if (recent.isNotEmpty() && !managing) {
                item(key = "continue") {
                    ContinueRow(recent, nowPlaying, links.onOpenRecent) { record ->
                        viewModel.resume(record, links.onPlay, links.onPlaySong)
                    }
                }
            }
            if (!managing) item(key = "tiles") { TileGrid(tiles) }
            if (music != null) {
                item(key = "sheetsHeader") {
                    SheetsHeader(
                        group, sheets.size, starred.size, { group = it; selected = emptySet() },
                        onCreate = { creating = true }, onImport = links.onImportSheet,
                    )
                }
                when (group) {
                    SheetGroup.Mine -> if (sheets.isEmpty()) {
                        item(key = "sheetsEmpty") { EmptySheets { creating = true } }
                    } else {
                        items(if (managing) sheetOrder.items(sheets) else sheets, key = ::sheetKey) { sheet ->
                            val key = sheetKey(sheet)
                            SheetListRow(
                                sheet.coverUrl, sheet.name,
                                pluralStringResource(R.plurals.music_song_count, sheet.songs.size, sheet.songs.size),
                                modifier = if (managing) Modifier.reorderItem(this, sheetOrder, sheet, animated) else Modifier,
                                onClick = if (managing) ({ toggle(key) }) else links.onOpenSheet?.let { open -> { open(sheet.id) } },
                                onLongClick = if (managing) null else ({ startManage(key) }),
                                trailing = if (managing) ({ ManageTrailing(key in selected, sheetOrder, sheet) }) else null,
                            )
                        }
                    }
                    SheetGroup.Starred -> if (starred.isEmpty()) {
                        item(key = "starredEmpty") { Note(stringResource(R.string.library_starred_empty)) }
                    } else {
                        items(if (managing) starredOrder.items(starred) else starred, key = ::starredKey) { sheet ->
                            val key = starredKey(sheet)
                            SheetListRow(
                                sheet.coverUrl, sheet.title,
                                listOfNotNull(
                                    sheet.artist.takeIf { it.isNotBlank() },
                                    sheet.worksNum?.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.music_song_count, it, it) },
                                ).joinToString(" · ").ifEmpty { null },
                                modifier = if (managing) Modifier.reorderItem(this, starredOrder, sheet, animated) else Modifier,
                                onClick = if (managing) ({ toggle(key) }) else ({ links.onOpenStarred(sheet) }),
                                onLongClick = if (managing) null else ({ startManage(key) }),
                                trailing = if (managing) ({ ManageTrailing(key in selected, starredOrder, sheet) }) else null,
                            )
                        }
                    }
                }
            }
        }
    }
    if (creating && music != null) {
        NewSheetDialog(onDismiss = { creating = false }) { name ->
            creating = false
            music.createSheet(name)
        }
    }
    if (confirmDelete && music != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.music_sheets_delete_title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    if (group == SheetGroup.Mine) music.deleteSheets(sheets.filter { sheetKey(it) in selected }.map { it.id })
                    else starred.filter { starredKey(it) in selected }.forEach(music::unstar)
                    endManage()
                }) { Text(stringResource(R.string.downloads_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private fun sheetKey(sheet: UserSheet) = "sheet:${sheet.id}"

private fun starredKey(sheet: MusicSheet) = "starred:${sheet.platform}:${sheet.id}"

@Composable
private fun <T> ManageTrailing(checked: Boolean, order: ReorderState<T>, item: T) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null)
        ReorderHandle(order, item)
    }
}

@Composable
private fun OverflowMenu(onManageSheets: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(XmIcons.Outlined.MoreVert, stringResource(R.string.search_more)) }
        com.xuleo.xmplus.md3.ui.components.XmMenu(open, { open = false }) {
            com.xuleo.xmplus.md3.ui.components.XmMenuItem(
                stringResource(R.string.library_manage_sheets), { open = false; onManageSheets() }, 0, 1,
                icon = XmIcons.Outlined.SwapVert,
            )
        }
    }
}

/** 继续听: the latest items of both kinds; its title opens 最近播放. */
@Composable
private fun ContinueRow(records: List<ListenRecord>, nowPlaying: NowPlaying, onOpenRecent: () -> Unit, onResume: (ListenRecord) -> Unit) {
    Column {
        Row(
            Modifier.pressable(onOpenRecent).heightIn(min = 48.dp)
                .padding(start = XmLayout.PagePadding, end = XmLayout.PagePadding / 2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.library_continue), style = MaterialTheme.typography.titleMedium)
            Icon(XmIcons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = XmLayout.PagePadding),
            horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
        ) {
            items(records, key = { "${it.kind.key}:${it.albumId}" }) { record ->
                ContinueCard(record, nowPlaying.matches(record)) { onResume(record) }
            }
        }
    }
}

@Composable
private fun ContinueCard(record: ListenRecord, playing: Boolean, onClick: () -> Unit) {
    val song = record.kind == ContentKind.Song
    Column(Modifier.width(112.dp).pressable(onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium)) {
            if (song) MusicCover(record.coverUrl, Modifier.matchParentSize()) else Cover(record.coverUrl, Modifier.matchParentSize())
            if (!song && record.durationMs > 0) {
                val progress = (record.positionMs.toFloat() / record.durationMs).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp).heightIn(max = 3.dp),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    trackColor = Color.White.copy(alpha = 0.4f),
                )
            }
            if (playing) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(XmIcons.Filled.Equalizer, contentDescription = null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        Text(
            if (song) record.trackTitle else record.trackTitle.ifEmpty { record.albumTitle },
            style = MaterialTheme.typography.bodyMedium, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
            color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Shortcuts in rows of four; tiles that are left out close up rather than leave a gap. */
@Composable
private fun TileGrid(tiles: List<Tile>) {
    Column(Modifier.padding(horizontal = XmLayout.PagePadding / 2, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        tiles.chunked(GridColumns).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { tile -> ShortcutTile(tile, Modifier.weight(1f)) }
                repeat(GridColumns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ShortcutTile(tile: Tile, modifier: Modifier = Modifier) {
    Column(
        modifier.pressable(tile.onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(tile.icon, contentDescription = null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(tile.label), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            tile.count?.takeIf { it > 0 }?.let {
                Text("$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** 自建 n · 收藏 n, switched in place, with 新建 and 导入 at the end. */
@Composable
private fun SheetsHeader(
    group: SheetGroup,
    mine: Int,
    starred: Int,
    onGroup: (SheetGroup) -> Unit,
    onCreate: () -> Unit,
    onImport: (() -> Unit)?,
) {
    Row(Modifier.fillMaxWidth().padding(top = XmLayout.ItemGap, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        ModeSwitch(
            SheetGroup.entries, group,
            label = { if (it == SheetGroup.Mine) stringResource(R.string.library_sheets_mine, mine) else stringResource(R.string.library_sheets_starred, starred) },
            onSelect = onGroup, modifier = Modifier.weight(1f), textStyle = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = onCreate) { Icon(XmIcons.Outlined.Add, stringResource(R.string.library_sheet_new)) }
        onImport?.let { IconButton(onClick = it) { Icon(XmIcons.Outlined.Link, stringResource(R.string.library_sheet_import)) } }
    }
}

@Composable
private fun EmptySheets(onCreate: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(XmLayout.PagePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
    ) {
        Text(stringResource(R.string.library_sheets_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onCreate) { Text(stringResource(R.string.library_sheet_new)) }
    }
}

/** A sheet on the overview: cover, name and one quiet line; while managing, a check box and a drag handle end it. */
@Composable
private fun SheetListRow(
    cover: String?,
    name: String,
    meta: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val click = when {
        onClick == null -> Modifier
        onLongClick != null -> Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
        else -> Modifier.clickable(onClick = onClick)
    }
    Row(
        modifier.fillMaxWidth().then(click).heightIn(min = 64.dp)
            .padding(start = XmLayout.PagePadding, end = if (trailing != null) 4.dp else XmLayout.PagePadding)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
    ) {
        MusicCover(cover, Modifier.size(56.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (meta != null) {
                Text(
                    meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/** 新建歌单: one name field. */
@Composable
internal fun NewSheetDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) =
    SheetNameDialog(stringResource(R.string.library_sheet_new), stringResource(R.string.library_sheet_create), onDismiss, onConfirm = onCreate)
