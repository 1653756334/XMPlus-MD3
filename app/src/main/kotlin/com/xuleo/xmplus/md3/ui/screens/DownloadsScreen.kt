package com.xuleo.xmplus.md3.ui.screens

import android.os.SystemClock
import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.ui.icons.XmIcons
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.download.DownloadState
import com.xuleo.xmplus.md3.core.download.DownloadTask
import com.xuleo.xmplus.md3.download.DownloadsViewModel
import com.xuleo.xmplus.md3.download.downloadErrorMessage
import com.xuleo.xmplus.md3.ui.Destination
import com.xuleo.xmplus.md3.ui.components.Cover
import com.xuleo.xmplus.md3.ui.components.PagerTabs
import com.xuleo.xmplus.md3.ui.components.SettlingPager
import com.xuleo.xmplus.md3.ui.components.formatDuration
import kotlinx.coroutines.delay

/** The downloaded episodes of one album, newest album first. */
private class DownloadedAlbum(val id: String, val episodes: List<DownloadTask>) {
    val title: String get() = episodes.first().albumTitle
    val coverUrl: String? get() = episodes.firstNotNullOfOrNull { it.coverUrl }
    val bytes: Long get() = episodes.sumOf { it.bytes }
}

/** A removal waiting for the listener to agree, with the audio it frees. */
private sealed interface PendingRemoval {
    data class Episode(val task: DownloadTask) : PendingRemoval
    data class Album(val album: DownloadedAlbum) : PendingRemoval
    /** An unfinished episode that was already charged: cancelling it gives up that listening time. */
    data class Charged(val task: DownloadTask) : PendingRemoval
    data object Unfinished : PendingRemoval
}

/**
 * 下载: how much the downloads take and what is left, then 已下载 and 下载中, which swipe. 已下载 lists albums that open
 * into their episodes; 下载中 shows each episode's progress and speed. The top bar's icons act on everything in 下载中.
 */
@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel, onOpenAlbum: (String) -> Unit, onPlay: (albumId: String, trackId: String) -> Unit) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val free by viewModel.freeBytes.collectAsStateWithLifecycle()
    val active = remember(tasks) { tasks.filter { it.state != DownloadState.Completed } }
    val albums = remember(tasks) {
        tasks.filter { it.state == DownloadState.Completed }.groupBy { it.albumId }
            .map { (id, episodes) -> DownloadedAlbum(id, episodes.sortedBy { it.index ?: Int.MAX_VALUE }) }
            .sortedByDescending { album -> album.episodes.maxOf { it.createdAt } }
    }
    val pager = rememberPagerState(initialPage = if (albums.isEmpty() && active.isNotEmpty()) ActiveTab else DoneTab) { 2 }
    var removal by remember { mutableStateOf<PendingRemoval?>(null) }
    val running = active.any { it.state == DownloadState.Queued || it.state == DownloadState.Running }
    TabScaffold(
        Destination.Downloads.label,
        actions = {
            if (pager.currentPage == ActiveTab && active.isNotEmpty()) {
                if (running) {
                    IconButton(onClick = viewModel::pauseAll) { Icon(XmIcons.Outlined.PauseCircle, stringResource(R.string.downloads_pause_all)) }
                } else {
                    IconButton(onClick = viewModel::resumeAll) { Icon(XmIcons.Outlined.PlayCircle, stringResource(R.string.downloads_resume_all)) }
                }
                IconButton(onClick = { removal = PendingRemoval.Unfinished }) {
                    Icon(XmIcons.Outlined.Cancel, stringResource(R.string.downloads_cancel_all))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            StorageBar(used = albums.sumOf { it.bytes } + active.sumOf { it.bytes }, free = free)
            PagerTabs(
                pager,
                listOf(
                    tabLabel(R.string.downloads_tab_done, albums.sumOf { it.episodes.size }),
                    tabLabel(R.string.downloads_tab_active, active.size),
                ),
            )
            SettlingPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                if (page == DoneTab) {
                    DonePage(
                        albums, onPlay = { onPlay(it.albumId, it.trackId) }, onOpenAlbum = onOpenAlbum,
                        onDeleteEpisode = { removal = PendingRemoval.Episode(it) },
                        onDeleteAlbum = { removal = PendingRemoval.Album(it) },
                    )
                } else {
                    ActivePage(active, viewModel, onCancel = { task ->
                        if (task.receiptId != null) removal = PendingRemoval.Charged(task) else viewModel.cancel(task.trackId)
                    })
                }
            }
        }
    }
    removal?.let { pending ->
        RemovalDialog(
            pending,
            onConfirm = {
                removal = null
                when (pending) {
                    is PendingRemoval.Episode -> viewModel.delete(pending.task.trackId)
                    is PendingRemoval.Album -> viewModel.deleteAlbum(pending.album.id)
                    is PendingRemoval.Charged -> viewModel.cancel(pending.task.trackId)
                    PendingRemoval.Unfinished -> viewModel.cancelAll()
                }
            },
            onDismiss = { removal = null },
        )
    }
}

