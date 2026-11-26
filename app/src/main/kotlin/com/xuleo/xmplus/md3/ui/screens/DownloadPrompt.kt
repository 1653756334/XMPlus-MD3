package com.xuleo.xmplus.md3.ui.screens

import com.xuleo.xmplus.md3.ui.components.rememberShowMessage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.ui.icons.XmIcons
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.Track
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.download.DownloadActions
import com.xuleo.xmplus.md3.download.DownloadPlan
import com.xuleo.xmplus.md3.download.downloadErrorMessage
import java.text.NumberFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What a download request ended with, shown once as a toast. */
internal sealed interface DownloadNotice {
    data class Added(val count: Int) : DownloadNotice
    data object AlreadyListed : DownloadNotice
    data class Failed(val code: String) : DownloadNotice
}

/**
 * Starts downloads from a screen. The episodes are priced first; when any of them cost listening time the listener sees
 * how much and agrees before anything is queued. Free episodes are queued straight away.
 */
@Stable
internal class DownloadPrompt(private val actions: DownloadActions, private val scope: CoroutineScope) {
    /** Lookups done and to do while the cost is worked out; null when nothing is being priced. */
    var checking by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    /** A plan that costs listening time, waiting for the listener's answer. */
    var pending by mutableStateOf<DownloadPlan?>(null)
        private set

    var notice by mutableStateOf<DownloadNotice?>(null)
        internal set

    private var job: Job? = null
    private var onQueued: () -> Unit = {}

    /** Prices what [tracks] returns. [queued] runs once the episodes are in the download list. */
    fun request(album: Album, albumTotal: Int, queued: () -> Unit = {}, tracks: suspend () -> List<Track>) {
        if (job?.isActive == true || pending != null) return
        job = scope.launch {
            checking = 0 to 0
            try {
                val plan = actions.plan(album, albumTotal, tracks()) { done, total -> checking = done to total }
                when {
                    plan.episodes.isEmpty() -> {
                        notice = DownloadNotice.AlreadyListed
                        queued()
                    }
                    plan.totalSeconds == 0L -> {
                        notice = DownloadNotice.Added(actions.start(plan))
                        queued()
                    }
                    else -> {
                        onQueued = queued
                        pending = plan
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: XmlyException) {
                notice = DownloadNotice.Failed(error.code)
            } catch (_: Exception) {
                notice = DownloadNotice.Failed("unknown")
            } finally {
                checking = null
            }
        }
    }

    fun cancelCheck() {
        job?.cancel()
        checking = null
    }

    fun confirm() {
        val plan = pending ?: return
        val queued = onQueued
        pending = null
        onQueued = {}
        scope.launch {
            notice = try {
                DownloadNotice.Added(actions.start(plan)).also { queued() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                DownloadNotice.Failed("unknown")
            }
        }
    }

    fun dismiss() {
        pending = null
        onQueued = {}
    }
}

@Composable
internal fun rememberDownloadPrompt(actions: DownloadActions?): DownloadPrompt? {
    val scope = rememberCoroutineScope()
    return remember(actions, scope) { actions?.let { DownloadPrompt(it, scope) } }
}

/** The prompt's dialogs and toasts. Place it once on the screen that owns the prompt. */
@Composable
internal fun DownloadPromptHost(prompt: DownloadPrompt?) {
    if (prompt == null) return
    val showMessage = rememberShowMessage()
    val notice = prompt.notice
    val message = when (notice) {
        is DownloadNotice.Added -> pluralStringResource(R.plurals.downloads_added, notice.count, notice.count)
        DownloadNotice.AlreadyListed -> stringResource(R.string.downloads_already_listed)
        is DownloadNotice.Failed -> stringResource(downloadErrorMessage(notice.code))
        null -> null
    }
    LaunchedEffect(notice) {
        if (message == null) return@LaunchedEffect
        showMessage(message)
        prompt.notice = null
    }
    prompt.checking?.let { (done, total) -> CheckingDialog(done, total, prompt::cancelCheck) }
    prompt.pending?.let { plan -> ConfirmDialog(plan, prompt::confirm, prompt::dismiss) }
}

/** Shown only when pricing takes a moment, so a single free episode never flashes a dialog. */
@Composable
private fun CheckingDialog(done: Int, total: Int, onCancel: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(CheckingDialogDelayMs)
        shown = true
    }
    if (!shown) return
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.download_checking_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, Modifier.fillMaxWidth())
                Text(stringResource(R.string.download_checking, done, total), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** How many episodes, how many of them cost listening time and how much, the balance, and what happens on failure. */
@Composable
private fun ConfirmDialog(plan: DownloadPlan, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val count = plan.episodes.size
    val paid = plan.paidCount
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(XmIcons.Outlined.Download, contentDescription = null) },
        title = { Text(pluralStringResource(R.plurals.download_confirm_title, count, count)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    pluralStringResource(
                        R.plurals.download_confirm_cost, paid, paid, listeningTimeText(plan.totalSeconds),
                        NumberFormat.getIntegerInstance().format(plan.totalSeconds),
                    ),
                )
                plan.balanceSeconds?.let { Text(stringResource(R.string.download_confirm_balance, listeningTimeText(it)), color = muted) }
                if (plan.skipped > 0) {
                    Text(pluralStringResource(R.plurals.download_confirm_skipped, plan.skipped, plan.skipped), color = muted)
                }
                Text(stringResource(R.string.download_confirm_rules), style = MaterialTheme.typography.bodySmall, color = muted)
                if (!plan.affordable) Text(stringResource(R.string.download_confirm_short), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = plan.affordable) { Text(stringResource(R.string.download_confirm_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val CheckingDialogDelayMs = 400L
