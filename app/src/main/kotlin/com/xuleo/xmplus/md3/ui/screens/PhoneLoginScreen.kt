package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.account.AccountViewModel
import com.xuleo.xmplus.md3.core.designsystem.XmPreview
import com.xuleo.xmplus.md3.core.designsystem.XmPreviews
import com.xuleo.xmplus.md3.ui.GeetestDialog
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private const val MinCodeLength = 4

@Composable
internal fun PhoneLoginScreen(accounts: AccountViewModel?, onBack: () -> Unit, onImport: () -> Unit) {
    val viewModel = accounts ?: return
    val session = viewModel.phone
    val accountState by viewModel.accounts.collectAsStateWithLifecycle()
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
        showPrimaryOption = accountState.hasAccounts,
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
    makePrimary: Boolean, showPrimaryOption: Boolean, busy: Boolean, errorCode: String?,
    onMobile: (String) -> Unit, onCode: (String) -> Unit, onPrimary: (Boolean) -> Unit,
    onSend: () -> Unit, onVerify: () -> Unit, onImport: () -> Unit, onBack: () -> Unit,
) {
    FormPage(Icons.Outlined.Smartphone, R.string.login_phone_title, R.string.login_phone_intro, onBack) {
        if (signInPending) {
            Text(stringResource(R.string.login_phone_verified), style = MaterialTheme.typography.bodyLarge)
        } else {
            OutlinedTextField(
                value = mobile, onValueChange = onMobile, modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large, label = { Text(stringResource(R.string.login_phone_number)) },
                leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null) },
                enabled = !busy && !awaitingCode, singleLine = true, isError = errorCode == "invalid_mobile",
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Phone),
            )
            // Once a code was sent, the code field takes over and resending moves inside it.
            if (!awaitingCode) PrimaryAction(stringResource(R.string.login_send), onSend, !busy && mobile.isNotEmpty())
        }
        if (awaitingCode) {
            OutlinedTextField(
                value = code, onValueChange = onCode, modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large, label = { Text(stringResource(R.string.login_phone_code)) },
                leadingIcon = { Icon(Icons.Outlined.Sms, contentDescription = null) },
                trailingIcon = { ResendButton(cooldownSeconds, enabled = !busy, onSend) },
                enabled = !busy, singleLine = true,
                supportingText = { Text(stringResource(R.string.login_phone_code_hint)) },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.NumberPassword),
            )
        }
        if (showPrimaryOption) PrimaryAccountOption(makePrimary, !busy, onPrimary)
        AccountOperationFeedback(busy, errorCode)
        if (awaitingCode || signInPending) {
            PrimaryAction(stringResource(R.string.login_phone_verify), onVerify,
                enabled = !busy && (signInPending || code.length >= MinCodeLength))
        }
        TextButton(onClick = onImport, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.login_use_info))
        }
    }
}

@Composable
private fun ResendButton(cooldownSeconds: Int, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled && cooldownSeconds == 0) {
        Text(if (cooldownSeconds > 0) stringResource(R.string.login_resend_in, cooldownSeconds)
            else stringResource(R.string.login_resend))
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
    PhoneLoginContent("13800001111", "", false, false, 0, true, true, false, null, {}, {}, {}, {}, {}, {}, {})
}

@XmPreviews
@Composable
private fun PhoneLoginCodePreview() = XmPreview {
    PhoneLoginContent("13800001111", "1234", true, false, 171, true, true, false, "sms_verify_rejected", {}, {}, {}, {}, {}, {}, {})
}