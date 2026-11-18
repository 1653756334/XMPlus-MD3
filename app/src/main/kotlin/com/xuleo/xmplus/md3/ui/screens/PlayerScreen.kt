@file:OptIn(ExperimentalMaterial3Api::class)

package com.xuleo.xmplus.md3.ui.screens

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.media.BookmarkEpisode
import com.xuleo.xmplus.md3.core.media.PlaybackController
import com.xuleo.xmplus.md3.core.media.PlaybackPhase
import com.xuleo.xmplus.md3.core.media.PlaybackQueueState
import com.xuleo.xmplus.md3.core.media.PlaybackSelection
import com.xuleo.xmplus.md3.core.media.PlaybackSkipSeconds
import com.xuleo.xmplus.md3.core.media.PlaybackState
import com.xuleo.xmplus.md3.core.media.SleepTimerState
import com.xuleo.xmplus.md3.core.model.AlbumSkip
import com.xuleo.xmplus.md3.ui.components.Cover
import com.xuleo.xmplus.md3.ui.components.DetailArtworkBackdrop
import com.xuleo.xmplus.md3.ui.components.DetailTopBarHeight
import com.xuleo.xmplus.md3.ui.components.ModeSwitch
import com.xuleo.xmplus.md3.ui.components.formatDuration
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

/** A full-height sheet so closing the player does not navigate away from the page under it. */
@Composable
internal fun PlayerSheet(
    playback: PlaybackController,
    onDismiss: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenAnchor: (albumId: String, uid: String?) -> Unit,
    onAccounts: () -> Unit,
) {
    val state by playback.state.collectAsStateWithLifecycle()
    val queue by playback.queue.collectAsStateWithLifecycle()
    val speed by playback.speed.collectAsStateWithLifecycle()
    val sleep by playback.sleepTimer.collectAsStateWithLifecycle()
    val skip by playback.skip.collectAsStateWithLifecycle()
    val sheet = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    fun closeThen(next: () -> Unit = {}) {
        scope.launch {
            sheet.hide()
            if (!sheet.isVisible) {
                onDismiss()
                next()
            }
        }
    }
    val comingSoon = rememberComingSoon()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        sheetMaxWidth = Dp.Unspecified,
        shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = Color.Transparent,
        dragHandle = null,
        // The artwork fills the sheet while the controls apply the insets themselves.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        val episode = state.selection
        val bookmarkEpisode = playback.bookmarkEpisode()
        var showActions by remember(episode?.album?.id, episode?.track?.id) { mutableStateOf(false) }
        var showQueue by remember(episode?.album?.id) { mutableStateOf(false) }
        var showBookmarks by remember(bookmarkEpisode) { mutableStateOf<BookmarkEpisode?>(null) }
        var showSpeed by remember { mutableStateOf(false) }
        var showSleep by remember { mutableStateOf(false) }
    var showSkip by remember { mutableStateOf(false) }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (episode?.coverUrl != null) {
                DetailArtworkBackdrop(
                    episode.coverUrl, Modifier.fillMaxWidth().height((maxHeight * 0.68f).coerceAtMost(540.dp)),
                )
            }
            Column(
                Modifier.fillMaxSize().windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                ),
            ) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                PlayerTopBar(
                    hasEpisode = episode != null,
                    onMinimize = { closeThen() },
                    onPage = { if (it != PlayerPage.Playing) comingSoon() },
                    onMore = { showActions = true },
                )
                if (episode == null) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.mini_player_idle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    PlayerLayout(
                        cover = { PlayerCover(episode.coverUrl, playing = state.wantsToPlay) },
                        controls = {
                            PlayerControls(
                                episode = episode,
                                state = state,
                                queue = queue,
                                speed = speed,
                                sleep = sleep,
                                skip = skip,
                                playback = playback,
                                onOpenAlbum = { closeThen { onOpenAlbum(episode.album.id) } },
                                onOpenQueue = { showQueue = true },
                                onOpenSpeed = { showSpeed = true },
                                onOpenSleep = { showSleep = true },
                                onOpenBookmarks = { showBookmarks = playback.bookmarkEpisode() },
                                onComingSoon = comingSoon,
                                onAccounts = { closeThen(onAccounts) },
                            )
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }
        if (showActions && episode != null) {
            PlayerActionsSheet(
                onDismiss = { showActions = false },
                onOpenAlbum = { closeThen { onOpenAlbum(episode.album.id) } },
                onOpenAnchor = { closeThen { onOpenAnchor(episode.album.id, episode.album.anchorUid) } },
                skip = skip,
                onOpenSkip = { showSkip = true },
                canGoToStart = state.canSeek && state.positionMs > 0,
                onGoToStart = {
                    // Playback may have changed while the sheet was closing.
                    if (playback.state.value.selection == episode) playback.seekTo(0)
                },
            )
        }
        if (showQueue && episode != null) {
            PlayerQueueSheet(queue, onSelect = playback::playQueued, onDismiss = { showQueue = false })
        }
        if (bookmarkEpisode != null && showBookmarks == bookmarkEpisode) {
            PlayerBookmarksSheet(playback, bookmarkEpisode, state.canSeek, onDismiss = { showBookmarks = null })
        }
        if (showSkip && episode != null) {
            PlayerSkipSheet(
                skip = skip,
                durationMs = state.durationMs,
                loadSuggestion = playback::skipRecommendation,
                onSkip = { playback.setSkip(episode.album.id, it) },
                onDismiss = { showSkip = false },
            )
        }
        if (showSpeed && episode != null) {
            PlayerSpeedSheet(speed, onSpeed = playback::setSpeed, onDismiss = { showSpeed = false })
        }
        if (showSleep && episode != null) {
            PlayerSleepSheet(
                timer = sleep,
                onMinutes = playback::startSleepTimer,
                onEpisodeEnd = playback::sleepAtEpisodeEnd,
                onOff = playback::cancelSleepTimer,
                onDismiss = { showSleep = false },
            )
        }
    }
}

