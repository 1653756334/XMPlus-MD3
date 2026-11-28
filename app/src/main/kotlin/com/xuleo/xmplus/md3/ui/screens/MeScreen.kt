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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.ui.icons.XmIcons
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.account.AccountViewModel
import com.xuleo.xmplus.md3.core.account.*
import com.xuleo.xmplus.md3.core.media.EpisodeMediaCache
import com.xuleo.xmplus.md3.core.media.MusicMediaCache
import com.xuleo.xmplus.md3.core.media.MusicQualitySettings
import com.xuleo.xmplus.md3.core.designsystem.*
import com.xuleo.xmplus.md3.settings.AppLanguage
import com.xuleo.xmplus.md3.ui.Destination
import com.xuleo.xmplus.md3.ui.components.SectionCard
import coil3.compose.AsyncImage

@Composable
fun MeScreen(
    viewModel: AccountViewModel?,
    onManage: () -> Unit,
    onLogin: () -> Unit,
    onImportBackup: () -> Unit,
    historyUpload: @Composable () -> Unit,
    episodeCache: EpisodeMediaCache?,
    quality: MusicQualitySettings?,
    cache: MusicMediaCache?,
    pluginCount: Int?,
    language: AppLanguage,
    onLanguage: () -> Unit,
    onDownloads: () -> Unit,
    onPlugins: () -> Unit,
) {
    val state = viewModel?.accounts?.collectAsStateWithLifecycle()?.value ?: AccountsState.Ready(emptyList())
    val primary = (state as? AccountsState.Ready)?.accounts?.firstOrNull { it.primary }
    TabScaffold(Destination.Me.label) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.align(Alignment.TopCenter).widthIn(max = XmLayout.ContentWidth)
                .fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = XmLayout.ItemGap),
                verticalArrangement = Arrangement.spacedBy(XmLayout.SectionGap)) {
                if (state is AccountsState.Loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding))
                if (state is AccountsState.Unavailable) {
                    Box(Modifier.padding(horizontal = XmLayout.PagePadding)) { AccountReadError({ viewModel?.retryLoad() }, viewModel?.busy == true) }
                }
                Box(Modifier.padding(horizontal = XmLayout.PagePadding)) { AccountOperationFeedback(viewModel?.busy == true, viewModel?.errorCode) }
                SettingsGroups(
                    accountName = primary?.let { accountName(it) },
                    historyUpload = historyUpload,
                    episodeCache = episodeCache,
                    quality = quality,
                    cache = cache,
                    pluginCount = pluginCount,
                    language = language,
                    onLanguage = onLanguage,
                    onAccounts = if (primary == null) onLogin else onManage,
                    onPlugins = onPlugins,
                    onDownloads = onDownloads,
                )
            }
        }
    }
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
internal fun languageLabel(language: AppLanguage): String = stringResource(when (language) {
    AppLanguage.System -> R.string.language_system
    AppLanguage.Chinese -> R.string.language_zh
    AppLanguage.English -> R.string.language_en
})

/** 语言: the three choices in a radio dialog, applied as one is picked. */
@Composable
internal fun LanguageDialog(selected: AppLanguage, onSelect: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
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
private fun MeScreenPreview() = XmPreview {
    MeScreen(
        null, onManage = {}, onLogin = {}, onImportBackup = {}, historyUpload = {},
        episodeCache = null, quality = null, cache = null, pluginCount = 6,
        language = AppLanguage.System, onLanguage = {}, onDownloads = {}, onPlugins = {},
    )
}
