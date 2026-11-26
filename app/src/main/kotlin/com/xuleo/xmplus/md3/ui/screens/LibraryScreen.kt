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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.Dp
import com.xuleo.xmplus.md3.core.model.MusicItem
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
import com.xuleo.xmplus.md3.ui.components.PlayingBars
import com.xuleo.xmplus.md3.ui.components.ReorderHandle
import com.xuleo.xmplus.md3.ui.components.ReorderState
import com.xuleo.xmplus.md3.ui.components.rememberReorderState
import com.xuleo.xmplus.md3.ui.components.reorderItem
import com.xuleo.xmplus.md3.ui.components.CardHeading
import com.xuleo.xmplus.md3.ui.components.CardTopPadding
import com.xuleo.xmplus.md3.ui.components.CardPartPadding
import com.xuleo.xmplus.md3.ui.components.SectionCard
import com.xuleo.xmplus.md3.ui.components.SectionHeader
import com.xuleo.xmplus.md3.ui.components.sectionCardPart
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.xuleo.xmplus.md3.ui.components.SelectionToolbar
import com.xuleo.xmplus.md3.ui.components.SelectionToolbarSpace
import com.xuleo.xmplus.md3.ui.components.ToolbarAction
import com.xuleo.xmplus.md3.ui.icons.XmIcons

/** How many items 继续听 shows. */
private const val ContinueCount = 10
private const val GridColumns = 2

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
    val onOpenStarred: (MusicSheet) -> Unit,
    /** 我喜欢 ([MusicLibrary.LikesId]) and 自建 sheets. */
    val onOpenSheet: ((String) -> Unit)? = null,
    /** Plays 我喜欢 from the top. */
    val onPlayLikes: ((List<MusicItem>) -> Unit)? = null,
    val onSearch: (() -> Unit)? = null,
    val onImportSheet: (() -> Unit)? = null,
    /** Opens account sign-in, as 我的 does. */
    val onLogin: (() -> Unit)? = null,
)

private enum class SheetGroup { Mine, Starred }

private class Tile(@StringRes val label: Int, val icon: ImageVector, val count: Int?, val onClick: () -> Unit)

