@file:OptIn(ExperimentalMaterial3Api::class)

package com.xuleo.xmplus.md3.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import kotlinx.coroutines.launch

internal val DetailTopBarHeight = 56.dp
private val RevealStart = 24.dp
private val RevealSpan = 72.dp
private val BackdropBlur = 40.dp
private const val ShowHeaderThreshold = 0.5f

/** Edge-to-edge details with tabs under a shared collapsible header. List position is independent of the header. */
@Composable
internal fun DetailPage(
    onBack: () -> Unit,
    title: String = "",
    header: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val top = WindowInsets.statusBars.getTop(density).toFloat()
    val bar = with(density) { DetailTopBarHeight.toPx() }
    val revealStart = with(density) { RevealStart.toPx() }
    val revealSpan = with(density) { RevealSpan.toPx() }
    val chrome = remember(top, bar, header != null) { CollapsingChrome(top + bar, expandOnlyAtTop = true) }
    val progress = { ((-chrome.offset - revealStart) / revealSpan).coerceIn(0f, 1f) }
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(
            Modifier.fillMaxSize().clipToBounds().nestedScroll(chrome.connection),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (header == null) {
                DetailTopBarSpace()
            } else {
                Box(Modifier.collapsing(chrome)) {
                    Box(Modifier.onSizeChanged { chrome.height = it.height.toFloat() }) { header() }
                }
            }
            content()
        }
        DetailTopBar(
            title = title,
            progress = progress,
            onBack = onBack,
            onShowHeader = { scope.launch { chrome.animateExpand(motion) } },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/** The artwork extends behind the status bar and fades into the page below the hero. */
@Composable
internal fun DetailHero(
    artwork: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    Box(modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize().clipToBounds()) {
            Cover(artwork, Modifier.fillMaxSize().blur(BackdropBlur), RectangleShape)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(surface.copy(alpha = 0.6f), surface.copy(alpha = 0.85f), surface)),
                ),
            )
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            DetailTopBarSpace()
            content()
        }
    }
}

@Composable
private fun DetailTopBarSpace() {
    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
    Spacer(Modifier.height(DetailTopBarHeight))
}

/** With the header visible the left button goes back; when collapsed it restores the header without scrolling the list. */
@Composable
private fun DetailTopBar(
    title: String,
    progress: () -> Float,
    onBack: () -> Unit,
    onShowHeader: () -> Unit,
    modifier: Modifier,
) {
    val surface = MaterialTheme.colorScheme.surface
    val currentProgress by rememberUpdatedState(progress)
    val showBack by remember { derivedStateOf { currentProgress() < ShowHeaderThreshold } }
    Column(modifier.fillMaxWidth().drawBehind { drawRect(surface, alpha = progress()) }) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(Modifier.fillMaxWidth().height(DetailTopBarHeight), verticalAlignment = Alignment.CenterVertically) {
            Crossfade(showBack, animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "detailTopButton") { back ->
                IconButton(if (back) onBack else onShowHeader, Modifier.padding(start = 4.dp)) {
                    if (back) Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    else Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.detail_show_header))
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

/** A tap switches pages immediately; the Material indicator still animates and the pager can still be swiped. */
@Composable
internal fun DetailTabs(pager: PagerState, labels: List<Int>, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    PrimaryTabRow(pager.currentPage, modifier, containerColor = Color.Transparent, divider = {}) {
        CompositionLocalProvider(LocalRippleConfiguration provides null) {
            labels.forEachIndexed { index, label ->
                Tab(
                    selected = pager.currentPage == index,
                    onClick = { scope.launch { pager.scrollToPage(index) } },
                    text = { Text(stringResource(label), maxLines = 1) },
                )
            }
        }
    }
}
