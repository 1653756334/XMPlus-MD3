package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.media.MusicQuality
import com.xuleo.xmplus.md3.core.media.MusicQualitySettings
import com.xuleo.xmplus.md3.ui.icons.XmIcons

/**
 * 默认播放音质 and 下载音质: the quality songs start in, shown under their names, each picking from the four in a radio
 * dialog. They are the first quality tried; playback and downloads fall back from there.
 */
@Composable
internal fun MusicPlaybackSettings(settings: MusicQualitySettings) {
    val play by settings.play.collectAsStateWithLifecycle()
    val download by settings.download.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf<Choice?>(null) }
    val playLabel = stringResource(R.string.music_play_quality)
    val downloadLabel = stringResource(R.string.music_download_quality)

    QualitySettingRow(playLabel, play, XmIcons.Outlined.PlayArrow) { picking = Choice.Play }
    QualitySettingRow(downloadLabel, download, XmIcons.Outlined.Download) { picking = Choice.Download }

    val choice = picking ?: return
    val current = if (choice == Choice.Play) play else download
    AlertDialog(
        onDismissRequest = { picking = null },
        title = { Text(stringResource(if (choice == Choice.Play) R.string.music_play_quality else R.string.music_download_quality)) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                MusicQuality.entries.forEach { quality ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(quality == current, role = Role.RadioButton, onClick = {
                                if (choice == Choice.Play) settings.setPlay(quality) else settings.setDownload(quality)
                                picking = null
                            })
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = quality == current, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(quality.label))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private enum class Choice { Play, Download }

@Composable
private fun QualitySettingRow(label: String, quality: MusicQuality, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        onClick = onClick,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, contentDescription = null) },
        supportingContent = { Text(stringResource(quality.label)) },
        trailingContent = { Icon(XmIcons.Outlined.ChevronRight, contentDescription = null) },
    ) { Text(label) }
}
