@file:OptIn(ExperimentalMaterial3Api::class)

package com.xuleo.xmplus.md3.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.media.PlaybackController
import com.xuleo.xmplus.md3.core.media.PlaybackPhase
import com.xuleo.xmplus.md3.core.media.PlaybackSelection
import com.xuleo.xmplus.md3.core.media.PlaybackSkipSeconds
import com.xuleo.xmplus.md3.core.media.PlaybackState
import com.xuleo.xmplus.md3.ui.components.Cover
import com.xuleo.xmplus.md3.ui.components.DetailArtworkBackdrop
import com.xuleo.xmplus.md3.ui.components.DetailTopBarHeight
import com.xuleo.xmplus.md3.ui.components.formatDuration
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
                Row(Modifier.fillMaxWidth().height(DetailTopBarHeight), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { closeThen() }, modifier = Modifier.padding(start = 4.dp)) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown, stringResource(R.string.player_minimize), Modifier.size(28.dp),
                        )
                    }
                }
                if (episode == null) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.mini_player_idle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    PlayerLayout(episode.coverUrl, Modifier.weight(1f).fillMaxWidth()) {
                        PlayerControls(
                            episode = episode,
                            state = state,
                            playback = playback,
                            onOpenAlbum = { closeThen { onOpenAlbum(episode.album.id) } },
                            onOpenAnchor = { closeThen { onOpenAnchor(episode.album.id, episode.album.anchorUid) } },
                            onAccounts = { closeThen(onAccounts) },
                        )
                    }
                }
            }
        }
    }
}

/** Fit portrait controls near the bottom without sacrificing a scrollable layout for large text or short screens. */
@Composable
private fun PlayerLayout(artwork: String?, modifier: Modifier = Modifier, controls: @Composable () -> Unit) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        val height = maxHeight
        val scroll = rememberScrollState()
        if (maxWidth >= 560.dp && maxWidth > height * 1.25f) {
            Row(
                Modifier.widthIn(max = 900.dp).fillMaxWidth().verticalScroll(scroll).heightIn(min = height)
                    .padding(start = 28.dp, end = 28.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val edge = minOf(320.dp, (height - 20.dp).coerceAtLeast(120.dp))
                    Cover(artwork, Modifier.size(edge), MaterialTheme.shapes.large)
                }
                Box(Modifier.weight(1f)) { controls() }
            }
        } else {
            val edge = minOf(380.dp, (maxWidth - 56.dp).coerceAtLeast(0.dp), (height * 0.56f).coerceAtLeast(120.dp))
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(scroll).heightIn(min = height)
                    .padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                Cover(artwork, Modifier.padding(bottom = 24.dp).size(edge), MaterialTheme.shapes.large)
                controls()
            }
        }
    }
}

@Composable
private fun PlayerControls(
    episode: PlaybackSelection,
    state: PlaybackState,
    playback: PlaybackController,
    onOpenAlbum: () -> Unit,
    onOpenAnchor: () -> Unit,
    onAccounts: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    episode.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (episode.subtitle.isNotBlank() && episode.subtitle != episode.title) {
                    Text(
                        episode.subtitle, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            PlayerMenu(onOpenAlbum, onOpenAnchor, canGoToStart = state.canSeek && state.positionMs > 0) {
                playback.seekTo(0)
            }
        }
        PositionControl(state, playback::seekTo)
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkipButton(forward = false, enabled = state.canSeek, onClick = playback::skipBack)
            MainPlaybackButton(state, playback::toggle)
            SkipButton(forward = true, enabled = state.canSeek, onClick = playback::skipForward)
        }
        state.errorCode?.let { error ->
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(playbackMessage(error)), color = MaterialTheme.colorScheme.error)
                if (error in AccountErrors) TextButton(onAccounts) { Text(stringResource(R.string.account_manage)) }
            }
        }
    }
}

@Composable
private fun PlayerMenu(
    onOpenAlbum: () -> Unit,
    onOpenAnchor: () -> Unit,
    canGoToStart: Boolean,
    onGoToStart: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreHoriz, stringResource(R.string.player_more))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.player_view_album)) },
                leadingIcon = { Icon(Icons.Outlined.Album, contentDescription = null) },
                onClick = { expanded = false; onOpenAlbum() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.player_view_anchor)) },
                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                onClick = { expanded = false; onOpenAnchor() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.player_go_to_start)) },
                leadingIcon = { Icon(Icons.Filled.Replay, contentDescription = null) },
                enabled = canGoToStart,
                onClick = { expanded = false; onGoToStart() },
            )
        }
    }
}

