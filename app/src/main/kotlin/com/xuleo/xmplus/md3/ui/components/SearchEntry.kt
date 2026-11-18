package com.xuleo.xmplus.md3.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.designsystem.XmPreview
import com.xuleo.xmplus.md3.core.designsystem.XmPreviews
import com.xuleo.xmplus.md3.core.model.HotWord
import kotlinx.coroutines.delay

private const val ROTATE_MILLIS = 4000L

/** The bar that opens search. While it is shown it cycles through the hot words, one at a time. */
@Composable
fun SearchEntry(hotWords: List<HotWord>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var index by remember(hotWords) { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(hotWords) {
        if (hotWords.size > 1) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(ROTATE_MILLIS)
                    index = (index + 1) % hotWords.size
                }
            }
        }
    }
    val text = hotWords.getOrNull(index)?.display ?: stringResource(R.string.search_hint)
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    Box(modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(
            onClick, Modifier.fillMaxWidth().widthIn(max = XmLayout.FormWidth).heightIn(min = 44.dp),
            shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                AnimatedContent(
                    text, Modifier.padding(start = 8.dp).clipToBounds(), label = "hotWord",
                    transitionSpec = {
                        (slideInVertically(spatial) { it } + fadeIn(effects)) togetherWith (slideOutVertically(spatial) { -it } + fadeOut(effects))
                    },
                ) {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@XmPreviews
@Composable
private fun SearchEntryPreview() = XmPreview { SearchEntry(listOf(HotWord("a", "Hot word")), {}) }