/**
 * 听过, after QQ 音乐's 我的 and 喜马拉雅's 我听: 继续听 with the latest of both kinds, then a 听书 section (the account
 * collections, or 书签 and a sign-in line when signed out) and a 音乐 section (我喜欢, then the user's own and starred
 * sheets).
 */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    music: MusicLibrary?,
    signedIn: Boolean,
    nowPlaying: NowPlaying,
    links: LibraryLinks,
) {
    val history by viewModel.listenHistory.collectAsStateWithLifecycle()
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
    val recent = history.orEmpty().take(ContinueCount)
    val bookmarksTile = Tile(R.string.listen_tab_bookmarks, XmIcons.Outlined.BookmarkBorder, null) {
        links.onOpenSection(LibrarySection.Bookmarks)
    }
    val tiles = listOf(
        Tile(R.string.listen_tab_subscriptions, XmIcons.Outlined.LibraryAdd, null) { links.onOpenSection(LibrarySection.Subscriptions) },
        Tile(R.string.library_favorites, XmIcons.Outlined.Star, null) { links.onOpenSection(LibrarySection.Favorites) },
        Tile(R.string.listen_tab_updates, XmIcons.Outlined.AutoAwesome, null) { links.onOpenSection(LibrarySection.Updates) },
        bookmarksTile,
        Tile(R.string.library_following, XmIcons.Outlined.Person, null) { links.onOpenSection(LibrarySection.Anchors) },
        Tile(R.string.listen_tab_cloud, XmIcons.Outlined.Sync, null) { links.onOpenSection(LibrarySection.Cloud) },
    )
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
            } else {
                links.onSearch?.let { search ->
                    IconButton(onClick = search) { Icon(XmIcons.Outlined.Search, stringResource(R.string.library_search)) }
                }
                if (music != null) OverflowMenu { managing = true }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            Modifier.fillMaxSize(), list,
            contentPadding = PaddingValues(top = 4.dp, bottom = if (managing) SelectionToolbarSpace else XmLayout.SectionGap),
        ) {
            if (recent.isNotEmpty() && !managing) {
                item(key = "continue") {
                    ContinueRow(recent, nowPlaying, links.onOpenRecent) { record ->
                        viewModel.resume(record, links.onPlay, links.onPlaySong)
                    }
                }
                item(key = "continueGap") { Spacer(Modifier.height(XmLayout.ItemGap)) }
            }
            if (!managing) {
                item(key = "books") {
                    // The heading sits as far from the top as 全站热播榜 does on 听书 home.
                    SectionCard(contentPadding = PaddingValues(top = CardTopPadding, bottom = 10.dp)) {
                        CardHeading(stringResource(R.string.tab_discover))
                        if (signedIn) TileGrid(tiles) else SignedOutBooks(bookmarksTile, links.onLogin)
                    }
                }
            }
            if (music != null) {
                // The 音乐 card is drawn row by row so its sheets can still be dragged into order.
                if (!managing) {
                    item(key = "musicGap") { Spacer(Modifier.height(XmLayout.ItemGap)) }
                    item(key = "musicHeading") {
                        CardHeading(stringResource(R.string.tab_music), Modifier.sectionCardPart(first = true, last = false, padding = CardTopPadding))
                    }
                    item(key = "likes") {
                        LikesRow(
                            liked,
                            onClick = links.onOpenSheet?.let { open -> { open(MusicLibrary.LikesId) } },
                            onPlay = links.onPlayLikes?.takeIf { liked.isNotEmpty() }?.let { play -> { play(liked) } },
                            modifier = Modifier.sectionCardPart(first = false, last = false),
                        )
                    }
                }
                item(key = "sheetsHeader") {
                    SheetsHeader(
                        group, sheets.size, starred.size, { group = it; selected = emptySet() },
                        onCreate = { creating = true }, onImport = links.onImportSheet,
                        modifier = Modifier.sectionCardPart(first = managing, last = false, padding = XmLayout.ItemGap),
                    )
                }
                when (group) {
                    SheetGroup.Mine -> if (sheets.isEmpty()) {
                        item(key = "sheetsEmpty") { EmptySheets(Modifier.sectionCardPart(first = false, last = false)) { creating = true } }
                    } else {
                        items(if (managing) sheetOrder.items(sheets) else sheets, key = ::sheetKey) { sheet ->
                            val key = sheetKey(sheet)
                            SheetListRow(
                                sheet.coverUrl, sheet.name,
                                pluralStringResource(R.plurals.music_song_count, sheet.songs.size, sheet.songs.size),
                                modifier = Modifier.padding(horizontal = XmLayout.PagePadding)
                                    .then(if (managing) Modifier.reorderItem(this, sheetOrder, sheet, animated) else Modifier)
                                    .sectionCardPart(first = false, last = false, margin = 0.dp),
                                onClick = if (managing) ({ toggle(key) }) else links.onOpenSheet?.let { open -> { open(sheet.id) } },
                                onLongClick = if (managing) null else ({ startManage(key) }),
                                trailing = if (managing) ({ ManageTrailing(key in selected, sheetOrder, sheet) }) else null,
                            )
                        }
                    }
                    SheetGroup.Starred -> if (starred.isEmpty()) {
                        item(key = "starredEmpty") {
                            QuietLine(stringResource(R.string.library_starred_empty), Modifier.sectionCardPart(first = false, last = false))
                        }
                    } else {
                        items(if (managing) starredOrder.items(starred) else starred, key = ::starredKey) { sheet ->
                            val key = starredKey(sheet)
                            SheetListRow(
                                sheet.coverUrl, sheet.title,
                                listOfNotNull(
                                    sheet.artist.takeIf { it.isNotBlank() },
                                    sheet.worksNum?.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.music_song_count, it, it) },
                                ).joinToString(" · ").ifEmpty { null },
                                modifier = Modifier.padding(horizontal = XmLayout.PagePadding)
                                    .then(if (managing) Modifier.reorderItem(this, starredOrder, sheet, animated) else Modifier)
                                    .sectionCardPart(first = false, last = false, margin = 0.dp),
                                onClick = if (managing) ({ toggle(key) }) else ({ links.onOpenStarred(sheet) }),
                                onLongClick = if (managing) null else ({ startManage(key) }),
                                trailing = if (managing) ({ ManageTrailing(key in selected, starredOrder, sheet) }) else null,
                            )
                        }
                    }
                }
                // The card's foot: as tall as its corner, so the rounding is never squeezed.
                item(key = "musicEnd") { Spacer(Modifier.sectionCardPart(first = false, last = true, padding = 16.dp)) }
            }
        }
        val starredGroup = group == SheetGroup.Starred
        SelectionToolbar(managing) {
            ToolbarAction(
                if (starredGroup) R.string.music_unstar else R.string.downloads_delete,
                if (starredGroup) XmIcons.Filled.Star else XmIcons.Outlined.DeleteOutline,
                selected.isNotEmpty(),
            ) { confirmDelete = true }
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
            title = { Text(stringResource(if (group == SheetGroup.Mine) R.string.music_sheets_delete_title else R.string.music_sheets_unstar_title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    if (group == SheetGroup.Mine) music.deleteSheets(sheets.filter { sheetKey(it) in selected }.map { it.id })
                    else starred.filter { starredKey(it) in selected }.forEach(music::unstar)
                    endManage()
                }) { Text(stringResource(if (group == SheetGroup.Mine) R.string.downloads_delete else R.string.music_unstar)) }
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
    // As the shelves on 听书 home: a heading with 全部 at its end, and the covers below.
    Column(verticalArrangement = Arrangement.spacedBy(XmLayout.ItemGap)) {
        SectionHeader(stringResource(R.string.library_continue), actionLabel = stringResource(R.string.library_continue_all), onAction = onOpenRecent)
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
                // Flush along the foot of the cover, as 喜马拉雅 shows how far an episode got.
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp),
                    strokeCap = StrokeCap.Butt,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    trackColor = Color.Black.copy(alpha = 0.35f),
                )
            }
            CoverKindBadge(record.kind, Modifier.align(Alignment.TopStart).padding(BadgeInset))
            if (playing) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    PlayingBars(Modifier.size(12.dp), MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        Text(
            if (song) record.trackTitle else record.trackTitle.ifEmpty { record.albumTitle },
            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

private val BadgeSize = 18.dp
private val BadgeInset = 6.dp

/** Space each side of the 听书 grid inside its card; with the columns' own margins the icons line up with the heading. */
private val TileGridInset = 4.dp

/** The round mark of a shortcut, and the least height of its row. */
private val TileIconSize = 40.dp
private val TileMinHeight = 56.dp

/** The kind on a cover's corner: a white mark on a soft dark disc, readable on any picture. */
@Composable
private fun CoverKindBadge(kind: ContentKind, modifier: Modifier = Modifier) {
    val song = kind == ContentKind.Song
    Box(modifier.size(20.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
        Icon(
            if (song) XmIcons.Filled.MusicNote else XmIcons.Filled.Headphones,
            contentDescription = stringResource(if (song) R.string.tab_music else R.string.tab_discover),
            Modifier.size(12.dp), tint = Color.White,
        )
    }
}

/** A small round mark on a cover that tells 听书 from 音乐. */
@Composable
internal fun KindBadge(kind: ContentKind, modifier: Modifier = Modifier, size: Dp = BadgeSize) {
    val song = kind == ContentKind.Song
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (song) XmIcons.Filled.MusicNote else XmIcons.Filled.Headphones,
            contentDescription = stringResource(if (song) R.string.tab_music else R.string.tab_discover),
            Modifier.size(size * 0.65f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Signed out, 听书 keeps 书签 and one line that leads to sign-in, so the section never shows gaps. */
@Composable
private fun SignedOutBooks(bookmarks: Tile, onLogin: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = TileGridInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // As wide as one column of the signed-in grid, so 书签 sits where it would there.
        ShortcutTile(bookmarks, Modifier.weight(1f))
        Row(
            Modifier.weight(1f).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
        ) {
            Text(
                stringResource(R.string.library_sign_in_hint), Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            onLogin?.let { FilledTonalButton(onClick = it, contentPadding = PaddingValues(horizontal = 16.dp)) { Text(stringResource(R.string.library_sign_in)) } }
        }
    }
}

/** 我喜欢 as a row of its own, as QQ 音乐 has it: mosaic, name and count, and a play button for the whole list. */
@Composable
private fun LikesRow(songs: List<MusicItem>, onClick: (() -> Unit)?, onPlay: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).heightIn(min = 64.dp)
            .padding(start = XmLayout.PagePadding, end = if (onPlay != null) 4.dp else XmLayout.PagePadding)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
    ) {
        if (songs.isEmpty()) {
            Box(
                Modifier.size(56.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(XmIcons.Filled.Favorite, contentDescription = null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        } else {
            SheetMosaic(songs, Modifier.size(56.dp), MaterialTheme.shapes.medium)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.music_favorites), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                pluralStringResource(R.plurals.music_song_count, songs.size, songs.size),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
        }
        onPlay?.let {
            IconButton(onClick = it) {
                Icon(XmIcons.Filled.PlayArrow, stringResource(R.string.music_play_all), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** One quiet line where a list is empty. */
@Composable
private fun QuietLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding, vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Shortcuts in rows of two, each a mark and its name; tiles that are left out close up rather than leave a gap. */
@Composable
private fun TileGrid(tiles: List<Tile>) {
    Column(Modifier.padding(horizontal = TileGridInset)) {
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
    Row(
        modifier.pressable(tile.onClick).heightIn(min = TileMinHeight).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
    ) {
        Box(
            Modifier.size(TileIconSize).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(tile.icon, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(tile.label), style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            tile.count?.takeIf { it > 0 }?.let {
                Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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
    modifier: Modifier = Modifier,
) {
    // Smaller than the card's heading: it switches the list below rather than naming a section.
    Row(modifier.fillMaxWidth().padding(top = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        ModeSwitch(
            SheetGroup.entries, group,
            label = { if (it == SheetGroup.Mine) stringResource(R.string.library_sheets_mine, mine) else stringResource(R.string.library_sheets_starred, starred) },
            onSelect = onGroup, modifier = Modifier.weight(1f), textStyle = MaterialTheme.typography.titleSmall,
        )
        IconButton(onClick = onCreate) { Icon(XmIcons.Outlined.Add, stringResource(R.string.library_sheet_new)) }
        onImport?.let { IconButton(onClick = it) { Icon(XmIcons.Outlined.Input, stringResource(R.string.library_sheet_import)) } }
    }
}

@Composable
private fun EmptySheets(modifier: Modifier = Modifier, onCreate: () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
    ) {
        Text(
            stringResource(R.string.library_sheets_empty), Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(onClick = onCreate) { Text(stringResource(R.string.library_sheet_new)) }
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
