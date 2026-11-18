package com.xuleo.xmplus.md3.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.AlbumStats
import com.xuleo.xmplus.md3.core.model.AnchorProfile
import com.xuleo.xmplus.md3.core.model.Track
import java.util.Locale

private val RowPadding = Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding, vertical = 8.dp)

@Composable
fun AlbumRow(album: Album, modifier: Modifier = Modifier) {
    Row(modifier.then(RowPadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap)) {
        Cover(album.coverUrl, Modifier.size(72.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(album.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (album.artist.isNotEmpty()) {
                Text(album.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (album.trackCount > 0) {
                Text(pluralStringResource(R.plurals.episode_count, album.trackCount, album.trackCount),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** The number beside a chart row; the first three use the accent colour. */
@Composable
fun RankPosition(position: Int?) {
    Text(
        position?.toString().orEmpty(), Modifier.width(28.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        color = if (position != null && position <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** A chart row: the server's position, a cover, the title and anchor, and play count, score and episodes when known. */
@Composable
fun RankedAlbumRow(position: Int?, album: Album, stats: AlbumStats?, modifier: Modifier = Modifier) {
    Row(modifier.then(RowPadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap)) {
        RankPosition(position)
        Cover(album.coverUrl, Modifier.size(56.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(album.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (album.artist.isNotEmpty()) {
                Text(album.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val details = listOfNotNull(
                stats?.playCount?.let { stringResource(R.string.rank_plays, formatCount(it)) },
                stats?.score?.let { stringResource(R.string.rank_score, String.format(Locale.ROOT, "%.1f", it)) },
                if (album.trackCount > 0) pluralStringResource(R.plurals.episode_count, album.trackCount, album.trackCount) else null,
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun TrackRow(track: Track, modifier: Modifier = Modifier) {
    Row(modifier.then(RowPadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap)) {
        Cover(track.coverUrl, Modifier.size(56.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val detail = listOfNotNull(
                track.albumTitle.ifEmpty { null },
                if (track.durationSeconds > 0) formatDuration(track.durationSeconds) else null,
            ).joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun AnchorRow(anchor: AnchorProfile, modifier: Modifier = Modifier, position: Int? = null) {
    Row(modifier.then(RowPadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap)) {
        if (position != null) RankPosition(position)
        Avatar(anchor.avatarUrl, Modifier.size(56.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(anchor.nickname, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (anchor.verified) {
                    Icon(Icons.Filled.Verified, stringResource(R.string.anchor_verified), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            val counts = listOfNotNull(
                anchor.albums?.let { pluralStringResource(R.plurals.anchor_album_count, it, formatCount(it.toLong())) },
                anchor.tracks?.let { pluralStringResource(R.plurals.anchor_sound_count, it, formatCount(it.toLong())) },
                anchor.fans?.let { pluralStringResource(R.plurals.anchor_follower_count, it, formatCount(it.toLong())) },
            ).joinToString(" · ")
            if (counts.isNotEmpty()) {
                Text(counts, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (anchor.intro.isNotEmpty()) {
                Text(anchor.intro, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}