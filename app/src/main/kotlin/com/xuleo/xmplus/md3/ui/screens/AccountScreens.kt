package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.clickable
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.ui.icons.XmIcons
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.account.*
import com.xuleo.xmplus.md3.core.designsystem.*
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountScaffold(title: Int, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(title)) }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(XmIcons.Outlined.ArrowBack, stringResource(R.string.action_back)) }
    }) }, contentWindowInsets = WindowInsets(0, 0, 0, 0), content = content)
}

@Composable
internal fun AccountsContent(
    state: AccountsState, busy: Boolean, errorCode: String?, onBack: () -> Unit, onImport: () -> Unit,
    onPrimary: (String) -> Unit, onRemove: (String) -> Unit, onRefresh: (String) -> Unit, onRetry: () -> Unit,
    onReimport: (String) -> Unit = {}, onClaim: (String) -> Unit = {}, onResume: (String) -> Unit = {},
    onAutoClaim: (Boolean) -> Unit = {}, onImportBackup: () -> Unit = {}, avatars: Map<String, String> = emptyMap(),
) {
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    var rewardPrompt by rememberSaveable { mutableStateOf<RewardPrompt?>(null) }
    var rewardTarget by rememberSaveable { mutableStateOf<String?>(null) }
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
                        if (accounts.isNotEmpty()) item {
                            SwitchRow(R.string.reward_auto, R.string.reward_auto_summary, state.autoClaim, !busy) {
                                // Turning it on needs a deliberate yes; turning it off never does.
                                if (it) rewardPrompt = RewardPrompt.AutoClaim else onAutoClaim(false)
                            }
                        }
                        items(accounts, key = { it.identity }) { account ->
                            AccountRow(account, avatars[account.identity], busy, { onPrimary(account.identity) }, { removing = account.identity },
                                { onRefresh(account.identity) }, { onReimport(account.identity) },
                                { rewardTarget = account.identity; rewardPrompt = RewardPrompt.Claim },
                                { rewardTarget = account.identity; rewardPrompt = RewardPrompt.Resume })
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onImport, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                    Icon(XmIcons.Outlined.Add, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.account_add))
                                }
                                OutlinedButton(onClick = onImportBackup, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                    Icon(XmIcons.Outlined.FileOpen, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.archive_import))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    rewardPrompt?.let { prompt ->
        val account = accounts.firstOrNull { it.identity == rewardTarget }
        if (prompt == RewardPrompt.AutoClaim || account != null) RewardConfirmation(prompt, account?.uid.orEmpty(),
            onConfirm = {
                rewardPrompt = null
                when (prompt) {
                    RewardPrompt.AutoClaim -> onAutoClaim(true)
                    RewardPrompt.Claim -> onClaim(requireNotNull(account).identity)
                    RewardPrompt.Resume -> onResume(requireNotNull(account).identity)
                }
            }, onDismiss = { rewardPrompt = null })
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
private fun AccountRow(account: AccountSummary, avatar: String?, busy: Boolean, onPrimary: () -> Unit, onRemove: () -> Unit,
    onRefresh: () -> Unit, onImport: () -> Unit, onClaim: () -> Unit, onResume: () -> Unit) {
    var actions by remember { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            AccountAvatar(avatar)
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
                if (account.rewardPaused) Text(stringResource(R.string.reward_paused), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            IconButton(onClick = { actions = true }, enabled = !busy) {
                Icon(XmIcons.Outlined.MoreVert, stringResource(R.string.account_options, accountName(account)))
            }
        }
    }
    if (actions) AccountActions(account, onDismiss = { actions = false }, onPrimary, onRefresh, onImport, onClaim, onResume, onRemove)
}

/** The per-account actions, as a bottom sheet: a short list reads better there than in a small popup menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountActions(account: AccountSummary, onDismiss: () -> Unit, onPrimary: () -> Unit, onRefresh: () -> Unit,
    onImport: () -> Unit, onClaim: () -> Unit, onResume: () -> Unit, onRemove: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    // Slide the sheet away first so the chosen action does not start under a half-closed sheet.
    fun choose(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss(); action() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(accountName(account), Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
        if (!account.primary) AccountAction(XmIcons.Outlined.Star, R.string.account_make_primary) { choose(onPrimary) }
        AccountAction(XmIcons.Outlined.Refresh, R.string.account_refresh) { choose(onRefresh) }
        if (account.rewardPaused) AccountAction(XmIcons.Outlined.PlayArrow, R.string.reward_resume) { choose(onResume) }
        else AccountAction(XmIcons.Outlined.Redeem, R.string.reward_claim) { choose(onClaim) }
        AccountAction(XmIcons.Outlined.Key, R.string.account_reimport) { choose(onImport) }
        AccountAction(XmIcons.Outlined.Delete, R.string.account_remove, destructive = true) { choose(onRemove) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AccountAction(icon: ImageVector, @StringRes label: Int, destructive: Boolean = false, onClick: () -> Unit) {
    val colors = if (destructive) {
        val error = MaterialTheme.colorScheme.error
        ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = error, leadingIconColor = error)
    } else {
        ListItemDefaults.colors(containerColor = Color.Transparent)
    }
    ListItem(
        headlineContent = { Text(stringResource(label)) }, leadingContent = { Icon(icon, contentDescription = null) },
        colors = colors, modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
internal fun ImportAccountContent(
    draft: String, makePrimary: Boolean, busy: Boolean, errorCode: String?, onDraft: (String) -> Unit,
    onPrimary: (Boolean) -> Unit, onSubmit: () -> Unit, onBack: () -> Unit,
    recovery: Boolean = false, showPrimaryOption: Boolean = true, onPhoneLogin: () -> Unit = {},
) {
    var visible by remember { mutableStateOf(false) }
    FormPage(XmIcons.Outlined.Key, if (recovery) R.string.account_reimport else R.string.account_import,
        R.string.account_import_detail, onBack) {
        OutlinedTextField(value = draft, onValueChange = onDraft, modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large, label = { Text(stringResource(R.string.account_login_info)) },
            enabled = !busy, minLines = 5, maxLines = 8, isError = errorCode != null,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
            trailingIcon = { IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) XmIcons.Outlined.VisibilityOff else XmIcons.Outlined.Visibility,
                    stringResource(if (visible) R.string.account_hide_info else R.string.account_show_info))
            } })
        if (!recovery && showPrimaryOption) PrimaryAccountOption(makePrimary, !busy, onPrimary)
        AccountOperationFeedback(busy, errorCode)
        PrimaryAction(stringResource(if (busy) R.string.account_verifying else R.string.account_import), onSubmit,
            enabled = !busy && draft.isNotBlank())
        Text(stringResource(R.string.account_import_privacy), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!recovery) TextButton(onClick = onPhoneLogin, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.login_use_phone))
        }
    }
}

/** What importing a backup will do, shown before anything changes. */
@Composable
internal fun ArchiveDialog(contents: ArchiveContents, busy: Boolean, errorCode: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.archive_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (contents.accounts.isEmpty()) {
                    Text(stringResource(R.string.archive_empty))
                } else {
                    Text(stringResource(R.string.archive_accounts, contents.accounts.size, contents.loginCount))
                    Text(stringResource(R.string.archive_keep))
                    Text(stringResource(R.string.archive_history))
                    if (contents.loginCount == 0) Text(stringResource(R.string.archive_no_login))
                }
                if (contents.hasSettings || contents.hasLibrary || contents.hasDownloads) Text(stringResource(R.string.archive_skipped))
                if (contents.hasSettings && contents.accounts.isNotEmpty()) Text(stringResource(R.string.archive_auto_claim))
                AccountOperationFeedback(busy, errorCode)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !busy && contents.accounts.isNotEmpty()) { Text(stringResource(R.string.archive_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.action_cancel)) } })
}

