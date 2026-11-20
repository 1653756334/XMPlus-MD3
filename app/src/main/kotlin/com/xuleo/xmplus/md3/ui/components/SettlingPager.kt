package com.xuleo.xmplus.md3.ui.components

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
) {
    val snap = PagerDefaults.flingBehavior(state, snapAnimationSpec = tween(SettleMillis, easing = EaseOutCubic))
    HorizontalPager(
        state = state,
        modifier = modifier,
        flingBehavior = remember(state, snap) { PageSnap(state, snap) },
        key = key,
        overscrollEffect = null,
        pageContent = pageContent,
    )
}

private class PageSnap(private val state: PagerState, private val snap: TargetedFlingBehavior) : TargetedFlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float, onRemainingDistanceUpdated: (Float) -> Unit): Float {
        // A vertical fling from a child passes through this horizontal pager before reaching
        // the player sheet. A zero-distance tween would hold it here for the full settle time.
        if (initialVelocity == 0f && state.currentPageOffsetFraction == 0f) {
            onRemainingDistanceUpdated(0f)
            return 0f
        }
        return with(snap) { performFling(initialVelocity, onRemainingDistanceUpdated) }
    }
}
