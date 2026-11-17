package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.account.*
import com.xuleo.xmplus.md3.core.designsystem.*
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountScaffold(title: Int, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(title)) }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back)) }
    }) }, contentWindowInsets = WindowInsets(0, 0, 0, 0), content = content)
}

@Composable
internal fun AccountsContent(
    state: AccountsState, busy: Boolean, errorCode: String?, onBack: () -> Unit, onImport: () -> Unit,
    onPrimary: (String) -> Unit, onRemove: (String) -> Unit, onRefresh: (String) -> Unit, onRetry: () -> Unit,
    onReimport: (String) -> Unit = {},
) {
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    val accounts = (state as? AccountsState.Ready)?.accounts.orEmpty()
    AccountScaffold(R.string.account_manage, onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = XmLayout.ContentWidth).fillMaxWidth(),
                contentPadding = PaddingValues(XmLayout.PagePadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { AccountOperationFeedback(busy, errorCode) }
                when (state) {
                    AccountsState.Loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    AccountsState.Unavailable -> item { AccountReadError(onRetry, busy) }
                    is AccountsState.Ready -> {
                        if (accounts.isEmpty()) item {
                            Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                AccountAvatar()
                                Text(stringResource(R.string.account_empty), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        items(accounts, key = { it.identity }) { account ->
                            AccountRow(account, busy, { onPrimary(account.identity) }, { removing = account.identity },
                                { onRefresh(account.identity) }, { onReimport(account.identity) })
                        }
                        item { Button(onClick = onImport, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.account_add))
                        } }
                    }
                }
            }
        }
    }
    val target = accounts.firstOrNull { it.identity == removing }
    if (target != null) AlertDialog(onDismissRequest = { removing = null },
        title = { Text(stringResource(R.string.account_remove_title)) },
        text = { Text(stringResource(R.string.account_remove_detail, accountName(target))) },
        confirmButton = { TextButton(onClick = { removing = null; onRemove(target.identity) }, enabled = !busy) {
            Text(stringResource(R.string.account_remove), color = MaterialTheme.colorScheme.error)
        } }, dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.action_cancel)) } })
}

@Composable
private fun AccountRow(account: AccountSummary, busy: Boolean, onPrimary: () -> Unit, onRemove: () -> Unit,
    onRefresh: () -> Unit, onImport: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            AccountAvatar()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(accountName(account), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (account.primary) Text(stringResource(R.string.account_primary), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.account_device, account.deviceHint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.account_quota_value, quotaText(account)), style = MaterialTheme.typography.bodyLarge)
                Text(if (account.expired) stringResource(R.string.account_expired) else checkedText(account),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (account.expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }, enabled = !busy) {
                    Icon(Icons.Outlined.MoreVert, stringResource(R.string.account_options, accountName(account)))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!account.primary) DropdownMenuItem(text = { Text(stringResource(R.string.account_make_primary)) },
                        onClick = { menu = false; onPrimary() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.account_refresh)) }, onClick = { menu = false; onRefresh() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.account_reimport)) }, onClick = { menu = false; onImport() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.account_remove), color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onRemove() })
                }
            }
        }
    }
}

@Composable
internal fun ImportAccountContent(
    draft: String, makePrimary: Boolean, busy: Boolean, errorCode: String?, onDraft: (String) -> Unit,
    onPrimary: (Boolean) -> Unit, onSubmit: () -> Unit, onBack: () -> Unit,
    recovery: Boolean = false, onPhoneLogin: () -> Unit = {},
) {
    var visible by remember { mutableStateOf(false) }
    AccountScaffold(if (recovery) R.string.account_reimport else R.string.account_import, onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = XmLayout.FormWidth).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(XmLayout.PagePadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.account_import_detail), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = draft, onValueChange = onDraft, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.account_login_info)) }, enabled = !busy,
                    minLines = 5, maxLines = 8, isError = errorCode != null,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                    trailingIcon = { IconButton(onClick = { visible = !visible }) {
                        Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            stringResource(if (visible) R.string.account_hide_info else R.string.account_show_info))
                    } })
                if (!recovery) PrimaryAccountOption(makePrimary, !busy, onPrimary)
                AccountOperationFeedback(busy, errorCode)
                Button(onClick = onSubmit, enabled = !busy && draft.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (busy) R.string.account_verifying else R.string.account_import))
                }
                Text(stringResource(R.string.account_import_privacy), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!recovery) TextButton(onClick = onPhoneLogin, enabled = !busy) { Text(stringResource(R.string.login_use_phone)) }
            }
        }
    }
}

@Composable
internal fun PrimaryAccountOption(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
        .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.account_make_primary), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun AccountReadError(onRetry: () -> Unit, busy: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.account_read_error), color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = onRetry, enabled = !busy) { Text(stringResource(R.string.action_retry)) }
    }
}

@Composable
internal fun AccountOperationFeedback(busy: Boolean, errorCode: String?) {
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (errorCode != null) Text(stringResource(when (errorCode) {
        "invalid_credentials" -> R.string.account_invalid_info
        "credential_mismatch" -> R.string.account_mismatched_info
        "input_too_long" -> R.string.account_input_too_long
        "login_expired", "login_required" -> R.string.account_expired
        "network", "network_error", "http_error" -> R.string.account_network_error
        "invalid_mobile" -> R.string.login_error_mobile
        "invalid_code" -> R.string.login_error_code
        "sms_verify_rejected" -> R.string.login_error_sms_verify
        "sms_send_rejected" -> R.string.login_error_sms_send
        "quick_login_rejected" -> R.string.login_error_quick
        "invalid_captcha", "captcha_reused" -> R.string.login_error_captcha
        "challenge_invalid", "challenge_expired" -> R.string.login_error_challenge
        "rate_limited", "sms_cooldown" -> R.string.account_rate_limited
        "invalid_quota", "invalid_json", "protocol", "response_too_large" -> R.string.account_verification_error
        "account_read_failed" -> R.string.account_read_error
        "account_write_failed" -> R.string.account_write_error
        else -> R.string.account_operation_error
    }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
internal fun accountName(account: AccountSummary): String = stringResource(R.string.account_name, account.uid)

@Composable
internal fun quotaText(account: AccountSummary): String {
    val seconds = account.quota.remainingSeconds
    val format = NumberFormat.getIntegerInstance()
    return if (seconds in 1..59) stringResource(R.string.account_quota_less_minute)
        else stringResource(R.string.account_quota_duration, format.format(seconds / 3600), format.format(seconds % 3600 / 60))
}

@Composable
internal fun checkedText(account: AccountSummary): String = stringResource(R.string.account_checked,
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(account.quota.checkedAt.toEpochMilliseconds())))

@XmPreviews
@Composable
private fun ImportPreview() = XmPreview { ImportAccountContent("", true, false, null, {}, {}, {}, {}) }
