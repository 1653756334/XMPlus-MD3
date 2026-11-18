package com.xuleo.xmplus.md3.ui.components

import android.text.Html
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.designsystem.XmPreview
import com.xuleo.xmplus.md3.core.designsystem.XmPreviews
import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.AlbumDetail
import com.xuleo.xmplus.md3.core.model.AlbumStats

private val BackdropBlur = 40.dp
private const val MaxTags = 6

/** The album's head: its cover over a blurred copy of itself, the title, the anchor, the numbers and the tags. */
@Composable
fun AlbumHeader(detail: AlbumDetail, modifier: Modifier = Modifier) {
    val album = detail.album
    val surface = MaterialTheme.colorScheme.surface
    Box(modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize().clipToBounds()) {
            Cover(album.coverUrl, Modifier.fillMaxSize().blur(BackdropBlur), RectangleShape)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(surface.copy(alpha = 0.55f), surface))))
        }
        Row(Modifier.padding(XmLayout.PagePadding), horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap)) {
            Cover(album.coverUrl, Modifier.size(128.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    album.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
                if (album.artist.isNotEmpty()) AnchorLine(album.artist, album.anchorAvatarUrl, detail.anchorVerified)
                Text(
                    statsLine(detail), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val tags = detail.tags.take(MaxTags)
                if (tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.forEach { Tag(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun statsLine(detail: AlbumDetail): String {
    val count = detail.album.trackCount
    return listOfNotNull(
        detail.stats.playCount?.let { stringResource(R.string.rank_plays, formatCount(it)) },
        if (count > 0) pluralStringResource(R.plurals.episode_count, count, count) else null,
        stringResource(if (detail.stats.finished) R.string.album_status_finished else R.string.album_status_ongoing),
        if (detail.paid) stringResource(R.string.album_paid) else null,
    ).joinToString(" · ")
}

/** The anchor's name and avatar. It does not open the anchor page yet, which arrives with the anchor step. */
@Composable
private fun AnchorLine(name: String, avatar: String?, verified: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Avatar(avatar, Modifier.size(24.dp))
        Text(
            name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (verified) {
            Icon(Icons.Filled.Verified, stringResource(R.string.anchor_verified), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Tag(text: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** The introduction's HTML as plain text: paragraphs and line breaks kept, tags and images dropped. */
fun plainText(html: String): String = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim()

@XmPreviews
@Composable
private fun AlbumHeaderPreview() = XmPreview {
    AlbumHeader(
        AlbumDetail(
            Album("1", "A long album title that wraps onto a second line", "Anchor name", coverUrl = null, trackCount = 215),
            AlbumStats(playCount = 123_456, finished = false),
            paid = true, anchorVerified = true, tags = listOf("Fantasy", "Drama"), recordDescending = false,
        ),
    )
}