private enum class RewardPrompt { Claim, Resume, AutoClaim }

@Composable
private fun RewardConfirmation(prompt: RewardPrompt, uid: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val (title, detail, action) = when (prompt) {
        RewardPrompt.Claim -> Triple(R.string.reward_claim_title, stringResource(R.string.reward_claim_detail, uid), R.string.reward_claim)
        RewardPrompt.Resume -> Triple(R.string.reward_resume_title, stringResource(R.string.reward_resume_detail, uid), R.string.reward_resume_confirm)
        RewardPrompt.AutoClaim -> Triple(R.string.reward_auto_title, stringResource(R.string.reward_auto_detail), R.string.reward_auto_enable)
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(title)) }, text = { Text(detail) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } })
}

@Composable
private fun SwitchRow(@StringRes title: Int, @StringRes summary: Int, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
        .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
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
    if (errorCode != null) {
        Surface(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(XmIcons.Outlined.ErrorOutline, contentDescription = null)
                Text(stringResource(errorMessage(errorCode)), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@StringRes
private fun errorMessage(code: String): Int = when (code) {
    "invalid_credentials" -> R.string.account_invalid_info
    "credential_mismatch" -> R.string.account_mismatched_info
    "input_too_long" -> R.string.account_input_too_long
    "login_expired", "login_required" -> R.string.account_expired
    "network", "network_error", "http_error" -> R.string.account_network_error
    "archive_invalid" -> R.string.archive_invalid_error
    "uncertain" -> R.string.account_uncertain
    "reward_paused" -> R.string.reward_paused_error
    "reward_rejected" -> R.string.reward_rejected_error
    "invalid_mobile" -> R.string.login_error_mobile
    "invalid_code" -> R.string.login_error_code
    "sms_verify_rejected" -> R.string.login_error_sms_verify
    "sms_send_rejected" -> R.string.login_error_sms_send
    "quick_login_rejected" -> R.string.login_error_quick
    "invalid_captcha", "captcha_reused" -> R.string.login_error_captcha
    "challenge_invalid", "challenge_expired" -> R.string.login_error_challenge
    "rate_limited", "sms_cooldown" -> R.string.account_rate_limited
    "invalid_quota", "invalid_json", "invalid_response", "protocol", "response_too_large" -> R.string.account_verification_error
    "account_read_failed" -> R.string.account_read_error
    "account_write_failed" -> R.string.account_write_error
    else -> R.string.account_operation_error
}

/** The primary-account choice only means something once another account exists. */
internal val AccountsState.hasAccounts: Boolean
    get() = this is AccountsState.Ready && accounts.isNotEmpty()

@Composable
internal fun accountName(account: AccountSummary): String = stringResource(R.string.account_name, account.uid)

@Composable
internal fun quotaText(account: AccountSummary): String = listeningTimeText(account.quota.remainingSeconds)

/** Listening time as hours and minutes, or 不足 1 分钟 below a minute. */
@Composable
internal fun listeningTimeText(seconds: Long): String {
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