@Composable
private fun tabLabel(label: Int, count: Int): String =
    if (count == 0) stringResource(label) else stringResource(R.string.downloads_tab_count, stringResource(label), count)

/** Space the downloads take against what the device has left, as one thin bar with both numbers under it. */
@Composable
private fun StorageBar(used: Long, free: Long?) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val fraction = if (free == null || used + free <= 0) 0f else used.toFloat() / (used + free)
    Column(Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding, vertical = 8.dp)) {
        LinearProgressIndicator(
            progress = { fraction }, modifier = Modifier.fillMaxWidth().height(4.dp),
            trackColor = scheme.surfaceContainerHighest, gapSize = 0.dp, drawStopIndicator = {},
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(
                stringResource(R.string.downloads_storage_used, Formatter.formatShortFileSize(context, used)),
                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
            )
            if (free != null) {
                Text(
                    stringResource(R.string.downloads_storage_free, Formatter.formatShortFileSize(context, free)),
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Albums, each opening in place into its episodes. Tapping an episode plays it from the downloaded file. */
@Composable
private fun DonePage(
    albums: List<DownloadedAlbum>,
    onPlay: (DownloadTask) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onDeleteEpisode: (DownloadTask) -> Unit,
    onDeleteAlbum: (DownloadedAlbum) -> Unit,
) {
    if (albums.isEmpty()) {
        EmptyPage(XmIcons.Outlined.DownloadDone, R.string.downloads_empty_done)
        return
    }
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = XmLayout.SectionGap)) {
        for (album in albums) {
            val open = album.id in expanded
            item(key = "album-${album.id}") {
                AlbumRow(
                    album, open,
                    onToggle = { expanded = if (open) expanded - album.id else expanded + album.id },
                    onOpenAlbum = { onOpenAlbum(album.id) },
                    onDelete = { onDeleteAlbum(album) },
                )
            }
            if (open) {
                items(album.episodes, key = { "episode-${it.trackId}" }) { task ->
                    DoneEpisodeRow(task, onPlay = { onPlay(task) }, onDelete = { onDeleteEpisode(task) })
                }
            }
        }
    }
}

@Composable
private fun AlbumRow(album: DownloadedAlbum, open: Boolean, onToggle: () -> Unit, onOpenAlbum: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val turn by animateFloatAsState(if (open) 180f else 0f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "albumChevron")
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = stringResource(if (open) R.string.downloads_hide_episodes else R.string.downloads_show_episodes), onClick = onToggle)
            .padding(start = XmLayout.PagePadding, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(album.coverUrl, Modifier.size(CoverSize), MaterialTheme.shapes.small)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(album.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val count = album.episodes.size
            Text(
                pluralStringResource(R.plurals.downloads_album_meta, count, count, Formatter.formatShortFileSize(context, album.bytes)),
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
            )
        }
        Icon(XmIcons.Filled.KeyboardArrowDown, contentDescription = null, Modifier.graphicsLayer { rotationZ = turn }, tint = scheme.onSurfaceVariant)
        Box {
            IconButton(onClick = { menu = true }) { Icon(XmIcons.Outlined.MoreVert, stringResource(R.string.player_more), tint = scheme.onSurfaceVariant) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.downloads_open_album)) },
                    leadingIcon = { Icon(XmIcons.Outlined.Album, contentDescription = null) },
                    onClick = {
                        menu = false
                        onOpenAlbum()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.downloads_delete_album)) },
                    leadingIcon = { Icon(XmIcons.Outlined.DeleteOutline, contentDescription = null) },
                    onClick = {
                        menu = false
                        onDelete()
                    },
                )
            }
        }
    }
}

/** An episode under its album, indented to the album title: number, title, length and size, and a delete icon. */
@Composable
private fun DoneEpisodeRow(task: DownloadTask, onPlay: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = stringResource(R.string.action_play), onClick = onPlay)
            .padding(start = XmLayout.PagePadding, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            task.index?.toString().orEmpty(), Modifier.width(CoverSize), style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(task.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    task.durationSeconds.takeIf { it > 0 }?.let { formatDuration(it) },
                    Formatter.formatShortFileSize(context, task.bytes),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) { Icon(XmIcons.Outlined.DeleteOutline, stringResource(R.string.downloads_delete), tint = scheme.onSurfaceVariant) }
    }
}

@Composable
private fun ActivePage(active: List<DownloadTask>, viewModel: DownloadsViewModel, onCancel: (DownloadTask) -> Unit) {
    if (active.isEmpty()) {
        EmptyPage(XmIcons.Outlined.Downloading, R.string.downloads_empty_active)
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = XmLayout.SectionGap)) {
        items(active, key = { it.trackId }) { task ->
            ActiveRow(
                task,
                onPause = { viewModel.pause(task.trackId) },
                onResume = { viewModel.resume(task.trackId) },
                onCancel = { onCancel(task) },
            )
        }
    }
}

