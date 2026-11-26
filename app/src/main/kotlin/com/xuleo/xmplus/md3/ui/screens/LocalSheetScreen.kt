@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.xuleo.xmplus.md3.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.media.MusicLibrary
import com.xuleo.xmplus.md3.core.media.MusicPlaySource
import com.xuleo.xmplus.md3.core.model.MusicItem
import com.xuleo.xmplus.md3.ui.components.AddToSheetSheet
import com.xuleo.xmplus.md3.ui.components.Cover
import com.xuleo.xmplus.md3.ui.components.MusicCover
import com.xuleo.xmplus.md3.ui.components.ReorderHandle
import com.xuleo.xmplus.md3.ui.components.SheetNameDialog
import com.xuleo.xmplus.md3.ui.components.SongMenuItem
import com.xuleo.xmplus.md3.ui.components.SongRow
import com.xuleo.xmplus.md3.ui.components.XmMenu
import com.xuleo.xmplus.md3.ui.components.XmMenuItem
import com.xuleo.xmplus.md3.ui.components.XmSwap
import com.xuleo.xmplus.md3.ui.components.rememberReorderState
import com.xuleo.xmplus.md3.ui.components.reorderItem
import com.xuleo.xmplus.md3.ui.icons.XmIcons
import kotlinx.coroutines.launch

private enum class SheetMode { Normal, Selecting, Sorting }

private val MosaicSize = 120.dp
private val ToolbarSpace = 112.dp

/**
 * 我喜欢 ([MusicLibrary.LikesId]) or one of the user's sheets: the cover mosaic, name and count, 播放全部, then the songs.
 * Tapping a song plays the sheet from it. A long press (or 选择) selects songs to add, move or remove; 排序 drags them.
 * 我喜欢 cannot be renamed or deleted.
 */