@Composable
private fun SkipButton(forward: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val description = stringResource(
        if (forward) R.string.player_seek_forward else R.string.player_seek_back, PlaybackSkipSeconds,
    )
    IconButton(
        onClick, Modifier.size(56.dp).semantics { contentDescription = description }, enabled = enabled,
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.Replay, contentDescription = null,
                modifier = Modifier.matchParentSize().graphicsLayer { scaleX = if (forward) -1f else 1f },
            )
            Text(
                PlaybackSkipSeconds.toString(),
                Modifier.align(Alignment.Center).padding(top = 4.dp).clearAndSetSemantics {},
                fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun MainPlaybackButton(state: PlaybackState, onClick: () -> Unit) {
    val retry = state.phase == PlaybackPhase.Failed
    val loading = state.phase == PlaybackPhase.Preparing || state.phase == PlaybackPhase.Buffering
    val label = stringResource(when {
        retry -> R.string.action_retry
        state.wantsToPlay -> R.string.action_pause
        else -> R.string.action_play
    })
    FilledIconButton(onClick, Modifier.size(68.dp).semantics { contentDescription = label }, shape = CircleShape) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp,
            )
        } else {
            val icon = when {
                retry -> Icons.Filled.Refresh
                state.wantsToPlay -> Icons.Filled.Pause
                else -> Icons.Filled.PlayArrow
            }
            Icon(icon, contentDescription = null, modifier = Modifier.size(34.dp))
        }
    }
}

@Composable
private fun PositionControl(state: PlaybackState, onSeek: (Long) -> Unit) {
    var dragging by remember(state.selection?.track?.id, state.canSeek) { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1).toFloat()
    val position = (dragging ?: state.positionMs.toFloat()).coerceIn(0f, duration)
    val slider = remember(duration) { SliderState(value = position, trackRange = 0f..duration) }
    slider.value = position
    val label = stringResource(R.string.player_progress)
    val scheme = MaterialTheme.colorScheme
    val thumb = if (state.canSeek) scheme.primary else scheme.outline
    val colors = SliderDefaults.colors(inactiveTrackColor = scheme.outlineVariant)
    val timeStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
    val touchHeight = 48.dp

    Column(Modifier.fillMaxWidth()) {
        // Keep the full touch target and slider semantics; only the painted track and thumb are small.
        Slider(
            state = slider,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek(it.toLong()) }
                dragging = null
            },
            enabled = state.canSeek,
            modifier = Modifier.fillMaxWidth().height(touchHeight).semantics { contentDescription = label },
            colors = colors,
            thumb = {
                Box(Modifier.height(touchHeight), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(8.dp).background(thumb, CircleShape))
                }
            },
            track = { trackState ->
                SliderDefaults.Track(
                    sliderState = trackState, modifier = Modifier.height(3.dp), enabled = state.canSeek, colors = colors,
                    drawStopIndicator = null, thumbTrackGapSize = 0.dp,
                )
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(position / 1000.0), style = timeStyle, color = scheme.onSurfaceVariant)
            Text(formatDuration(state.durationMs / 1000.0), style = timeStyle, color = scheme.onSurfaceVariant)
        }
    }
}

private val AccountErrors = setOf("login_required", "login_expired", "insufficient_quota", "usage_uncertain", "uncertain")

@StringRes
internal fun playbackMessage(code: String): Int = when (code) {
    "insufficient_quota" -> R.string.player_insufficient_quota
    "login_required" -> R.string.player_login_required
    "login_expired" -> R.string.account_expired
    "permission_denied", "preview_only" -> R.string.player_permission_denied
    "network", "network_error" -> R.string.error_network
    "rate_limited" -> R.string.error_rate_limited
    "usage_uncertain", "uncertain" -> R.string.account_uncertain
    "account_read_failed", "invalid_ledger" -> R.string.account_read_error
    else -> R.string.player_unavailable
}