/** 文稿 and 评论 become swipeable pages when they are built; for now only 播放 exists. */
private enum class PlayerPage(@StringRes val label: Int) {
    Playing(R.string.player_page_playing),
    Transcript(R.string.player_page_transcript),
    Comments(R.string.player_page_comments),
}

/** Placeholder buttons for features that are not connected yet; a new tap replaces the previous toast. */
@Composable
private fun rememberComingSoon(): () -> Unit {
    val context = LocalContext.current
    val message = stringResource(R.string.player_coming_soon)
    return remember(context, message) {
        var shown: Toast? = null
        val show: () -> Unit = {
            shown?.cancel()
            val toast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
            toast.show()
            shown = toast
        }
        show
    }
}

/** Minimize on the left, the page headings in the middle (styled like 发现's), 更多 on the right. */
@Composable
private fun PlayerTopBar(hasEpisode: Boolean, onMinimize: () -> Unit, onPage: (PlayerPage) -> Unit, onMore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(DetailTopBarHeight).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMinimize) {
            Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.player_minimize), Modifier.size(28.dp))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (hasEpisode) {
                ModeSwitch(
                    items = PlayerPage.entries,
                    selected = PlayerPage.Playing,
                    label = { stringResource(it.label) },
                    onSelect = onPage,
                    textStyle = MaterialTheme.typography.titleMedium,
                )
            }
        }
        if (hasEpisode) {
            IconButton(onClick = onMore) { Icon(Icons.Filled.MoreHoriz, stringResource(R.string.player_more)) }
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}

/**
 * Portrait keeps the controls at the bottom and gives the cover the largest square left above them; when even the
 * smallest cover does not fit (short screens, large text) the page scrolls. Wide screens put the cover beside them.
 */
