@file:OptIn(ExperimentalMaterial3Api::class)

package com.xuleo.xmplus.md3.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
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
        var showActions by remember(episode?.album?.id, episode?.track?.id) { mutableStateOf(false) }
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
                Row(
                    Modifier.fillMaxWidth().height(DetailTopBarHeight).padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { closeThen() }) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown, stringResource(R.string.player_minimize), Modifier.size(28.dp),
                        )
                    }
                    if (episode != null) {
                        IconButton(onClick = { showActions = true }) {
                            Icon(Icons.Filled.MoreHoriz, stringResource(R.string.player_more))
                        }
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
                            onAccounts = { closeThen(onAccounts) },
                        )
                    }
                }
            }
        }
        if (showActions && episode != null) {
            PlayerActionsSheet(
                onDismiss = { showActions = false },
                onOpenAlbum = { closeThen { onOpenAlbum(episode.album.id) } },
                onOpenAnchor = { closeThen { onOpenAnchor(episode.album.id, episode.album.anchorUid) } },
                canGoToStart = state.canSeek && state.positionMs > 0,
                onGoToStart = {
                    // Playback may have changed while the sheet was closing.
                    if (playback.state.value.selection == episode) playback.seekTo(0)
                },
            )
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
    onAccounts: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        PositionControl(state, playback::seekTo)
        Row(
            Modifier.align(Alignment.CenterHorizontally).widthIn(max = 264.dp).fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkipButton(forward = false, enabled = state.canSeek, modifier = Modifier.weight(1f), onClick = playback::skipBack)
            MainPlaybackButton(state, playback::toggle)
            SkipButton(forward = true, enabled = state.canSeek, modifier = Modifier.weight(1f), onClick = playback::skipForward)
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
private fun PlayerActionsSheet(
    onDismiss: () -> Unit,
    onOpenAlbum: () -> Unit,
    onOpenAnchor: () -> Unit,
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerActionButton(R.string.player_view_album, Icons.Outlined.Album, Modifier.weight(1f)) {
                    select(onOpenAlbum)
                }
                PlayerActionButton(R.string.player_view_anchor, Icons.Outlined.Person, Modifier.weight(1f)) {
                    select(onOpenAnchor)
                }
                PlayerActionButton(
                    R.string.player_go_to_start, Icons.Filled.Replay, Modifier.weight(1f), enabled = canGoToStart,
                ) {
                    select(onGoToStart)
                }
            }
        }
    }
}

@Composable
private fun PlayerActionButton(
    @StringRes label: Int,
    icon: ImageVector,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    TextButton(
        onClick, modifier, enabled = enabled,
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurface),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val iconBackground = colors.surfaceContainerHighest.let { if (enabled) it else it.copy(alpha = 0.5f) }
            Box(Modifier.size(48.dp).background(iconBackground, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null)
            }
            Text(stringResource(label), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SkipButton(forward: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val description = stringResource(
        if (forward) R.string.player_seek_forward else R.string.player_seek_back, PlaybackSkipSeconds,
    )
    TextButton(
        onClick,
        modifier.heightIn(min = 56.dp).semantics { contentDescription = description },
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(
                if (forward) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                contentDescription = null, modifier = Modifier.size(24.dp),
            )
            Text(
                stringResource(R.string.player_skip_interval, PlaybackSkipSeconds), Modifier.clearAndSetSemantics {},
                style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
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
