@file:OptIn(ExperimentalMaterial3Api::class)

package com.xuleo.xmplus.md3.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.media.MusicLibrary
import com.xuleo.xmplus.md3.core.model.MusicItem
import com.xuleo.xmplus.md3.ui.icons.XmIcons

/** The music library behind every song menu; null where there is none, as in previews. */
val LocalMusicLibrary = staticCompositionLocalOf<MusicLibrary?> { null }

/**
 * 加入歌单, as MusicFree's panel: 新建歌单 first, then 我喜欢 and the user's sheets with cover and count. Tapping one adds
 * [songs] to it; with [move] they leave [from] for it instead. [from] itself is not offered. [onDone] runs after a sheet
 * was chosen.
 */
@Composable
fun AddToSheetSheet(
    songs: List<MusicItem>,
    onDismiss: () -> Unit,
    from: String? = null,
    move: Boolean = false,
    onDone: () -> Unit = {},
) {
    val library = LocalMusicLibrary.current ?: return
    val liked by library.liked.collectAsStateWithLifecycle()
    val sheets by library.sheets.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    var creating by rememberSaveable { mutableStateOf(false) }
    fun put(id: String, name: String) {
        val message = if (move && from != null) {
            library.moveSongs(songs, from, id)
            resources.getString(R.string.music_sheet_moved, name)
        } else if (library.addSongs(id, songs) > 0) {
            resources.getString(R.string.music_sheet_added, name)
        } else {
            resources.getString(R.string.music_sheet_present)
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        onDismiss()
        onDone()
    }
    // As in MusicFree, cancelling the name brings the list back.
    if (creating) {
        SheetNameDialog(
            stringResource(R.string.library_sheet_new), stringResource(R.string.library_sheet_create),
            onDismiss = { creating = false },
        ) { name ->
            creating = false
            put(library.createSheet(name), name)
        }
        return
    }
    val likesName = stringResource(R.string.music_favorites)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = { WindowInsets.safeDrawing },
    ) {
        Text(
            stringResource(if (move) R.string.music_move_to else R.string.music_add_to_sheet),
            Modifier.padding(horizontal = XmLayout.PagePadding, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
            item(key = "new") {
                TargetRow(null, XmIcons.Outlined.Add, stringResource(R.string.library_sheet_new), null) { creating = true }
            }
            if (from != MusicLibrary.LikesId) {
                item(key = MusicLibrary.LikesId) {
                    TargetRow(liked.firstOrNull()?.artwork, XmIcons.Outlined.FavoriteBorder, likesName, liked.size) {
                        put(MusicLibrary.LikesId, likesName)
                    }
                }
            }
            items(sheets.filter { it.id != from }, key = { it.id }) { sheet ->
                TargetRow(sheet.coverUrl, null, sheet.name, sheet.songs.size) { put(sheet.id, sheet.name) }
            }
        }
    }
}

@Composable
private fun TargetRow(cover: String?, icon: ImageVector?, name: String, count: Int?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp)
            .padding(horizontal = XmLayout.PagePadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
    ) {
        if (cover == null && icon != null) {
            Box(
                Modifier.size(48.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        } else {
            MusicCover(cover, Modifier.size(48.dp), MaterialTheme.shapes.small)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (count != null) {
                Text(
                    pluralStringResource(R.plurals.music_song_count, count, count),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
        }
    }
}

/** A sheet's name: one field, for 新建歌单 and 重命名. The button stays off until the name is set and changed. */
@Composable
internal fun SheetNameDialog(
    title: String,
    confirm: String,
    onDismiss: () -> Unit,
    initial: String = "",
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val value = name.text.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                name, { name = it }, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                label = { Text(stringResource(R.string.library_sheet_name)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotEmpty() && value != initial.trim()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