@Composable
private fun PlayerLayout(cover: @Composable () -> Unit, controls: @Composable () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        val viewport = maxHeight
        val scroll = rememberScrollState()
        if (maxWidth >= 560.dp && maxWidth > viewport * 1.25f) {
            Row(
                Modifier.widthIn(max = 900.dp).fillMaxWidth().verticalScroll(scroll).heightIn(min = viewport)
                    .padding(start = 28.dp, end = 28.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(minOf(320.dp, (viewport - 20.dp).coerceAtLeast(120.dp)))) { cover() }
                }
                Box(Modifier.weight(1f)) { controls() }
            }
        } else {
            Layout(
                content = {
                    Box { cover() }
                    Box { controls() }
                },
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 24.dp),
            ) { measurables, constraints ->
                val controlsPlaceable = measurables[1].measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                val gap = CoverGap.roundToPx()
                val bottom = PlayerBottomPadding.roundToPx()
                val largest = minOf(MaxCover.roundToPx(), constraints.maxWidth)
                val room = viewport.roundToPx() - controlsPlaceable.height - bottom - gap * 2
                val edge = room.coerceIn(minOf(MinCover.roundToPx(), largest), largest)
                val coverPlaceable = measurables[0].measure(Constraints.fixed(edge, edge))
                val height = maxOf(viewport.roundToPx(), edge + gap * 2 + controlsPlaceable.height + bottom)
                val controlsTop = height - bottom - controlsPlaceable.height
                layout(constraints.maxWidth, height) {
                    coverPlaceable.place((constraints.maxWidth - edge) / 2, (controlsTop - edge) / 2)
                    controlsPlaceable.place(0, controlsTop)
                }
            }
        }
    }
}

/** The cover eases back a little with a spring while paused. */
@Composable
private fun PlayerCover(artwork: String?, playing: Boolean) {
    val scale by animateFloatAsState(
        if (playing) 1f else PausedCoverScale,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "coverScale",
    )
    val corner = MaterialTheme.shapes.large
    Cover(
        artwork,
        Modifier.fillMaxSize().graphicsLayer {
            scaleX = scale
            scaleY = scale
            shadowElevation = CoverElevation.toPx()
            shape = corner
            clip = true
        },
        corner,
    )
}

/**
 * Top to bottom: what is playing, what you can do with it, where you are in it, the transport, and how you listen
 * (queue, speed, sleep timer).
 */
