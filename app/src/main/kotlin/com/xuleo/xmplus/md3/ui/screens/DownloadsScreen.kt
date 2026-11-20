package com.xuleo.xmplus.md3.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.download.DownloadState
import com.xuleo.xmplus.md3.core.download.DownloadTask
import com.xuleo.xmplus.md3.download.DownloadsViewModel
import com.xuleo.xmplus.md3.download.downloadErrorMessage
import com.xuleo.xmplus.md3.ui.Destination

/** 下载: what is downloading now, then what is downloaded, grouped by album. */
@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    if (tasks.isEmpty()) {
        EmptyTab(Destination.Downloads.label, Destination.Downloads.icon)
        return
    }
    val active = tasks.filter { it.state != DownloadState.Completed }
    val finished = tasks.filter { it.state == DownloadState.Completed }.groupBy { it.albumId }
    LazyColumn(Modifier.fillMaxWidth()) {
        if (active.isNotEmpty()) {
            item(key = "active") {
                val anyRunning = active.any { it.state == DownloadState.Queued || it.state == DownloadState.Running }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.downloads_section_active), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = if (anyRunning) viewModel::pauseAll else viewModel::resumeAll) {
                        Text(stringResource(if (anyRunning) R.string.downloads_pause_all else R.string.downloads_resume_all))
                    }
                }
            }
            items(active, key = { "active-${it.trackId}" }) { task -> ActiveRow(task, viewModel) }
        }
        for ((albumId, group) in finished) {
            item(key = "album-$albumId") {
                Text(
                    group.first().albumTitle,
                    Modifier.padding(horizontal = XmLayout.PagePadding, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            items(group, key = { "done-${it.trackId}" }) { task -> FinishedRow(task, viewModel) }
        }
    }
}

@Composable
private fun ActiveRow(task: DownloadTask, viewModel: DownloadsViewModel) {
    val running = task.state == DownloadState.Queued || task.state == DownloadState.Running
    val (icon, label) = when {
        running -> Icons.Outlined.Pause to R.string.downloads_pause
        task.state == DownloadState.Failed -> Icons.Outlined.Refresh to R.string.downloads_retry
        else -> Icons.Outlined.PlayArrow to R.string.downloads_resume
    }
    ListItem(
        headlineContent = { Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(stateText(task), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (task.state == DownloadState.Running) {
                    val total = task.totalBytes
                    val bar = Modifier.fillMaxWidth().padding(top = 6.dp)
                    if (total != null && total > 0) {
                        LinearProgressIndicator(progress = { task.bytes.toFloat() / total }, modifier = bar)
                    } else {
                        LinearProgressIndicator(modifier = bar)
                    }
                }
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = { if (running) viewModel.pause(task.trackId) else viewModel.resume(task.trackId) }) {
                    Icon(icon, stringResource(label))
                }
                IconButton(onClick = { viewModel.cancel(task.trackId) }) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.downloads_cancel))
                }
            }
        },
    )
}

@Composable
private fun FinishedRow(task: DownloadTask, viewModel: DownloadsViewModel) {
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                Formatter.formatFileSize(context, task.bytes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            IconButton(onClick = { viewModel.delete(task.trackId) }) {
                Icon(Icons.Outlined.Delete, stringResource(R.string.downloads_delete))
            }
        },
    )
}

@Composable
private fun stateText(task: DownloadTask): String = when (task.state) {
    DownloadState.Queued -> stringResource(R.string.downloads_waiting)
    DownloadState.Running -> task.totalBytes?.takeIf { it > 0 }
        ?.let { stringResource(R.string.downloads_percent, (task.bytes * 100 / it).toInt()) }
        ?: stringResource(R.string.downloads_running)
    DownloadState.Paused -> stringResource(R.string.downloads_paused)
    DownloadState.Failed -> stringResource(downloadErrorMessage(task.error))
    DownloadState.Completed -> ""
}