/** Cover, title and album, then the bar with how much has arrived and how fast; pause or resume, and remove. */
@Composable
private fun ActiveRow(task: DownloadTask, onPause: () -> Unit, onResume: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val running = task.state == DownloadState.Running
    val waiting = task.state == DownloadState.Queued
    val total = task.totalBytes?.takeIf { it > 0 }
    val speed = rememberSpeed(task.bytes, running)
    Row(
        Modifier.fillMaxWidth().padding(start = XmLayout.PagePadding, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(task.coverUrl, Modifier.size(CoverSize), MaterialTheme.shapes.small)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                task.albumTitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val bar = Modifier.fillMaxWidth().padding(top = 8.dp).height(3.dp)
            when {
                running && total == null -> LinearProgressIndicator(bar, trackColor = scheme.surfaceContainerHighest, gapSize = 0.dp)
                total != null -> LinearProgressIndicator(
                    progress = { (task.bytes.toFloat() / total).coerceIn(0f, 1f) }, modifier = bar,
                    color = if (running) scheme.primary else scheme.outline,
                    trackColor = scheme.surfaceContainerHighest, gapSize = 0.dp, drawStopIndicator = {},
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                val status = when (task.state) {
                    DownloadState.Failed -> stringResource(downloadErrorMessage(task.error))
                    DownloadState.Queued -> stringResource(R.string.downloads_waiting)
                    DownloadState.Paused -> stringResource(R.string.downloads_paused)
                    else -> total?.let {
                        stringResource(
                            R.string.downloads_progress_size,
                            Formatter.formatShortFileSize(context, task.bytes), Formatter.formatShortFileSize(context, it),
                        )
                    } ?: stringResource(R.string.downloads_running)
                }
                Text(
                    status, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                    color = if (task.state == DownloadState.Failed) scheme.error else scheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (running && speed != null) {
                    Text(
                        stringResource(R.string.downloads_speed, Formatter.formatShortFileSize(context, speed)),
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
        val (icon, label, action) = when {
            running || waiting -> Triple(XmIcons.Outlined.Pause, R.string.downloads_pause, onPause)
            task.state == DownloadState.Failed -> Triple(XmIcons.Outlined.Refresh, R.string.downloads_retry, onResume)
            else -> Triple(XmIcons.Outlined.PlayArrow, R.string.downloads_resume, onResume)
        }
        IconButton(onClick = action) { Icon(icon, stringResource(label), tint = scheme.onSurfaceVariant) }
        IconButton(onClick = onCancel) { Icon(XmIcons.Outlined.Close, stringResource(R.string.downloads_cancel), tint = scheme.onSurfaceVariant) }
    }
}

/** Bytes per second over the last second while [running]; null until the first second has passed. */
@Composable
private fun rememberSpeed(bytes: Long, running: Boolean): Long? {
    var speed by remember { mutableStateOf<Long?>(null) }
    val latest by rememberUpdatedState(bytes)
    LaunchedEffect(running) {
        speed = null
        if (!running) return@LaunchedEffect
        var lastBytes = latest
        var lastAt = SystemClock.elapsedRealtime()
        while (true) {
            delay(SpeedSampleMs)
            val now = SystemClock.elapsedRealtime()
            val current = latest
            speed = ((current - lastBytes).coerceAtLeast(0) * 1000 / (now - lastAt).coerceAtLeast(1))
            lastBytes = current
            lastAt = now
        }
    }
    return speed
}

@Composable
private fun EmptyPage(icon: ImageVector, text: Int) {
    Column(
        Modifier.fillMaxSize().padding(XmLayout.FormPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Removing audio always says how much goes with it; cancelling everything says what is lost. */
@Composable
private fun RemovalDialog(removal: PendingRemoval, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val (title, body, action) = when (removal) {
        is PendingRemoval.Episode -> Triple(
            stringResource(R.string.downloads_delete_title),
            stringResource(R.string.downloads_delete_body, Formatter.formatShortFileSize(context, removal.task.bytes)),
            stringResource(R.string.downloads_delete),
        )
        is PendingRemoval.Album -> Triple(
            stringResource(R.string.downloads_delete_album_title),
            stringResource(R.string.downloads_delete_body, Formatter.formatShortFileSize(context, removal.album.bytes)),
            stringResource(R.string.downloads_delete),
        )
        is PendingRemoval.Charged -> Triple(
            stringResource(R.string.downloads_cancel_title),
            stringResource(R.string.downloads_cancel_charged_body),
            stringResource(R.string.downloads_cancel),
        )
        PendingRemoval.Unfinished -> Triple(
            stringResource(R.string.downloads_cancel_all_title),
            stringResource(R.string.downloads_cancel_all_body),
            stringResource(R.string.downloads_cancel_all),
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val DoneTab = 0
private const val ActiveTab = 1
private val CoverSize = 48.dp
private const val SpeedSampleMs = 1_000L
