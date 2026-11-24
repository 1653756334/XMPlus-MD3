package com.xuleo.xmplus.md3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.ui.icons.XmIcons
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.AlbumStats

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = XmLayout.PagePadding, end = if (onAction != null) 4.dp else XmLayout.PagePadding),
        verticalAlignment = Alignment.CenterVertically) {
        Text(
            title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onAction, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                Text(actionLabel, maxLines = 1)
                Icon(XmIcons.Filled.KeyboardArrowRight, contentDescription = null, Modifier.size(18.dp))
            }
        }
    }
}

/** Play count over a soft shade, for the corner of a cover. */
@Composable
fun PlayCountBadge(playCount: Long, modifier: Modifier = Modifier) {
    Row(
        modifier.background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(XmIcons.Filled.Headphones, contentDescription = null, Modifier.size(12.dp), tint = Color.White)
        Text(formatCount(playCount), style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
    }
}

/** A cover with its title and anchor beneath, sized for a horizontal shelf. */
@Composable
fun AlbumCard(album: Album, stats: AlbumStats?, modifier: Modifier = Modifier.width(128.dp), onClick: () -> Unit = {}) {
    Column(modifier.clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Cover(album.coverUrl, Modifier.matchParentSize())
            stats?.playCount?.let { PlayCountBadge(it, Modifier.align(Alignment.BottomStart).fillMaxWidth()) }
        }
        // Two title lines and one artist line are always reserved, so cards in a shelf or grid line up whatever their text.
        Text(album.title, style = MaterialTheme.typography.bodyMedium, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(album.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 1, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A numbered chart row. The first three numbers use the accent colour. */
@Composable
fun RankRow(position: Int, album: Album, stats: AlbumStats?, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            position.toString(), Modifier.width(24.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            color = if (position <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Cover(album.coverUrl, Modifier.size(52.dp), MaterialTheme.shapes.small)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(album.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(album.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        stats?.playCount?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(XmIcons.Filled.Headphones, contentDescription = stringResource(R.string.play_count), Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatCount(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(0.dp))
    }
}