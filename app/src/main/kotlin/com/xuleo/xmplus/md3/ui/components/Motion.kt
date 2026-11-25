package com.xuleo.xmplus.md3.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Swaps one piece of content for the next as [target] changes: the old one shrinks a little and fades, the new one
 * grows in, and the space between them resizes smoothly. Used where a mode change replaces an icon, a control or a title.
 */
@Composable
fun <S> XmSwap(
    target: S,
    modifier: Modifier = Modifier,
    scale: Float = 0.8f,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable (S) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = scale)) togetherWith
                (fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = scale)) using
                SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
        },
        contentAlignment = contentAlignment,
        label = "XmSwap",
    ) { content(it) }
}