@Composable
private fun PlayerControls(
    episode: PlaybackSelection,
    state: PlaybackState,
    queue: PlaybackQueueState,
    speed: Float,
    sleep: SleepTimerState,
    skip: AlbumSkip,
    playback: PlaybackController,
    onOpenAlbum: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSpeed: () -> Unit,
    onOpenSleep: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onComingSoon: () -> Unit,
    onAccounts: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        EpisodeHeading(episode, onOpenAlbum)
        EpisodeActions(onComingSoon, onOpenBookmarks, state.canSeek, Modifier.padding(top = 4.dp))
        SeekBar(state, skip, playback::seekTo, Modifier.padding(top = 4.dp))
        TransportControls(state, queue, playback, Modifier.padding(top = 8.dp))
        PlaybackErrors(state, queue, onAccounts)
        ListeningTools(
            queue = queue,
            speed = speed,
            sleep = sleep,
            onOpenQueue = onOpenQueue,
            onOpenSpeed = onOpenSpeed,
            onOpenSleep = onOpenSleep,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/** The episode title, then the album name, which opens the album. */
@Composable
private fun EpisodeHeading(episode: PlaybackSelection, onOpenAlbum: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Text(
            episode.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (episode.subtitle.isNotBlank() && episode.subtitle != episode.title) {
            Row(
                Modifier.heightIn(min = 40.dp).clip(MaterialTheme.shapes.small)
                    .clickable(onClickLabel = stringResource(R.string.player_view_album), onClick = onOpenAlbum),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    episode.subtitle, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                    modifier = Modifier.size(18.dp), tint = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EpisodeActions(
    onComingSoon: () -> Unit,
    onOpenBookmarks: () -> Unit,
    bookmarksAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth()) {
        EpisodeAction(Icons.Outlined.ThumbUp, R.string.player_like, onComingSoon)
        EpisodeAction(Icons.Outlined.FavoriteBorder, R.string.player_favorite, onComingSoon)
        EpisodeAction(Icons.Outlined.BookmarkAdd, R.string.player_bookmarks, onOpenBookmarks, enabled = bookmarksAvailable)
        EpisodeAction(Icons.Outlined.Download, R.string.player_download, onComingSoon)
    }
}

@Composable
private fun RowScope.EpisodeAction(icon: ImageVector, @StringRes label: Int, onClick: () -> Unit, enabled: Boolean = true) {
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        ) {
            Icon(icon, stringResource(label))
        }
    }
}

/**
 * The position as a bar with the time riding on it in a pill, as in Ximalaya. Drag the pill or tap the bar to seek;
 * the pill shows where a drag will land.
 */
@Composable
private fun SeekBar(state: PlaybackState, skip: AlbumSkip, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val trackId = state.selection?.track?.id
    val enabled = state.canSeek
    val duration = state.durationMs.coerceAtLeast(0L)
    var dragging by remember(trackId, enabled) { mutableStateOf<Float?>(null) }
    var barWidth by remember { mutableIntStateOf(0) }
    var pillWidth by remember { mutableIntStateOf(0) }
    val played = if (duration > 0) state.positionMs.toFloat() / duration else 0f
    // The ends that will be skipped, as shares of the bar; none when they would swallow the episode.
    val skipping = duration > 0 && (skip.introSeconds + skip.outroSeconds) * 1_000L < duration
    val introShare = if (skipping) skip.introSeconds * 1_000f / duration else 0f
    val outroShare = if (skipping) skip.outroSeconds * 1_000f / duration else 0f
    val fraction = (dragging ?: played).coerceIn(0f, 1f)
    val shownMs = dragging?.let { (it * duration).toLong() } ?: state.positionMs
    val time = "${formatDuration(shownMs / 1000.0)} / ${formatDuration(duration / 1000.0)}"
    val seek by rememberUpdatedState(onSeek)
    val total by rememberUpdatedState(duration)
    // The pill's centre travels the bar, so it never hangs over either end.
    fun fractionAt(x: Float): Float {
        val travel = (barWidth - pillWidth).toFloat()
        return if (travel <= 0f) 0f else ((x - pillWidth / 2f) / travel).coerceIn(0f, 1f)
    }

    val scheme = MaterialTheme.colorScheme
    val activeColor = if (enabled) scheme.primary else scheme.outline
    val inactiveColor = scheme.outlineVariant
    val skippedColor = scheme.tertiary.copy(alpha = 0.6f)
    val pillColor = if (enabled) scheme.primary else scheme.surfaceContainerHighest
    val pillTextColor = if (enabled) scheme.onPrimary else scheme.onSurfaceVariant
    val label = stringResource(R.string.player_progress)

    Box(
        modifier.fillMaxWidth().height(SeekBarHeight)
            .onSizeChanged { barWidth = it.width }
            .pointerInput(enabled, trackId) {
                if (enabled) detectTapGestures { seek((fractionAt(it.x) * total).toLong()) }
            }
            .pointerInput(enabled, trackId) {
                if (enabled) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = fractionAt(it.x) },
                        onDragEnd = {
                            dragging?.let { seek((it * total).toLong()) }
                            dragging = null
                        },
                        onDragCancel = { dragging = null },
                    ) { change, _ ->
                        change.consume()
                        dragging = fractionAt(change.position.x)
                    }
                }
            }
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = time
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                if (enabled) {
                    setProgress { target ->
                        seek((target.coerceIn(0f, 1f) * total).toLong())
                        true
                    }
                } else {
                    disabled()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val y = size.height / 2f
            val stroke = SeekTrackHeight.toPx()
            val head = pillWidth / 2f + (size.width - pillWidth).coerceAtLeast(0f) * fraction
            drawLine(inactiveColor, Offset(head, y), Offset(size.width - stroke / 2f, y), stroke, StrokeCap.Round)
            drawLine(activeColor, Offset(stroke / 2f, y), Offset(head, y), stroke, StrokeCap.Round)
            fun at(share: Float) = pillWidth / 2f + (size.width - pillWidth).coerceAtLeast(0f) * share
            if (introShare > 0f) drawLine(skippedColor, Offset(stroke / 2f, y), Offset(at(introShare), y), stroke, StrokeCap.Round)
            if (outroShare > 0f) drawLine(skippedColor, Offset(at(1f - outroShare), y), Offset(size.width - stroke / 2f, y), stroke, StrokeCap.Round)
        }
        Text(
            time,
            Modifier.offset { IntOffset(((barWidth - pillWidth).coerceAtLeast(0) * fraction).roundToInt(), 0) }
                .onSizeChanged { pillWidth = it.width }
                .background(pillColor, CircleShape)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            color = pillTextColor,
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            maxLines = 1,
        )
    }
}

