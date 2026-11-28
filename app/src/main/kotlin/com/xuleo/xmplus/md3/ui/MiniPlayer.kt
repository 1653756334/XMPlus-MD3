package com.xuleo.xmplus.md3.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmPreview
import com.xuleo.xmplus.md3.core.designsystem.XmPreviews
import com.xuleo.xmplus.md3.core.media.PlaybackController
import com.xuleo.xmplus.md3.core.media.PlaybackPhase
import com.xuleo.xmplus.md3.core.media.PlaybackState
import com.xuleo.xmplus.md3.ui.components.Cover
import com.xuleo.xmplus.md3.ui.icons.XmIcons
import com.xuleo.xmplus.md3.ui.screens.playbackMessage
import com.xuleo.xmplus.md3.ui.screens.rememberAnimationsEnabled
import kotlin.math.abs
import kotlinx.coroutines.launch

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
    // Swiping left plays the next episode or song, swiping right the previous one, as QQ音乐 and 喜马拉雅 do. A previous
    // one that is already under way starts again first, the way a player's previous key works.
    val restartThreshold = MiniPlayerRestartMs
    val episodePosition = episodeState.positionMs
    val songPosition = songState?.positionMs ?: 0L
    val swipe: (Boolean) -> Unit = { forward ->
        if (song != null) {
            if (forward) music?.next() else {
                if (songPosition > restartThreshold) music?.seekTo(0) else music?.previous()
            }
        } else if (forward) playback?.nextEpisode() else {
            if (episodePosition > restartThreshold) playback?.seekTo(0) else playback?.previousEpisode()
        }
    }
    val openLabel = stringResource(R.string.player_open)
    val cancelLabel = stringResource(R.string.action_cancel)
    val switch: (Boolean) -> Unit = { forward -> if (active) swipe(forward) }
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
                .swipeToSkip(onSwipe = switch)
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
            // The two buttons sit together at the end: the queue keeps the edge, play moves over to it.
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                // The queue opens from here for both kinds, as in QQ音乐 and 喜马拉雅.
                if (active) IconButton(onClick = onQueue) {
                    Icon(XmIcons.Outlined.QueueMusic, stringResource(R.string.player_queue))
                }
            }
        }
    }
}

/**
 * A horizontal drag on the mini player: released past [SwipeThreshold] to the left calls [onSwipe] with true, to the
 * right with false. The row follows the finger, slides out when the swipe counts and the one that replaces it comes in
 * from the other side; a shorter drag springs back. A vertical drag over the bar is left to the page behind it.
 *
 * The gesture is keyed on nothing: the player publishes its position four times a second, and a key that changed with it
 * would throw the drag away halfway and leave the row standing off to one side.
 */
@Composable
private fun Modifier.swipeToSkip(onSwipe: (Boolean) -> Unit): Modifier {
    val animated = rememberAnimationsEnabled()
    val motion = MaterialTheme.motionScheme
    val threshold = with(LocalDensity.current) { SwipeThreshold.toPx() }
    val scope = rememberCoroutineScope()
    val latestSwipe by rememberUpdatedState(onSwipe)
    val latestMotion by rememberUpdatedState(motion)
    // What the row has been dragged by, and the part of it that is settling on its own once the finger is gone.
    var drag by remember { mutableFloatStateOf(0f) }
    val settle = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }
    val latestWidth by rememberUpdatedState(width)

    // Whichever job is newest owns the row; the ones before it may be cancelled and must not move it again.
    var generation by remember { mutableIntStateOf(0) }
    fun settleWith(body: suspend () -> Unit) {
        val mine = ++generation
        scope.launch {
            try {
                body()
            } finally {
                // However it ends, cancelled or not, the row comes back to where it belongs.
                if (mine == generation) runCatching { settle.snapTo(0f) }
            }
        }
    }

    return this
        .onSizeChanged { width = it.width }
        .graphicsLayer {
            val offset = drag + settle.value
            translationX = offset
            // Fades as it travels, so a swipe that ends up changing nothing still reads as the same row.
            val travel = if (width > 0) abs(offset) / width else 0f
            alpha = 1f - travel.coerceIn(0f, 1f) * SwipeFade
        }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = {
                    // Picks up from wherever a settle left it, so the row never jumps under the finger.
                    drag += settle.value
                    scope.launch { runCatching { settle.snapTo(0f) } }
                },
                onDragCancel = {
                    drag = 0f
                    settleWith { settle.animateTo(0f, latestMotion.defaultEffectsSpec()) }
                },
                onDragEnd = {
                    val travelled = drag
                    drag = 0f
                    val direction = when {
                        travelled < -threshold -> -1f
                        travelled > threshold -> 1f
                        else -> 0f
                    }
                    val distance = if (latestWidth > 0) latestWidth.toFloat() else threshold * 4
                    settleWith {
                        settle.snapTo(travelled)
                        when {
                            // Too short to count: back where it was.
                            direction == 0f || !animated -> {
                                settle.animateTo(0f, latestMotion.defaultEffectsSpec())
                                if (direction != 0f) latestSwipe(direction < 0)
                            }
                            else -> {
                                settle.animateTo(direction * distance, latestMotion.fastEffectsSpec())
                                latestSwipe(direction < 0)
                                // The next one enters from the side the swipe was heading for.
                                settle.snapTo(-direction * distance * SwipeEnterFraction)
                                settle.animateTo(0f, latestMotion.defaultEffectsSpec())
                            }
                        }
                    }
                },
            ) { change, amount ->
                change.consume()
                drag += amount
            }
        }
}

/** From where a previous episode or song starts again instead of going back, as the system's player keys do. */
private const val MiniPlayerRestartMs = 3_000L

/** How far a finger has to travel to change what is playing. */
private val SwipeThreshold = 64.dp

/** How far the row has faded by the time it has been dragged its own width. */
private const val SwipeFade = 0.7f

/** How much of the row's width the next one comes in from, so it arrives quickly rather than after a whole width. */
private const val SwipeEnterFraction = 0.4f

@XmPreviews
@Composable
private fun MiniPlayerPreview() = XmPreview { MiniPlayer() }
