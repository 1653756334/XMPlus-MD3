package com.xuleo.xmplus.md3.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.media.PlaybackController
import com.xuleo.xmplus.md3.core.media.PlaybackPhase
import com.xuleo.xmplus.md3.core.media.PlaybackState
import com.xuleo.xmplus.md3.ui.components.Cover
import com.xuleo.xmplus.md3.ui.components.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerScreen(playback: PlaybackController, onBack: () -> Unit, onAccounts: () -> Unit) {
    val state by playback.state.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.player_title)) },
                navigationIcon = {
                    IconButton(onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { insets ->
        val episode = state.selection
        if (episode == null) {
            Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.mini_player_idle), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 520.dp).verticalScroll(rememberScrollState())
                        .padding(horizontal = XmLayout.PagePadding, vertical = XmLayout.SectionGap),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(XmLayout.SectionGap),
                ) {
                    Cover(
                        episode.coverUrl, Modifier.widthIn(max = 340.dp).fillMaxWidth().aspectRatio(1f),
                        MaterialTheme.shapes.extraLarge,
                    )
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(episode.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        if (episode.subtitle.isNotBlank()) {
                            Text(
                                episode.subtitle, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    PositionControl(state, playback::seekTo)
                    Row(
                        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton({ playback.seekBy(-15_000L) }, enabled = state.canSeek) {
                            Icon(Icons.Filled.FastRewind, stringResource(R.string.player_rewind))
                            Text(stringResource(R.string.player_seek_back))
                        }
                        val retry = state.phase == PlaybackPhase.Failed
                        FilledIconButton(playback::toggle, Modifier.size(64.dp)) {
                            Icon(
                                if (retry) Icons.Filled.Refresh else if (state.wantsToPlay) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                stringResource(if (retry) R.string.action_retry else if (state.wantsToPlay) R.string.action_pause else R.string.action_play),
                                Modifier.size(32.dp),
                            )
                        }
                        TextButton({ playback.seekBy(30_000L) }, enabled = state.canSeek) {
                            Icon(Icons.Filled.FastForward, stringResource(R.string.player_fast_forward))
                            Text(stringResource(R.string.player_seek_forward))
                        }
                    }
                    when (state.phase) {
                        PlaybackPhase.Preparing, PlaybackPhase.Buffering -> CircularProgressIndicator(Modifier.size(24.dp))
                        PlaybackPhase.Failed -> state.errorCode?.let { error ->
                            Text(stringResource(playbackMessage(error)), color = MaterialTheme.colorScheme.error)
                            if (error in AccountErrors) TextButton(onAccounts) { Text(stringResource(R.string.account_manage)) }
                        }
                        else -> Unit
                    }
                }
            }
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
    Column(Modifier.fillMaxWidth()) {
        Slider(
            state = slider, onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek(it.toLong()) }
                dragging = null
            },
            enabled = state.canSeek,
            modifier = Modifier.semantics { contentDescription = label },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(position / 1000.0), style = MaterialTheme.typography.labelMedium)
            Text(formatDuration(state.durationMs / 1000.0), style = MaterialTheme.typography.labelMedium)
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
