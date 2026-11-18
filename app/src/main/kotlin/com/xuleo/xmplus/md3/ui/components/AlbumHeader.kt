package com.xuleo.xmplus.md3.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
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

/** The height of the bar over an album, below the status bar. */
val AlbumTopBarHeight = 56.dp

private val BackdropBlur = 40.dp
private val HeaderCover = 120.dp
private val AnchorHeight = 24.dp
private val TagHeight = 32.dp
private const val MaxTags = 6

/** Past this share of the way collapsed, the top-left button scrolls to the top instead of going back. */
private const val TopThreshold = 0.5f

/**
 * The album's head. Its blurred cover runs up behind the status bar and fades into the page, so the top of the screen reads
 * as one piece. The cover sits on the left with the title and anchor beside it; the numbers and tags go below, so the head
 * is short and every line has a fixed height.
 */
@Composable
fun AlbumHero(detail: AlbumDetail, modifier: Modifier = Modifier, onOpenAnchor: (() -> Unit)? = null) {
    val album = detail.album
    val surface = MaterialTheme.colorScheme.surface
    val shape = MaterialTheme.shapes.large
    Box(modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize().clipToBounds()) {
            Cover(album.coverUrl, Modifier.fillMaxSize().blur(BackdropBlur), RectangleShape)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(surface.copy(alpha = 0.6f), surface.copy(alpha = 0.85f), surface)),
                ),
            )
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding)) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(AlbumTopBarHeight))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Cover(album.coverUrl, Modifier.size(HeaderCover).shadow(elevation = 10.dp, shape = shape), shape)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f).height(HeaderCover)) {
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

/**
 * The bar over an album. While the header is in view its button goes back; once the header has collapsed it brings the header back
 * without moving the list. Its background and title fade in as [progress] rises from 0 to 1, and the button has no background of
 * its own, so it reads as an icon over the cover.
 */
@Composable
fun AlbumTopBar(
    title: String,
    progress: () -> Float,
    onBack: () -> Unit,
    onShowHeader: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme.surface
    val atTop by remember { derivedStateOf { progress() < TopThreshold } }
    Column(modifier.fillMaxWidth().drawBehind { drawRect(surface, alpha = progress()) }) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(Modifier.fillMaxWidth().height(AlbumTopBarHeight), verticalAlignment = Alignment.CenterVertically) {
            Crossfade(atTop, label = "albumTopButton") { top ->
                if (top) {
                    IconButton(onBack, Modifier.padding(start = 4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                } else {
                    IconButton(onShowHeader, Modifier.padding(start = 4.dp)) {
                        Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.album_show_header))
                    }
                }
            }
            Text(
                title, Modifier.weight(1f).padding(end = XmLayout.PagePadding).graphicsLayer { alpha = progress() },
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
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
