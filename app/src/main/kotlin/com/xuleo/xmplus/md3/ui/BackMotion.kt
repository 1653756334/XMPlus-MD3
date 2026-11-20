package com.xuleo.xmplus.md3.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventTransitionState

/*
 * A page opens by sliding in from the end side while the one below drifts a third of the way out. Back is that slide
 * run the other way, toward the side a swipe began at: from the left edge the page goes off to the right, from the right
 * edge off to the left, and the back button sends it toward the reading end. The page below comes back from the other
 * side. A swipe moves the page with the finger; a press plays the same slide.
 */

/** [direction] is +1 when pages open from the right, as they do left to right. */
internal fun openTransform(direction: Int, spatial: FiniteAnimationSpec<IntOffset>, effects: FiniteAnimationSpec<Float>): ContentTransform =
    (slideInHorizontally(spatial) { it * direction } + fadeIn(effects)) togetherWith
        (slideOutHorizontally(spatial) { -it * direction / 3 } + fadeOut(effects))

/** [direction] is +1 to send the page off to the right, as a swipe from the left edge does, and -1 to the left. */
internal fun backTransform(direction: Int, spatial: FiniteAnimationSpec<IntOffset>, effects: FiniteAnimationSpec<Float>): ContentTransform =
    (slideInHorizontally(spatial) { -it * direction / 3 } + fadeIn(effects)) togetherWith
        (slideOutHorizontally(spatial) { it * direction } + fadeOut(effects))

/** The page follows the edge the swipe began at. A press of the back button sends it toward the reading end. */
internal fun backDirection(edge: Int, layoutDirection: Int): Int = when (edge) {
    NavigationEvent.EDGE_LEFT -> 1
    NavigationEvent.EDGE_RIGHT -> -1
    else -> layoutDirection
}

/** Read inside onBack: the dispatcher publishes Idle after the completion callback returns.
 * Unlike a value collected during composition, this also sees a swipe committed before the first preview frame. */
internal fun NavigationEventDispatcher?.committingBackEdge(): Int {
    val gesture = this?.transitionState?.value as? NavigationEventTransitionState.InProgress
    return if (gesture?.direction == NavigationEventTransitionState.TRANSITIONING_BACK) gesture.latestEvent.swipeEdge
    else NavigationEvent.EDGE_NONE
}