package com.xuleo.xmplus.md3.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset

/*
 * A page opens by sliding in from the end side while the one below drifts a third of the way out. Back is exactly that
 * slide in reverse, whatever started it: the back button, or a back swipe from either edge of the screen. The page goes
 * off toward the end side it came from and the page below comes back from the start side. A swipe moves it with the
 * finger; a press plays the same slide.
 */

/** [direction] is +1 when pages open from the right, as they do left to right, and -1 right to left. */
internal fun openTransform(direction: Int, spatial: FiniteAnimationSpec<IntOffset>, effects: FiniteAnimationSpec<Float>): ContentTransform =
    (slideInHorizontally(spatial) { it * direction } + fadeIn(effects)) togetherWith
        (slideOutHorizontally(spatial) { -it * direction / 3 } + fadeOut(effects))

/** [openTransform] in reverse, for the same [direction]. */
internal fun backTransform(direction: Int, spatial: FiniteAnimationSpec<IntOffset>, effects: FiniteAnimationSpec<Float>): ContentTransform =
    (slideInHorizontally(spatial) { -it * direction / 3 } + fadeIn(effects)) togetherWith
        (slideOutHorizontally(spatial) { it * direction } + fadeOut(effects))