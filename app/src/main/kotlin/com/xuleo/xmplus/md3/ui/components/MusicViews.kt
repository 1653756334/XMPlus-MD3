package com.xuleo.xmplus.md3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
 * nothing, so the row keeps its width. Actions on songs arrive with the player (M3).
 */
@Composable
fun SongRow(song: MusicItem, modifier: Modifier = Modifier, playing: Boolean = false, onClick: () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp)
            .padding(horizontal = XmLayout.PagePadding, vertical = 8.dp),
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
    }
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
