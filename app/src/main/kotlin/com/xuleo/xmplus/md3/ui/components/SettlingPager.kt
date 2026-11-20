package com.xuleo.xmplus.md3.ui.components

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Long enough to read as a slide, short enough that a flick does not feel held back. */
private const val SettleMillis = 300

/**
 * The app's sideways pages. A released page eases onto its place: the default spring took the finger's speed with it
 * and carried the page past its place and back. The first and last pages stop flat instead of stretching.
 */
@Composable
internal fun SettlingPager(
    state: PagerState,
    modifier: Modifier = Modifier,
    key: ((index: Int) -> Any)? = null,
    pageContent: @Composable PagerScope.(page: Int) -> Unit,
) = HorizontalPager(
    state = state,
    modifier = modifier,
    flingBehavior = PagerDefaults.flingBehavior(state, snapAnimationSpec = tween(SettleMillis, easing = EaseOutCubic)),
    key = key,
    overscrollEffect = null,
    pageContent = pageContent,
)