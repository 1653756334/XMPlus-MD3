package com.xuleo.xmplus.md3.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.media.ListenRecord
import com.xuleo.xmplus.md3.core.media.SongResume
import com.xuleo.xmplus.md3.core.model.ContentKind
import com.xuleo.xmplus.md3.library.LibraryViewModel
import com.xuleo.xmplus.md3.ui.components.PlayingBars
import com.xuleo.xmplus.md3.ui.components.PlayingMarkSize
import com.xuleo.xmplus.md3.ui.components.SelectionToolbar
import com.xuleo.xmplus.md3.ui.components.SelectionToolbarSpace
import com.xuleo.xmplus.md3.ui.components.ToolbarAction
import com.xuleo.xmplus.md3.ui.components.XmSwap
import com.xuleo.xmplus.md3.ui.components.formatDate
import com.xuleo.xmplus.md3.ui.icons.XmIcons
import kotlinx.coroutines.launch

private enum class RecentFilter(val kind: ContentKind?) { All(null), Books(ContentKind.Episode), Music(ContentKind.Song) }

/**
 * 最近播放: this device's history of both kinds, newest first. Tapping a row plays it from where it stopped; a long
 * press selects, and removed rows come back with 撤销. Back ends selection first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentScreen(
    viewModel: LibraryViewModel,
    nowPlaying: NowPlaying,
    onBack: () -> Unit,
    onPlay: (String, String, Long) -> Unit,
    onPlaySong: (SongResume) -> Unit,
) {
    val history by viewModel.listenHistory.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    var filter by rememberSaveable { mutableStateOf(RecentFilter.All) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    val removed = stringResource(R.string.history_removed)
    val undo = stringResource(R.string.history_undo)
    val rows = history.orEmpty().filter { filter.kind == null || it.kind == filter.kind }
    val allSelected = rows.isNotEmpty() && rows.all { it.albumId in selected }
    val endSelection = {
        selecting = false
        selected = emptySet()
    }
    BackHandler(enabled = selecting, onBack = endSelection)
    // The selected rows leave together, and one Undo puts them all back.
    val deleteSelected: () -> Unit = {
        val chosen = rows.filter { it.albumId in selected }
        chosen.forEach(viewModel::removeHistory)
        endSelection()
        scope.launch {
            if (snackbar.showSnackbar(removed, undo, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                chosen.forEach(viewModel::restoreHistory)
            }
        }
    }
    val toggle: (ListenRecord) -> Unit = { record ->
        selected = if (record.albumId in selected) selected - record.albumId else selected + record.albumId
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selecting) stringResource(R.string.history_selected, selected.size) else stringResource(R.string.library_recent),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (selecting) {
                        IconButton(onClick = endSelection) { Icon(XmIcons.Outlined.Close, stringResource(R.string.action_cancel)) }
                    } else {
                        IconButton(onClick = onBack) { Icon(XmIcons.Outlined.ArrowBack, stringResource(R.string.action_back)) }
                    }
                },
                actions = {
                    if (selecting) {
                        IconButton(onClick = {
                            selected = if (allSelected) emptySet() else rows.mapTo(HashSet()) { it.albumId }
                        }) {
                            Icon(XmIcons.Filled.DoneAll, stringResource(R.string.history_select_all))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = XmLayout.PagePadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RecentFilter.entries.forEach { option ->
                    FilterChip(
                        selected = option == filter,
                        onClick = {
                            filter = option
                            endSelection()
                        },
                        label = { Text(stringResource(option.label)) },
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                history == null -> Box(Modifier.fillMaxSize())
                rows.isEmpty() -> Note(stringResource(R.string.library_recent_empty))
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = if (selecting) SelectionToolbarSpace else 0.dp),
                ) {
                    items(rows, key = { "${it.kind.key}:${it.albumId}" }) { record ->
                        RecentRow(
                            record,
                            playing = nowPlaying.matches(record),
                            showKind = filter == RecentFilter.All,
                            selecting = selecting,
                            checked = record.albumId in selected,
                            onClick = {
                                if (selecting) toggle(record) else viewModel.resume(record, onPlay, onPlaySong)
                            },
                            onLongClick = {
                                if (!selecting) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selecting = true
                                }
                                toggle(record)
                            },
                        )
                    }
                }
            }
            SelectionToolbar(selecting) {
                ToolbarAction(R.string.history_remove, XmIcons.Outlined.DeleteOutline, selected.isNotEmpty(), deleteSelected)
            }
            }
        }
    }
}

private val RecentFilter.label: Int
    get() = when (this) {
        RecentFilter.All -> R.string.search_kind_all
        RecentFilter.Books -> R.string.tab_discover
        RecentFilter.Music -> R.string.tab_music
    }

/** An episode row shows its album, the episode and where playback stopped; a song row its artist. */
@Composable
private fun RecentRow(
    record: ListenRecord,
    playing: Boolean,
    showKind: Boolean,
    selecting: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val song = record.kind == ContentKind.Song
    ListingRow(
        cover = record.coverUrl,
        title = if (song) record.trackTitle else record.albumTitle,
        subtitle = if (song) record.anchor else record.trackTitle,
        meta = if (song) formatDate(record.playedAtMs, locale)
        else joinMeta(progressText(record.positionMs, record.durationMs), formatDate(record.playedAtMs, locale)),
        onOpen = onClick,
        modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        music = song,
        highlighted = playing,
        kind = record.kind.takeIf { showKind },
        onLongClick = onLongClick,
        trailing = {
            XmSwap(selecting) { active ->
                when {
                    active -> Checkbox(checked = checked, onCheckedChange = null)
                    playing -> PlayingBars(Modifier.size(PlayingMarkSize))
                }
            }
        },
    )
}