/** Seek back, previous episode, play/pause, next episode, seek forward. */
@Composable
private fun TransportControls(
    state: PlaybackState,
    queue: PlaybackQueueState,
    playback: PlaybackController,
    modifier: Modifier = Modifier,
) {
    val canChange = queue.currentTrackId != null && !queue.isLoading
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeekButton(forward = false, enabled = state.canSeek, onClick = playback::skipBack)
        EpisodeButton(forward = false, enabled = canChange, onClick = playback::previousEpisode)
        MainPlaybackButton(state, queue.isLoading, playback::toggle)
        EpisodeButton(forward = true, enabled = canChange && queue.hasNext, onClick = playback::nextEpisode)
        SeekButton(forward = true, enabled = state.canSeek, onClick = playback::skipForward)
    }
}

@Composable
private fun PlaybackErrors(state: PlaybackState, queue: PlaybackQueueState, onAccounts: () -> Unit) {
    val queueError = queue.errorCode
    val playError = state.errorCode
    if (queueError == null && playError == null) return
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (queueError != null) {
            Text(
                stringResource(playbackMessage(queueError)),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
        if (playError != null) {
            Text(stringResource(playbackMessage(playError)), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            if (playError in AccountErrors) TextButton(onAccounts) { Text(stringResource(R.string.account_manage)) }
        }
    }
}

/**
 * Queue, speed and sleep timer, each an icon over its label. Non-default speed and active timers are highlighted.
 */
@Composable
private fun ListeningTools(
    queue: PlaybackQueueState,
    speed: Float,
    sleep: SleepTimerState,
    onOpenQueue: () -> Unit,
    onOpenSpeed: () -> Unit,
    onOpenSleep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasQueue = queue.items.isNotEmpty()
    val sleepLabel = sleep.remainingMs?.let { formatDuration(it / 1000.0) }
        ?: stringResource(if (sleep.atEpisodeEnd) R.string.player_sleep_episode_end_short else R.string.player_sleep)
    Row(modifier.fillMaxWidth()) {
        ToolButton(Icons.AutoMirrored.Outlined.QueueMusic, stringResource(R.string.player_queue), onOpenQueue, enabled = hasQueue)
        ToolButton(
            Icons.Outlined.Speed,
            if (speed == 1f) stringResource(R.string.player_speed)
            else stringResource(R.string.player_speed_value, speedLabel(speed)),
            onOpenSpeed,
            highlighted = speed != 1f,
        )
        ToolButton(
            if (sleep.active) Icons.Filled.Timer else Icons.Outlined.Timer,
            sleepLabel,
            onOpenSleep,
            highlighted = sleep.active,
        )
    }
}

@Composable
private fun RowScope.ToolButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val color = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.38f)
        highlighted -> scheme.primary
        else -> scheme.onSurfaceVariant
    }
    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.clip(MaterialTheme.shapes.medium)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .widthIn(min = 72.dp)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, tint = color)
            Text(
                label, color = color, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EpisodeButton(forward: Boolean, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(48.dp), enabled = enabled) {
        Icon(
            if (forward) Icons.Filled.SkipNext else Icons.Filled.SkipPrevious,
            stringResource(if (forward) R.string.player_next_episode else R.string.player_previous_episode),
            Modifier.size(36.dp),
        )
    }
}

