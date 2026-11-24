package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.ui.icons.XmIcons
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.media.HistoryUploadFeedback
import com.xuleo.xmplus.md3.core.media.HistoryUploader

@Composable
internal fun HistoryUploadSettings(uploader: HistoryUploader) {
    val state by uploader.state.collectAsStateWithLifecycle()
    var confirm by rememberSaveable { mutableStateOf(false) }
    val canChange = state.ready && !state.saving && !state.consentError
    ListItem(
        modifier = Modifier.toggleable(state.enabled, enabled = canChange, role = Role.Switch) { enabled ->
            if (enabled) confirm = true else uploader.setEnabled(false)
        },
        supportingContent = { Text(stringResource(if (state.enabled) R.string.history_upload_on_hint else R.string.history_upload_off_hint)) },
        leadingContent = { Icon(XmIcons.Outlined.CloudUpload, contentDescription = null) },
        trailingContent = { Switch(checked = state.enabled, onCheckedChange = null, enabled = canChange) },
    ) { Text(stringResource(R.string.history_upload_title)) }

    if (state.consentError) {
        ListItem(
            onClick = uploader::retryConsent,
            supportingContent = { Text(stringResource(R.string.history_upload_setting_error)) },
        ) { Text(stringResource(R.string.action_retry)) }
    } else if (state.enabled) {
        val feedback = when {
            state.sending -> R.string.history_upload_sending
            state.feedback == HistoryUploadFeedback.Uploaded -> R.string.history_upload_done
            state.feedback == HistoryUploadFeedback.Failed -> R.string.history_upload_failed
            state.feedback == HistoryUploadFeedback.SignIn -> R.string.history_upload_sign_in
            else -> R.string.history_upload_waiting
        }
        ListItem(
            onClick = uploader::retry,
            enabled = !state.sending,
            leadingContent = { Icon(XmIcons.Outlined.Sync, contentDescription = null) },
            supportingContent = { Text(stringResource(feedback)) },
        ) { Text(stringResource(R.string.history_upload_retry)) }
    }

    if (confirm && canChange && !state.enabled) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.history_upload_confirm_title)) },
            text = { Text(stringResource(R.string.history_upload_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { confirm = false; uploader.setEnabled(true) }) {
                    Text(stringResource(R.string.history_upload_enable))
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
