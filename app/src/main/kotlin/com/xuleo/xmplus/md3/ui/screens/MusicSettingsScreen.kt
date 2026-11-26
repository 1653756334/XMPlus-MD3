package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.media.MusicMediaCache
import com.xuleo.xmplus.md3.core.media.MusicQualitySettings
import com.xuleo.xmplus.md3.ui.components.SectionCard
import com.xuleo.xmplus.md3.ui.icons.XmIcons

/** 音乐 settings: the plugins, the qualities songs play and download in, and the room they may take on the phone. */
@Composable
internal fun MusicSettingsScreen(
    quality: MusicQualitySettings?,
    cache: MusicMediaCache?,
    pluginCount: Int?,
    onPlugins: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_music), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(XmIcons.Outlined.ArrowBack, stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.align(Alignment.TopCenter).widthIn(max = XmLayout.ContentWidth).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(vertical = XmLayout.ItemGap),
                verticalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
            ) {
                SectionCard {
                    ListItem(
                        onClick = onPlugins,
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Icon(XmIcons.Outlined.QueueMusic, contentDescription = null) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (pluginCount != null && pluginCount > 0) {
                                    Text(pluginCount.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(XmIcons.Outlined.ChevronRight, contentDescription = null)
                            }
                        },
                    ) { Text(stringResource(R.string.plugins_title)) }
                }
                if (quality != null) SectionCard { MusicPlaybackSettings(quality) }
                if (cache != null) SectionCard { MusicCacheSettings(cache) }
            }
        }
    }
}