@Composable
private fun PlayerActionsSheet(
    onDismiss: () -> Unit,
    onOpenAlbum: () -> Unit,
    onOpenAnchor: () -> Unit,
    skip: AlbumSkip,
    onOpenSkip: () -> Unit,
    canGoToStart: Boolean,
    onGoToStart: () -> Unit,
) {
    val sheet = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }

    fun select(action: () -> Unit) {
        if (closing || sheet.targetValue == SheetValue.Hidden) return
        closing = true
        scope.launch {
            try {
                sheet.hide()
                if (!sheet.isVisible) {
                    onDismiss()
                    action()
                }
            } finally {
                closing = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = { WindowInsets.safeDrawing },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = XmLayout.PagePadding, end = XmLayout.PagePadding, bottom = XmLayout.ItemGap),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.player_more),
                Modifier.padding(horizontal = 8.dp).semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            MoreRow(Icons.Outlined.Album, R.string.player_view_album, { select(onOpenAlbum) })
            MoreRow(Icons.Outlined.Person, R.string.player_view_anchor, { select(onOpenAnchor) })
            MoreRow(
                Icons.Outlined.ContentCut, R.string.player_skip_title, { select(onOpenSkip) },
                supporting = if (skip.isOff) stringResource(R.string.player_skip_off)
                else stringResource(R.string.player_skip_value, skip.introSeconds, skip.outroSeconds),
                highlighted = !skip.isOff,
            )
            MoreRow(
                Icons.Filled.Replay, R.string.player_go_to_start, { select(onGoToStart) },
                enabled = canGoToStart, chevron = false,
            )
        }
    }
}

/** One entry of the 更多 sheet: a round icon, the title, an optional line under it, and a chevron when it opens something. */
@Composable
private fun MoreRow(
    icon: ImageVector,
    @StringRes title: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supporting: String? = null,
    highlighted: Boolean = false,
    chevron: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val faded = if (enabled) 1f else 0.38f
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(40.dp).background(colors.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colors.onSurface.copy(alpha = faded))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface.copy(alpha = faded))
            if (supporting != null) {
                Text(
                    supporting, style = MaterialTheme.typography.bodyMedium,
                    color = if (highlighted) colors.primary else colors.onSurfaceVariant,
                )
            }
        }
        if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant)
    }
}
@Composable
private fun SeekButton(forward: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val description = stringResource(
        if (forward) R.string.player_seek_forward else R.string.player_seek_back, PlaybackSkipSeconds,
    )
    IconButton(onClick, Modifier.size(SeekButtonSize).semantics { contentDescription = description }, enabled = enabled) {
        SeekGlyph(PlaybackSkipSeconds, forward, LocalContentColor.current)
    }
}

/**
 * A thin ring open on the side it seeks toward (left for back, right for forward), with an arrowhead pointing into the
 * gap and the interval centred inside. Forward mirrors the arc only, so the number still reads normally.
 */
