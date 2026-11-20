package com.xuleo.xmplus.md3.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
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

private val HeaderCover = 120.dp
private val AnchorHeight = 24.dp
private val TagHeight = 32.dp
private const val MaxTags = 6

/**
 * The album's head. Its blurred cover runs up behind the status bar and fades into the page, so the top of the screen reads
 * as one piece. The cover sits on the left with the title and anchor beside it; the numbers and tags go below, so the head
 * is short and every line has a fixed height. 订阅 is in the top bar rather than here, so it stays in reach after the head
 * has scrolled away; downloads start from the episode list, where the episodes are picked.
 */
@Composable
fun AlbumHero(
    detail: AlbumDetail,
    modifier: Modifier = Modifier,
    onOpenAnchor: (() -> Unit)? = null,
) {
    val album = detail.album
    val shape = MaterialTheme.shapes.large
    DetailHero(album.coverUrl, modifier) {
        Column(Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Cover(album.coverUrl, Modifier.size(HeaderCover).shadow(elevation = 10.dp, shape = shape), shape)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f).heightIn(min = HeaderCover)) {
                    Text(
                        album.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    AnchorLine(album.artist, album.anchorAvatarUrl, detail.anchorVerified, onOpenAnchor)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                statsLine(detail), Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Tags(detail.tags.take(MaxTags))
            Spacer(Modifier.height(20.dp))
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

/** The anchor's avatar and name also serve as the entry to their page. */
@Composable
private fun AnchorLine(name: String, avatar: String?, verified: Boolean, onOpen: (() -> Unit)?) {
    if (name.isNotEmpty()) {
        val click = if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier
        Row(click.height(AnchorHeight), verticalAlignment = Alignment.CenterVertically) {
            Avatar(avatar, Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (verified) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.Verified, stringResource(R.string.anchor_verified), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** The tags on one line; a long list scrolls sideways rather than wrapping. */
@Composable
private fun Tags(tags: List<String>) {
    Row(
        Modifier.fillMaxWidth().height(TagHeight).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tags.forEach { tag ->
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(tag, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

@XmPreviews
@Composable
private fun AlbumHeroPreview() = XmPreview {
    AlbumHero(
        AlbumDetail(
            Album("1", "A long album title that wraps onto a second line", "Anchor name", coverUrl = null, trackCount = 215),
            AlbumStats(playCount = 123_456, finished = false),
            paid = true, anchorVerified = true, tags = listOf("Fantasy", "Drama"), recordDescending = false,
        ),
    )
}
