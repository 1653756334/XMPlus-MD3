package com.xuleo.xmplus.md3.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.ui.icons.XmIcons
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmPreview
import com.xuleo.xmplus.md3.core.designsystem.XmPreviews
import com.xuleo.xmplus.md3.core.media.PlaybackController
import com.xuleo.xmplus.md3.core.media.PlaybackPhase
import com.xuleo.xmplus.md3.core.media.PlaybackState
import com.xuleo.xmplus.md3.ui.components.Cover
import com.xuleo.xmplus.md3.ui.screens.playbackMessage

@Composable
fun MiniPlayer(
    playback: PlaybackController? = null,
    onOpen: () -> Unit = {},
    modifier: Modifier = Modifier,
    music: com.xuleo.xmplus.md3.core.media.MusicPlayback? = null,
    onQueue: () -> Unit = {},
) {
    val episodeState = playback?.state?.collectAsStateWithLifecycle()?.value ?: PlaybackState()
    val episodeQueue = playback?.queue?.collectAsStateWithLifecycle()?.value
    val songState = music?.state?.collectAsStateWithLifecycle()?.value
    // A song that is loaded wins: starting one stops 听书, and the other way round.
    val song = songState?.song
    val title = song?.title ?: episodeState.selection?.title
    val subtitle = song?.artist ?: episodeState.selection?.subtitle.orEmpty()
    val coverUrl = if (song != null) song.artwork else episodeState.selection?.coverUrl
    val active = song != null || episodeState.selection != null
    val phase = songState?.takeIf { song != null }?.phase ?: episodeState.phase
    val wantsToPlay = songState?.takeIf { song != null }?.wantsToPlay ?: episodeState.wantsToPlay
    val error = if (song != null) songState.errorCode else episodeState.errorCode ?: episodeQueue?.errorCode
    val loadingQueue = song == null && episodeQueue?.isLoading == true
    val toggle: () -> Unit = { if (song != null) music.toggle() else playback?.toggle() }
    val openLabel = stringResource(R.string.player_open)
    val cancelLabel = stringResource(R.string.action_cancel)
    Surface(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.End))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .clickable(enabled = active, onClickLabel = openLabel, role = Role.Button, onClick = onOpen)
                .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (coverUrl != null) Cover(coverUrl, Modifier.size(44.dp), RoundedCornerShape(12.dp))
            else Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = XmIcons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title ?: stringResource(R.string.mini_player_idle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (!active) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val supporting = if (error != null) stringResource(playbackMessage(error)) else subtitle
                if (supporting.isNotEmpty()) {
                    Text(
                        supporting, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = toggle, enabled = active) {
                val retry = phase == PlaybackPhase.Failed
                if (loadingQueue && !wantsToPlay) {
                    CircularProgressIndicator(Modifier.size(20.dp).semantics { contentDescription = cancelLabel }, strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = if (retry) XmIcons.Filled.Refresh else if (wantsToPlay) XmIcons.Filled.Pause else XmIcons.Filled.PlayArrow,
                        contentDescription = stringResource(if (retry) R.string.action_retry else if (wantsToPlay) R.string.action_pause else R.string.action_play),
                    )
                }
            }
            // Songs have a queue to open from here, as in QQ音乐; 听书 opens its list from the player.
            if (song != null) IconButton(onClick = onQueue) {
                Icon(XmIcons.Outlined.QueueMusic, stringResource(R.string.player_queue))
            }
        }
    }
}

@XmPreviews
@Composable
private fun MiniPlayerPreview() = XmPreview { MiniPlayer() }
