package com.xuleo.xmplus.md3.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import com.xuleo.xmplus.md3.ui.screens.rememberAnimationsEnabled

/** How far a card shrinks while it is held. */
private const val PressedScale = 0.96f

/**
 * The press effect for cards and tiles: no ripple, the element shrinks a little while held and springs back on release.
 * Without animations it stays still. List rows keep the usual ripple. A 48dp touch target is the caller's job.
 */
fun Modifier.pressable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    enabled: Boolean = true,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val animated = rememberAnimationsEnabled()
    val scale by animateFloatAsState(
        targetValue = if (pressed && animated) PressedScale else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "press",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}
