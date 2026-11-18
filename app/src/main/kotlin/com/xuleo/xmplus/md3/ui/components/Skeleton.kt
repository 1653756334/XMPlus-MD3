package com.xuleo.xmplus.md3.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/** A softly pulsing block that stands in for content that has not arrived. */
@Composable
fun Modifier.skeleton(shape: Shape = MaterialTheme.shapes.small): Modifier {
    val alpha by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.4f, targetValue = 0.9f, label = "alpha",
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
    )
    return background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha), shape)
}