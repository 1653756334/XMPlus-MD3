package com.xuleo.xmplus.md3.ui.components

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.ui.icons.XmIcons
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.model.AlbumReply
import com.xuleo.xmplus.md3.core.model.AlbumReview
import com.xuleo.xmplus.md3.core.model.AlbumScore
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The score, the rating and recommendation counts, and the three shares as bars. Missing parts are left out. When the
 * server sends no album score, the recommendation share is the headline, as in XMPlus; with nothing at all but a review
 * count, that count stands alone.
 */
@Composable
fun ScoreSummary(score: AlbumScore, modifier: Modifier = Modifier) {
    val headline = score.albumScore?.let { String.format(Locale.ROOT, "%.1f", it) }
        ?: score.recommendationScore?.let(::percent)
    val ratios = listOf(
        score.recommended to R.string.album_ratio_recommend,
        score.neutral to R.string.album_ratio_neutral,
        score.notRecommended to R.string.album_ratio_not,
    ).filter { it.first != null }
    val summary = listOfNotNull(
        score.recommendation.ifEmpty { null },
        score.recommendedCount?.let { stringResource(R.string.album_recommend_count, it) },
    ).joinToString(" · ")
    val details = headline != null || score.ratingCount != null || summary.isNotEmpty()
    if (!details && ratios.isEmpty() && score.commentCount == null) return
    Column(
        modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap)) {
            headline?.let {
                Text(
                    it, style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                score.ratingCount?.let {
                    Text(stringResource(R.string.album_rating_count, it), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (summary.isNotEmpty()) {
                    Text(summary, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (score.albumScore != null) score.recommendationScore?.let {
                    Text(stringResource(R.string.album_recommend_score, percent(it)), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!details) score.commentCount?.let {
                    Text(stringResource(R.string.album_recommend_count, it), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        ratios.forEach { (share, label) -> share?.let { RatioBar(stringResource(label), it) } }
    }
}
@Composable
private fun RatioBar(label: String, share: Double) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap)) {
        Text(
            label, Modifier.width(88.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        LinearProgressIndicator({ share.toFloat() }, Modifier.weight(1f))
        Text(
            percent(share), Modifier.width(48.dp), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End,
        )
    }
}

private fun percent(share: Double): String = "${(share * 100).roundToInt()}%"

/** One review: who wrote it, the text, its likes, the way to its replies, and the first reply when the server sends it. A long press copies the text. */
@Composable
fun ReviewRow(review: AlbumReview, onReplies: (AlbumReview) -> Unit, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val copied = stringResource(R.string.album_review_copied)
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier.fillMaxWidth()
            .combinedClickable(onLongClick = {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, review.content)))
                    Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                }
            }, onClick = {})
            .padding(horizontal = XmLayout.PagePadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AuthorLine(review.author, review.avatarUrl, formatDate(review.createdAtMillis, locale), review.highQuality)
        Text(review.content, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            review.likes?.let {
                Icon(XmIcons.Filled.ThumbUp, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(formatCount(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            if (review.replyCount > 0) {
                TextButton(onClick = { onReplies(review) }) {
                    Text(stringResource(R.string.album_review_replies, review.replyCount))
                }
            }
        }
        review.preview?.let { PreviewReply(it) }
    }
}

/** One reply in the replies sheet. */
@Composable
fun ReplyRow(reply: AlbumReply, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AuthorLine(reply.author, reply.avatarUrl, formatDate(reply.createdAtMillis, locale), featured = false)
        Text(reply.content, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AuthorLine(name: String, avatar: String?, time: String, featured: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Avatar(avatar, Modifier.size(36.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (featured) {
            Text(stringResource(R.string.album_review_featured), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PreviewReply(reply: AlbumReply) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Text(
            "${reply.author}: ${reply.content}", Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium,
            maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
    }
}