@Composable
internal fun LocalSheetScreen(
    library: MusicLibrary,
    sheetId: String,
    onBack: () -> Unit,
    onPlay: (List<MusicItem>, Int, MusicPlaySource) -> Unit,
    onPlayNext: (MusicItem) -> Unit,
    playingKey: String?,
) {
    val likes = sheetId == MusicLibrary.LikesId
    val liked by library.liked.collectAsStateWithLifecycle()
    val sheets by library.sheets.collectAsStateWithLifecycle()
    val loaded by library.loaded.collectAsStateWithLifecycle()
    val sheet = if (likes) null else sheets.firstOrNull { it.id == sheetId }
    // The sheet was deleted, here or elsewhere.
    LaunchedEffect(loaded, sheet == null) { if (!likes && loaded && sheet == null) onBack() }
    val name = if (likes) stringResource(R.string.music_favorites) else sheet?.name.orEmpty()
    val songs = if (likes) liked else sheet?.songs.orEmpty()
    val source = if (likes) MusicPlaySource.Likes else MusicPlaySource.sheet(sheetId)

    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val animated = rememberAnimationsEnabled()
    var mode by rememberSaveable { mutableStateOf(SheetMode.Normal) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var adding by remember { mutableStateOf<List<MusicItem>?>(null) }
    var moving by remember { mutableStateOf<List<MusicItem>?>(null) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val removedText = stringResource(R.string.music_sheet_removed)
    val undoText = stringResource(R.string.history_undo)
    val reorder = rememberReorderState<MusicItem>(list, { it.key }) { from, to -> library.moveSong(sheetId, from, to) }
    val showTitle by remember { derivedStateOf { list.firstVisibleItemIndex > 0 } }

    fun endMode() {
        mode = SheetMode.Normal
        selected = emptySet()
    }
    BackHandler(enabled = mode != SheetMode.Normal, onBack = ::endMode)
    // Removed songs come back to where they were with 撤销.
    fun remove(chosen: List<MusicItem>) {
        if (chosen.isEmpty()) return
        val before = library.songs(sheetId)
        val keys = chosen.mapTo(HashSet()) { it.key }
        val removed = before.withIndex().filter { it.value.key in keys }
        library.removeSongs(sheetId, chosen)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(removedText, undoText, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                library.addSongs(sheetId, removed.map { it.value })
                removed.indices.reversed().forEach { k -> library.moveSong(sheetId, k, removed[k].index) }
            }
        }
    }
    val chosen = songs.filter { it.key in selected }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = when (mode) {
                        SheetMode.Selecting -> stringResource(R.string.history_selected, selected.size)
                        SheetMode.Sorting -> stringResource(R.string.plugin_sort)
                        SheetMode.Normal -> if (showTitle) name else ""
                    }
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    if (mode == SheetMode.Normal) {
                        IconButton(onClick = onBack) { Icon(XmIcons.Outlined.ArrowBack, stringResource(R.string.action_back)) }
                    } else {
                        IconButton(onClick = ::endMode) { Icon(XmIcons.Outlined.Close, stringResource(R.string.action_cancel)) }
                    }
                },
                actions = {
                    XmSwap(mode, contentAlignment = Alignment.CenterEnd) { shown ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (shown) {
                                SheetMode.Selecting -> {
                                    val all = songs.isNotEmpty() && selected.size == songs.size
                                    IconButton(onClick = { selected = if (all) emptySet() else songs.mapTo(HashSet()) { it.key } }) {
                                        Icon(
                                            XmIcons.Filled.DoneAll, stringResource(R.string.history_select_all),
                                            tint = if (all) MaterialTheme.colorScheme.primary else androidx.compose.material3.LocalContentColor.current,
                                        )
                                    }
                                }
                                SheetMode.Sorting -> TextButton(onClick = ::endMode) { Text(stringResource(R.string.plugin_done)) }
                                SheetMode.Normal -> SheetOverflow(
                                    canEdit = songs.isNotEmpty(),
                                    canSort = songs.size > 1,
                                    own = !likes,
                                    onSelect = { mode = SheetMode.Selecting },
                                    onSort = { mode = SheetMode.Sorting },
                                    onRename = { renaming = true },
                                    onDelete = { deleting = true },
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                Modifier.fillMaxSize(), list,
                contentPadding = PaddingValues(bottom = if (mode == SheetMode.Selecting) ToolbarSpace else 8.dp),
            ) {
                item(key = "header") { SheetHeader(name, songs) }
                if (songs.isEmpty()) {
                    item(key = "empty") { Note(stringResource(if (likes) R.string.music_likes_empty else R.string.music_nothing)) }
                } else {
                    item(key = "play_all") { PlayAllRow(songs.size) { if (mode == SheetMode.Normal) onPlay(songs, 0, source) } }
                }
                val rows = if (mode == SheetMode.Sorting) reorder.items(songs) else songs
                items(rows, key = { it.key }) { song ->
                    val index = songs.indexOf(song)
                    SongRow(
                        song,
                        modifier = if (mode == SheetMode.Sorting) Modifier.reorderItem(this, reorder, song, animated) else Modifier,
                        playing = song.key == playingKey,
                        onPlayNext = { onPlayNext(song) },
                        onLongClick = if (mode == SheetMode.Normal) ({
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            mode = SheetMode.Selecting
                            selected = setOf(song.key)
                        }) else null,
                        extraItems = listOf(SongMenuItem(stringResource(R.string.music_queue_remove), XmIcons.Outlined.DeleteOutline) { remove(listOf(song)) }),
                        trailing = when (mode) {
                            SheetMode.Selecting -> ({ Checkbox(checked = song.key in selected, onCheckedChange = null, Modifier.padding(12.dp)) })
                            SheetMode.Sorting -> ({ ReorderHandle(reorder, song) })
                            SheetMode.Normal -> null
                        },
                    ) {
                        when (mode) {
                            SheetMode.Selecting -> selected = if (song.key in selected) selected - song.key else selected + song.key
                            SheetMode.Sorting -> Unit
                            SheetMode.Normal -> if (index >= 0) onPlay(songs, index, source)
                        }
                    }
                }
            }
            val motion = MaterialTheme.motionScheme
            AnimatedVisibility(
                visible = mode == SheetMode.Selecting,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = FloatingToolbarDefaults.ScreenOffset),
                enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec()),
                exit = slideOutVertically(motion.fastSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec()),
            ) {
                HorizontalFloatingToolbar(expanded = true, colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()) {
                    val any = chosen.isNotEmpty()
                    ToolbarAction(R.string.music_add_to_sheet, XmIcons.Outlined.PlaylistAdd, any) { adding = chosen }
                    ToolbarAction(R.string.music_move_to, XmIcons.Outlined.SwapVert, any) { moving = chosen }
                    ToolbarAction(R.string.music_queue_remove, XmIcons.Outlined.DeleteOutline, any) {
                        remove(chosen)
                        endMode()
                    }
                }
            }
        }
    }
    adding?.let { AddToSheetSheet(it, onDismiss = { adding = null }, from = sheetId, onDone = ::endMode) }
    moving?.let { AddToSheetSheet(it, onDismiss = { moving = null }, from = sheetId, move = true, onDone = ::endMode) }
    if (renaming && sheet != null) {
        SheetNameDialog(
            stringResource(R.string.music_sheet_rename), stringResource(R.string.bookmark_save),
            onDismiss = { renaming = false }, initial = sheet.name,
        ) {
            renaming = false
            library.renameSheet(sheetId, it)
        }
    }
    if (deleting && sheet != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.music_sheet_delete_title, sheet.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    library.deleteSheets(listOf(sheetId))
                }) { Text(stringResource(R.string.downloads_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SheetOverflow(
    canEdit: Boolean,
    canSort: Boolean,
    own: Boolean,
    onSelect: () -> Unit,
    onSort: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(XmIcons.Outlined.MoreVert, stringResource(R.string.search_more)) }
        XmMenu(open, { open = false }) {
            val count = if (own) 4 else 2
            XmMenuItem(stringResource(R.string.history_select), { open = false; onSelect() }, 0, count, icon = XmIcons.Outlined.Checklist, enabled = canEdit)
            XmMenuItem(stringResource(R.string.plugin_sort), { open = false; onSort() }, 1, count, icon = XmIcons.Outlined.SwapVert, enabled = canSort)
            if (own) {
                XmMenuItem(stringResource(R.string.music_sheet_rename), { open = false; onRename() }, 2, count, icon = XmIcons.Outlined.Edit)
                XmMenuItem(stringResource(R.string.music_sheet_delete), { open = false; onDelete() }, 3, count, icon = XmIcons.Outlined.DeleteOutline)
            }
        }
    }
}

@Composable
private fun SheetHeader(name: String, songs: List<MusicItem>) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding).padding(top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SheetMosaic(songs, Modifier.size(MosaicSize))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                pluralStringResource(R.plurals.music_song_count, songs.size, songs.size),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Four different song covers in a square, as QQ 音乐 shows a sheet; with fewer, the first cover or the placeholder. */
@Composable
internal fun SheetMosaic(songs: List<MusicItem>, modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.large) {
    val covers = remember(songs) { songs.mapNotNull { it.artwork }.distinct().take(4) }
    if (covers.size < 4) {
        MusicCover(covers.firstOrNull(), modifier, shape)
        return
    }
    Column(modifier.clip(shape)) {
        covers.chunked(2).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                row.forEach { Cover(it, Modifier.weight(1f).fillMaxHeight(), RectangleShape) }
            }
        }
    }
}