@Composable
private fun SeekGlyph(seconds: Int, forward: Boolean, color: Color) {
    Box(Modifier.size(SeekGlyphSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val mirror = if (forward) -1f else 1f
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = size.width * 0.36f
            fun ring(deg: Float): Offset {
                val rad = Math.toRadians(deg.toDouble())
                return Offset(cx + mirror * radius * cos(rad).toFloat(), cy + radius * sin(rad).toFloat())
            }
            val steps = 48
            val arc = Path()
            for (i in 0..steps) {
                val p = ring(SeekArcStart + SeekArcSweep * i / steps)
                if (i == 0) arc.moveTo(p.x, p.y) else arc.lineTo(p.x, p.y)
            }
            drawPath(arc, color, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            // The arc ends at the upper edge of the gap; the arrowhead points along the direction of travel, into the gap.
            val endRad = Math.toRadians((SeekArcStart + SeekArcSweep).toDouble())
            val tip = ring(SeekArcStart + SeekArcSweep)
            val dir = Offset(mirror * sin(endRad).toFloat(), -cos(endRad).toFloat())
            val across = Offset(-dir.y, dir.x)
            val length = 4.dp.toPx()
            val half = 3.dp.toPx()
            val head = Path().apply {
                moveTo(tip.x + dir.x * length, tip.y + dir.y * length)
                lineTo(tip.x + across.x * half, tip.y + across.y * half)
                lineTo(tip.x - across.x * half, tip.y - across.y * half)
                close()
            }
            drawPath(head, color)
        }
        Text(
            "$seconds", Modifier.clearAndSetSemantics {},
            color = color, style = MaterialTheme.typography.labelMedium, fontSize = 10.sp, lineHeight = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MainPlaybackButton(state: PlaybackState, loadingQueue: Boolean, onClick: () -> Unit) {
    val retry = state.phase == PlaybackPhase.Failed
    val waitingForQueue = loadingQueue && !state.wantsToPlay
    val loading = state.phase == PlaybackPhase.Preparing || state.phase == PlaybackPhase.Buffering || waitingForQueue
    val label = stringResource(when {
        waitingForQueue -> R.string.action_cancel
        retry -> R.string.action_retry
        state.wantsToPlay -> R.string.action_pause
        else -> R.string.action_play
    })
    FilledIconButton(onClick, Modifier.size(72.dp).semantics { contentDescription = label }, shape = CircleShape) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp,
            )
        } else {
            val icon = when {
                retry -> Icons.Filled.Refresh
                state.wantsToPlay -> Icons.Filled.Pause
                else -> Icons.Filled.PlayArrow
            }
            Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp))
        }
    }
}

private val MinCover = 160.dp
private val MaxCover = 400.dp
private val CoverGap = 16.dp
private val CoverElevation = 12.dp
private val PlayerBottomPadding = 16.dp
private const val PausedCoverScale = 0.9f

private val SeekBarHeight = 40.dp
private val SeekTrackHeight = 3.dp

private val SeekButtonSize = 48.dp
private val SeekGlyphSize = 36.dp
// The gap is centred on 180 degrees (left) for back; forward mirrors it to the right.
private const val SeekArcStart = 150f
private const val SeekArcSweep = -300f

private val AccountErrors = setOf("login_required", "login_expired", "insufficient_quota", "usage_uncertain", "uncertain")

@StringRes
internal fun playbackMessage(code: String): Int = when (code) {
    "insufficient_quota" -> R.string.player_insufficient_quota
    "login_required" -> R.string.player_login_required
    "login_expired" -> R.string.account_expired
    "permission_denied", "preview_only" -> R.string.player_permission_denied
    "network", "network_error" -> R.string.error_network
    "rate_limited" -> R.string.error_rate_limited
    "catalogue_changed", "duplicate_page", "missing_track" -> R.string.player_queue_changed
    "catalogue_limit" -> R.string.player_queue_limit
    "usage_uncertain", "uncertain" -> R.string.account_uncertain
    "account_read_failed", "invalid_ledger" -> R.string.account_read_error
    else -> R.string.player_unavailable
}
