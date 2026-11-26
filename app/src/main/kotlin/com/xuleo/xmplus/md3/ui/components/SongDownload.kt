@file:OptIn(ExperimentalMaterial3Api::class)

package com.xuleo.xmplus.md3.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.download.DownloadTask
import com.xuleo.xmplus.md3.core.media.MusicQuality
import com.xuleo.xmplus.md3.core.model.ContentKind
import com.xuleo.xmplus.md3.core.model.MusicItem
import com.xuleo.xmplus.md3.ui.screens.label
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Song downloads behind the song menu and the player: every task, the quality songs download in, and the ways to change
 * them. Null where there is nowhere to download to, as in previews.
 */
class SongDownloads(
    val tasks: StateFlow<List<DownloadTask>>,
    val quality: StateFlow<MusicQuality>,
    /** Queues [MusicItem] at a quality, or at the download quality setting when it is null. */
    val download: suspend (MusicItem, MusicQuality?) -> Boolean,
    val pause: suspend (String) -> Unit,
    val resume: suspend (String) -> Unit,
)

val LocalSongDownloads = staticCompositionLocalOf<SongDownloads?> { null }

/** [song]'s download, whatever its state; null when it was never queued. */
fun List<DownloadTask>.songDownload(song: MusicItem): DownloadTask? =
    firstOrNull { it.trackId == song.key && it.kind == ContentKind.Song }

/**
 * 下载 for one song: the qualities it can be downloaded in, the download-quality setting marked 默认. Picking one queues
 * the song at that quality and says so; the same sheet retries a failed download.
 */
@Composable
internal fun SongDownloadSheet(song: MusicItem, onDismiss: () -> Unit) {
    val downloads = LocalSongDownloads.current ?: return
    val preferred by downloads.quality.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val queued = stringResource(R.string.downloads_queued)
    val scope = rememberCoroutineScope()
    val sheet = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = { WindowInsets.safeDrawing },
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = XmLayout.ItemGap)) {
            Box(Modifier.padding(horizontal = XmLayout.PagePadding)) { SheetHeader(stringResource(R.string.music_download_quality)) }
            MusicQuality.entries.forEach { quality ->
                ListItem(
                    headlineContent = { Text(stringResource(quality.label)) },
                    trailingContent = if (quality == preferred) {
                        {
                            Text(
                                stringResource(R.string.music_tag_default),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else null,
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(role = Role.RadioButton) {
                        scope.launch {
                            sheet.hide()
                            if (!sheet.isVisible) {
                                onDismiss()
                                if (downloads.download(song, quality)) Toast.makeText(context, queued, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }.padding(horizontal = 8.dp),
                )
            }
        }
    }
}
