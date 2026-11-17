package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.account.AccountViewModel
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.designsystem.XmPreview
import com.xuleo.xmplus.md3.core.designsystem.XmPreviews
import com.xuleo.xmplus.md3.ui.GeetestDialog
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private const val MinCodeLength = 4

@Composable
internal fun PhoneLoginScreen(accounts: AccountViewModel?, onBack: () -> Unit, onImport: () -> Unit) {
    val session = accounts?.phone ?: return
    // Cooldowns and challenge expiry are time based, so the screen re-reads them every second.
    val cooldown by produceState(0, session.mobile) {
        while (true) {
            value = session.cooldownSeconds
            session.expireStaleChallenge()
            delay(1.seconds)
        }
    }
    PhoneLoginContent(
        mobile = session.mobile, code = session.code, awaitingCode = session.awaitingCode,
        signInPending = session.signInPending, cooldownSeconds = cooldown, makePrimary = session.makePrimary,
        busy = session.busy, errorCode = session.errorCode,
        onMobile = session::updateMobile, onCode = session::updateCode, onPrimary = { session.makePrimary = it },
        onSend = session::startCheck, onVerify = session::verify, onImport = onImport, onBack = onBack,
    )
    if (session.checking) GeetestDialog(onProof = session::onProof, onDismiss = session::onCheckClosed)
    if (session.pendingProof != null) SendConfirmation(session.mobile.takeLast(4), session::confirmSend, session::cancelSend)
}

@Composable
internal fun PhoneLoginContent(
    mobile: String, code: String, awaitingCode: Boolean, signInPending: Boolean, cooldownSeconds: Int,
    makePrimary: Boolean, busy: Boolean, errorCode: String?,
    onMobile: (String) -> Unit, onCode: (String) -> Unit, onPrimary: (Boolean) -> Unit,
    onSend: () -> Unit, onVerify: () -> Unit, onImport: () -> Unit, onBack: () -> Unit,
) {
    AccountScaffold(R.string.login_phone_title, onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = XmLayout.FormWidth).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(XmLayout.PagePadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.login_phone_intro), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (signInPending) {
                    Text(stringResource(R.string.login_phone_verified), style = MaterialTheme.typography.bodyLarge)
                } else {
                    OutlinedTextField(
                        value = mobile, onValueChange = onMobile, modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.login_phone_number)) },
                        enabled = !busy && !awaitingCode, singleLine = true,
                        isError = errorCode == "invalid_mobile",
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Phone),
                    )
                    val sendLabel = if (cooldownSeconds > 0) stringResource(R.string.login_resend_in, cooldownSeconds)
                    else stringResource(R.string.login_send)
                    // Once a code was sent, signing in is the main action and resending is secondary.
                    if (awaitingCode) {
                        OutlinedButton(onClick = onSend, enabled = !busy && cooldownSeconds == 0, modifier = Modifier.fillMaxWidth()) { Text(sendLabel) }
                    } else {
                        Button(onClick = onSend, enabled = !busy && cooldownSeconds == 0 && mobile.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(sendLabel) }
                    }
                }
                if (awaitingCode) {
                    OutlinedTextField(
                        value = code, onValueChange = onCode, modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.login_phone_code)) }, enabled = !busy, singleLine = true,
                        supportingText = { Text(stringResource(R.string.login_phone_code_hint)) },
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.NumberPassword),
                    )
                }
                PrimaryAccountOption(makePrimary, !busy, onPrimary)
                AccountOperationFeedback(busy, errorCode)
                if (awaitingCode || signInPending) {
                    Button(onClick = onVerify, enabled = !busy && (signInPending || code.length >= MinCodeLength), modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.login_phone_verify))
                    }
                }
                TextButton(onClick = onImport, enabled = !busy) { Text(stringResource(R.string.login_use_info)) }
            }
        }
    }
}

@Composable
private fun SendConfirmation(lastDigits: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.login_send_confirm_title)) },
        text = { Text(stringResource(R.string.login_send_confirm_detail, lastDigits)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_send)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@XmPreviews
@Composable
private fun PhoneLoginPreview() = XmPreview {
    PhoneLoginContent("13800001111", "", false, false, 0, true, false, null, {}, {}, {}, {}, {}, {}, {})
}

@XmPreviews
@Composable
private fun PhoneLoginCodePreview() = XmPreview {
    PhoneLoginContent("13800001111", "1234", true, false, 171, true, false, "sms_verify_rejected", {}, {}, {}, {}, {}, {}, {})
}