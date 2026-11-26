package com.xuleo.xmplus.md3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.core.designsystem.XmLayout

/** Space above and below the content of a card whose heading is a full 48dp row. */
val CardPartPadding = 8.dp

/** A tonal rounded group on a page, as 全站热播榜 sits on 听书 home. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = XmLayout.PagePadding),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier.padding(horizontal = XmLayout.PagePadding).fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(contentPadding), verticalArrangement = verticalArrangement, content = content)
    }
}

/**
 * One piece of a [SectionCard] that a lazy list draws item by item, so its rows can still be reordered:
 * [first] and [last] round the card's ends and add its inner space there. [margin] is the space beside the card;
 * pass 0 when the caller has already inset the piece (a reorderable row lifts inside the card's width).
 */
@Composable
fun Modifier.sectionCardPart(first: Boolean, last: Boolean, padding: Dp = CardPartPadding, margin: Dp = XmLayout.PagePadding): Modifier {
    val large = MaterialTheme.shapes.large
    val shape = when {
        first && last -> large
        first -> large.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
        last -> large.copy(topStart = ZeroCornerSize, topEnd = ZeroCornerSize)
        else -> RectangleShape
    }
    return this.padding(horizontal = margin).fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
        .padding(top = if (first) padding else 0.dp, bottom = if (last) padding else 0.dp)
}

/** The heading at the top of a card, with an optional action at its end. */
@Composable
fun CardHeading(title: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp)
            .padding(start = XmLayout.PagePadding, end = if (trailing != null) 4.dp else XmLayout.PagePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke(this)
    }
}
