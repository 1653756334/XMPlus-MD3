package com.xuleo.xmplus.md3.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.download.DownloadState
import com.xuleo.xmplus.md3.core.download.DownloadTask

/**
 * An episode's download control, the same wherever it appears: an arrow to download, a dashed arrow while waiting, a
 * ring that fills while downloading (tap to pause, tap again to go on), a check once downloaded, and a warning sign
 * after a failure (tap to try again). Every state is an outlined 24 dp glyph, so it sits evenly beside other icons.
 */
@Composable
fun DownloadButton(
    task: DownloadTask?,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val state = task?.state
    val description = stringResource(
        when (state) {
            null -> R.string.player_download
            DownloadState.Queued -> R.string.downloads_waiting
            DownloadState.Running -> R.string.downloads_pause
            DownloadState.Paused -> R.string.downloads_resume
            DownloadState.Failed -> R.string.downloads_retry
            DownloadState.Completed -> R.string.downloads_done
        },
    )
    val onClick = when (state) {
        null, DownloadState.Failed -> onDownload
        DownloadState.Queued, DownloadState.Running -> onPause
        DownloadState.Paused -> onResume
        DownloadState.Completed -> ({})
    }
    IconButton(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = description },
        enabled = state != DownloadState.Completed,
        colors = IconButtonDefaults.iconButtonColors(contentColor = tint, disabledContentColor = MaterialTheme.colorScheme.primary),
    ) {
        DownloadGlyph(task)
    }
}

/** The glyph [DownloadButton] shows, for places that show the state without acting on it. */
@Composable
fun DownloadGlyph(task: DownloadTask?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    when (task?.state) {
        null -> Icon(Icons.Outlined.Download, contentDescription = null, modifier)
        DownloadState.Queued -> Icon(Icons.Outlined.Downloading, contentDescription = null, modifier)
        DownloadState.Completed -> Icon(Icons.Outlined.DownloadDone, contentDescription = null, modifier, tint = scheme.primary)
        DownloadState.Failed -> Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier, tint = scheme.error)
        DownloadState.Running, DownloadState.Paused -> {
            val running = task.state == DownloadState.Running
            val total = task.totalBytes
            val fraction = if (total != null && total > 0) (task.bytes.toFloat() / total).coerceIn(0f, 1f) else null
            Box(modifier.size(24.dp), contentAlignment = Alignment.Center) {
                val ring = Modifier.size(RingSize)
                if (fraction == null && running) {
                    CircularProgressIndicator(ring, color = scheme.primary, strokeWidth = RingStroke, trackColor = scheme.outlineVariant)
                } else {
                    CircularProgressIndicator(
                        progress = { fraction ?: 0f }, modifier = ring,
                        color = if (running) scheme.primary else scheme.onSurfaceVariant,
                        strokeWidth = RingStroke, trackColor = scheme.outlineVariant, gapSize = 0.dp,
                    )
                }
                Icon(
                    if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null,
                    Modifier.size(12.dp), tint = if (running) scheme.primary else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Two dp of stroke on a 20 dp ring, matching the line weight of a 24 dp outlined icon. */
private val RingSize = 20.dp
private val RingStroke = 2.dp
