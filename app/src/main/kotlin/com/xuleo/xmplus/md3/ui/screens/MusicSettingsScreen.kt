package com.xuleo.xmplus.md3.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.media.EpisodeMediaCache
import com.xuleo.xmplus.md3.core.media.MusicMediaCache
import com.xuleo.xmplus.md3.core.media.MusicQualitySettings
import com.xuleo.xmplus.md3.settings.AppLanguage
import com.xuleo.xmplus.md3.ui.components.SettingsCard
import com.xuleo.xmplus.md3.ui.components.SettingsDivider
import com.xuleo.xmplus.md3.ui.components.SettingsRow
import com.xuleo.xmplus.md3.ui.icons.XmIcons

/**
 * What 我的 holds under its account: 通用 first, the things both kinds of content share, then 听书 and 音乐 for what is
 * particular to each. A group with nothing to show is left out, so a version without a cache has no empty heading.
 */
@Composable
internal fun SettingsGroups(
    accountName: String?,
    historyUpload: @Composable () -> Unit,
    episodeCache: EpisodeMediaCache?,
    quality: MusicQualitySettings?,
    cache: MusicMediaCache?,
    pluginCount: Int?,
    language: AppLanguage,
    onLanguage: () -> Unit,
    onAccounts: () -> Unit,
    onPlugins: () -> Unit,
    onDownloads: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(XmLayout.SectionGap)) {
        SettingsGroup(R.string.settings_general) {
            SettingsCard {
                SettingsRow(stringResource(R.string.downloads_title), icon = XmIcons.Outlined.Download, onClick = onDownloads)
                SettingsDivider()
                SettingsRow(stringResource(R.string.setting_language), icon = XmIcons.Outlined.Language,
                    supporting = languageLabel(language), onClick = onLanguage)
            }
        }
        SettingsGroup(R.string.settings_listen) {
            SettingsCard {
                SettingsRow(
                    stringResource(if (accountName == null) R.string.account_add else R.string.account_manage),
                    icon = XmIcons.Outlined.Person, supporting = accountName, onClick = onAccounts,
                )
                SettingsDivider()
                historyUpload()
                if (episodeCache != null) {
                    SettingsDivider()
                    EpisodeCacheSettings(episodeCache)
                }
            }
        }
        SettingsGroup(R.string.settings_music) {
            SettingsCard {
                SettingsRow(
                    stringResource(R.string.plugins_title), icon = XmIcons.Outlined.QueueMusic,
                    supporting = pluginCount?.takeIf { it > 0 }?.toString(), onClick = onPlugins,
                )
                if (quality != null) {
                    SettingsDivider()
                    MusicPlaybackSettings(quality)
                }
                if (cache != null) {
                    SettingsDivider()
                    MusicCacheSettings(cache)
                }
            }
        }
    }
}

/** One titled group of cards, headed the way 我的 heads its own rows. */
@Composable
internal fun SettingsGroup(@StringRes label: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(XmLayout.ItemGap)) {
        Text(
            stringResource(label), style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = XmLayout.PagePadding),
        )
        content()
    }
}
