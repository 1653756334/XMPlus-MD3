package com.xuleo.xmplus.md3.ui

import androidx.activity.BackEventCompat
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import kotlin.math.roundToInt

/*
 * Back, as in Gmail, the same for every page and for the gesture and the back button alike. While the finger moves,
 * the page shrinks into a card and is pulled in from the edge the swipe began at, with the page it returns to showing
 * behind it; once released, it carries on that way and fades out. The first [PullShare] of the motion is the pull, so
 * the finger drives it directly; the rest is the page leaving.
 */
private const val BackMillis = 400
private const val PullShare = 0.5f
private const val PulledScale = 0.88f
/** How far the pulled card moves, as a share of the page width: its swiped side ends up about 11% in. */
private const val PulledShift = 0.05f
private const val LeaveShift = 0.5f
private val PulledCorner = 28.dp

private fun pulled(fraction: Float) = (fraction / PullShare).coerceAtMost(1f)
private fun leaving(fraction: Float) = ((fraction - PullShare) / (1f - PullShare)).coerceIn(0f, 1f)

/** [direction] is +1 to pull the page right, as a swipe from the left edge does, and -1 to pull it left. */
internal fun backTransform(direction: Int): ContentTransform {
    val pullShare = PulledShift / LeaveShift
    val pull = scaleOut(tween(BackMillis, easing = Easing(::pulled)), targetScale = PulledScale)
    val move = slideOutHorizontally(
        tween(BackMillis, easing = Easing { pullShare * pulled(it) + (1f - pullShare) * leaving(it) }),
    ) { width -> (width * LeaveShift * direction).roundToInt() }
    val fade = fadeOut(tween(BackMillis, easing = Easing(::leaving)))
    // The page underneath stays where it is; the one leaving is drawn above it.
    return EnterTransition.None togetherWith (pull + move + fade)
}

/** The pull follows the edge the swipe began at. A press of the back button pulls toward the reading end. */
internal fun backDirection(edge: Int, layoutDirection: Int): Int = when (edge) {
    BackEventCompat.EDGE_LEFT -> 1
    BackEventCompat.EDGE_RIGHT -> -1
    else -> layoutDirection
}

/** Rounds a page's corners while it leaves, so that a page pulled back looks like a card over the one below. */
@Composable
internal fun BackCard(content: @Composable () -> Unit) {
    val transition = LocalNavAnimatedContentScope.current.transition
    val corner by transition.animateDp(
        transitionSpec = { tween(BackMillis, easing = Easing(::pulled)) },
        label = "backCorner",
    ) { if (it == EnterExitState.PostExit) PulledCorner else 0.dp }
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            shape = RoundedCornerShape(corner)
            clip = corner > 0.dp
        },
    ) { content() }
}
