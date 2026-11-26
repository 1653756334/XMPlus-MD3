package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.ui.icons.XmIcons
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.account.AccountViewModel
import com.xuleo.xmplus.md3.core.account.*
import com.xuleo.xmplus.md3.core.designsystem.*
import com.xuleo.xmplus.md3.core.media.HistoryUploader
import com.xuleo.xmplus.md3.core.media.MusicMediaCache
import com.xuleo.xmplus.md3.settings.AppLanguage
import com.xuleo.xmplus.md3.settings.setAppLanguage
import com.xuleo.xmplus.md3.ui.Destination
import coil3.compose.AsyncImage

@Composable
fun MeScreen(viewModel: AccountViewModel?, onManage: () -> Unit, onLogin: () -> Unit, onImportBackup: () -> Unit, historyUpload: HistoryUploader?,
    pluginCount: Int? = null, onPlugins: () -> Unit = {}, musicCache: MusicMediaCache? = null) {
    val configuration = LocalConfiguration.current
    val language = remember(configuration) { AppLanguage.current() }
    val state = viewModel?.accounts?.collectAsStateWithLifecycle()?.value ?: AccountsState.Ready(emptyList())
    MeContent(language, ::setAppLanguage, state, viewModel?.busy == true, viewModel?.errorCode,
        onManage = onManage, onImport = onLogin, onImportBackup = onImportBackup,
        onRefresh = { viewModel?.refreshQuota(it) }, onRetry = { viewModel?.retryLoad() },
        avatars = viewModel?.avatars.orEmpty(),
        generalExtras = {
            if (historyUpload != null) HistoryUploadSettings(historyUpload)
            if (musicCache != null) MusicCacheSettings(musicCache)
        },
        pluginCount = pluginCount, onPlugins = onPlugins)
}

@Composable
internal fun MeContent(
    language: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    state: AccountsState = AccountsState.Ready(emptyList()),
    busy: Boolean = false,
    errorCode: String? = null,
    onManage: () -> Unit = {},
    onImport: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    onRefresh: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    avatars: Map<String, String> = emptyMap(),
    generalExtras: @Composable () -> Unit = {},
    pluginCount: Int? = null,
    onPlugins: (() -> Unit)? = null,
) {
    var showLanguage by rememberSaveable { mutableStateOf(false) }
    TabScaffold(Destination.Me.label) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.align(Alignment.TopCenter).widthIn(max = XmLayout.ContentWidth)
                .fillMaxWidth().verticalScroll(rememberScrollState()).padding(XmLayout.PagePadding),
                verticalArrangement = Arrangement.spacedBy(XmLayout.SectionGap)) {
                when (state) {
                    AccountsState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    AccountsState.Unavailable -> AccountReadError(onRetry, busy)
                    is AccountsState.Ready -> {
                        val primary = state.accounts.firstOrNull { it.primary }
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AccountAvatar(primary?.let { avatars[it.identity] })
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(if (primary == null) stringResource(R.string.account_welcome) else accountName(primary),
                                            style = MaterialTheme.typography.titleLarge)
                                        Text(stringResource(if (primary == null) R.string.account_welcome_detail else R.string.account_primary),
                                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (primary != null) {
                                    Text(stringResource(R.string.account_quota), style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(quotaText(primary), style = MaterialTheme.typography.headlineMedium)
                                    Text(if (primary.expired) stringResource(R.string.account_expired) else checkedText(primary),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (primary.expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = if (primary == null) onImport else onManage, enabled = !busy) {
                                        Text(stringResource(if (primary == null) R.string.account_add else R.string.account_manage))
                                    }
                                    if (primary != null) OutlinedButton(onClick = { onRefresh(primary.identity) }, enabled = !busy) {
                                        Text(stringResource(R.string.account_refresh))
                                    } else OutlinedButton(onClick = onImportBackup, enabled = !busy) {
                                        Text(stringResource(R.string.archive_import))
                                    }
                                }
                            }
                        }
                    }
                }
                AccountOperationFeedback(busy, errorCode)
                if (onPlugins != null) ListItem(
                    onClick = onPlugins,
                    leadingContent = { Icon(XmIcons.Outlined.QueueMusic, contentDescription = null) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (pluginCount != null && pluginCount > 0) Text(pluginCount.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(XmIcons.Outlined.ChevronRight, contentDescription = null)
                        }
                    },
                ) { Text(stringResource(R.string.plugins_title)) }
                Column {
                    Text(stringResource(R.string.settings_general), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                    ListItem(
                        onClick = { showLanguage = true },
                        supportingContent = { Text(languageLabel(language)) },
                        leadingContent = { Icon(XmIcons.Outlined.Language, contentDescription = null) },
                        trailingContent = { Icon(XmIcons.Outlined.ChevronRight, contentDescription = null) },
                    ) { Text(stringResource(R.string.setting_language)) }
                    generalExtras()
                }
            }
        }
    }
    if (showLanguage) LanguageDialog(language, { showLanguage = false; onLanguageSelected(it) }, { showLanguage = false })
}

@Composable
internal fun AccountAvatar(url: String? = null) {
    // The default picture stays underneath, so it shows while the avatar loads and if it cannot be loaded.
    Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Icon(XmIcons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        if (url != null) AsyncImage(url, contentDescription = null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun languageLabel(language: AppLanguage): String = stringResource(when (language) {
    AppLanguage.System -> R.string.language_system
    AppLanguage.Chinese -> R.string.language_zh
    AppLanguage.English -> R.string.language_en
})

@Composable
private fun LanguageDialog(selected: AppLanguage, onSelect: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.setting_language)) },
        text = { Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
            AppLanguage.entries.forEach { option ->
                Row(Modifier.fillMaxWidth().selectable(option == selected, role = Role.RadioButton, onClick = { onSelect(option) })
                    .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = option == selected, onClick = null)
                    Spacer(Modifier.width(16.dp))
                    Text(languageLabel(option))
                }
            }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } })
}

@XmPreviews
@Composable
private fun MeContentPreview() = XmPreview { MeContent(AppLanguage.System, {}) }
