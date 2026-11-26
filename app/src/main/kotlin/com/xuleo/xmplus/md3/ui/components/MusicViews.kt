package com.xuleo.xmplus.md3.ui.components

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import com.xuleo.xmplus.md3.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.model.MusicAlbum
import com.xuleo.xmplus.md3.core.model.MusicArtist
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.stringResource
import com.xuleo.xmplus.md3.core.model.MusicItem
import com.xuleo.xmplus.md3.core.model.MusicSheet
import com.xuleo.xmplus.md3.ui.icons.XmIcons

/** A music cover; without an image it is a tinted surface with a note, never a grey box. */
@Composable
fun MusicCover(url: String?, modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.medium) {
    if (url != null) {
        Cover(url, modifier, shape)
        return
    }
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Icon(
            XmIcons.Filled.MusicNote, contentDescription = null, Modifier.fillMaxSize(0.4f),
            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
        )
    }
}

/** A sheet in a grid: cover with its play count when the plugin gives one, and the title on two lines. */
@Composable
fun SheetCard(sheet: MusicSheet, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Column(modifier.clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium)) {
            MusicCover(sheet.coverUrl, Modifier.matchParentSize())
            sheet.playCount?.takeIf { it > 0 }?.let { PlayCountBadge(it, Modifier.align(Alignment.BottomStart).fillMaxWidth()) }
        }
        Text(sheet.title, style = MaterialTheme.typography.bodyMedium, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A song: cover, title on one line and artist · album under it. The playing song shows the equalizer in place of
 * nothing, so the row keeps its width. With [onPlayNext] the row ends in the song menu (下一首播放, 加入歌单, then
 * [extraItems]); [trailing] replaces that menu, as check boxes do while selecting. [onLongClick] starts selection.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: MusicItem,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    onPlayNext: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    extraItems: List<SongMenuItem> = emptyList(),
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {},
) {
    val click = if (onLongClick != null) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    else Modifier.clickable(onClick = onClick)
    val ends = trailing != null || onPlayNext != null
    Row(
        modifier.fillMaxWidth().then(click).heightIn(min = 64.dp)
            .padding(start = XmLayout.PagePadding, end = if (ends) 4.dp else XmLayout.PagePadding)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
    ) {
        MusicCover(song.artwork, Modifier.size(48.dp), MaterialTheme.shapes.small)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            val meta = listOf(song.artist, song.album).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (playing) Icon(XmIcons.Filled.Equalizer, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        when {
            trailing != null -> trailing()
            onPlayNext != null -> SongMenu(song, playing, onPlayNext, extraItems)
        }
    }
}

/** An action a list adds to the song menu after the shared ones, such as 从歌单移除. */
class SongMenuItem(val text: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * The song menu, shared by every song list: 下一首播放 and 加入歌单 (with [LocalMusicLibrary]), then [extraItems].
 * [onPlayNext] null leaves 下一首播放 out.
 */
@Composable
fun SongMenu(
    song: MusicItem,
    playing: Boolean,
    onPlayNext: (() -> Unit)?,
    extraItems: List<SongMenuItem> = emptyList(),
) {
    var open by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val added = stringResource(R.string.music_play_next_added)
    val library = LocalMusicLibrary.current
    Box {
        IconButton(onClick = { open = true }) {
            Icon(XmIcons.Outlined.MoreVert, stringResource(R.string.music_song_more), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        XmMenu(open, { open = false }) {
            val count = (if (onPlayNext != null) 1 else 0) + (if (library != null) 1 else 0) + extraItems.size
            var index = 0
            if (onPlayNext != null) {
                XmMenuItem(
                    stringResource(R.string.music_play_next), {
                        open = false
                        onPlayNext()
                        Toast.makeText(context, added, Toast.LENGTH_SHORT).show()
                    }, index++, count,
                    icon = XmIcons.Outlined.QueueMusic, enabled = !playing,
                )
            }
            if (library != null) {
                XmMenuItem(
                    stringResource(R.string.music_add_to_sheet), { open = false; adding = true }, index++, count,
                    icon = XmIcons.Outlined.PlaylistAdd,
                )
            }
            extraItems.forEach { item ->
                XmMenuItem(item.text, { open = false; item.onClick() }, index++, count, icon = item.icon)
            }
        }
    }
    if (adding) AddToSheetSheet(listOf(song), onDismiss = { adding = false })
}

/** An album in a list: cover, title, and the date and song count when the plugin gives them. */
@Composable
fun MusicAlbumRow(album: MusicAlbum, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp)
            .padding(horizontal = XmLayout.PagePadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
    ) {
        MusicCover(album.artwork, Modifier.size(56.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(album.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val count = album.worksNum?.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.music_song_count, it, it) }
            val meta = listOfNotNull(album.date.takeIf { it.isNotBlank() }, count).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** An artist in a list: round avatar, name, and fans or works when the plugin gives them. */
@Composable
fun MusicArtistRow(artist: MusicArtist, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp)
            .padding(horizontal = XmLayout.PagePadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
    ) {
        MusicCover(artist.avatar, Modifier.size(48.dp), CircleShape)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(artist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                artist.fans?.takeIf { it > 0 }?.let { stringResource(R.string.music_fans, formatCount(it)) },
                artist.worksNum?.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.music_song_count, it, it) },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A sheet in a list: cover, title, and who made it with its song count. */
@Composable
fun MusicSheetRow(sheet: MusicSheet, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp)
            .padding(horizontal = XmLayout.PagePadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
    ) {
        MusicCover(sheet.coverUrl, Modifier.size(56.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(sheet.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                sheet.artist.takeIf { it.isNotBlank() },
                sheet.worksNum?.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.music_song_count, it, it) },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A ranking: cover, name, and what the plugin says about it, which is usually its first songs. */
@Composable
fun TopListRow(sheet: MusicSheet, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp)
            .padding(horizontal = XmLayout.PagePadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
    ) {
        MusicCover(sheet.coverUrl, Modifier.size(80.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(sheet.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sheet.description.isNotBlank()) {
                Text(
                    sheet.description.trim(), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
