package com.xuleo.xmplus.md3.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** The four bars of a playing mark: thin, rounded, and rising and falling as one wave, as QQ 音乐 shows it. */
@Composable
fun PlayingBars(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val context = LocalContext.current
    // A still picture where the system has animations turned off.
    val animated = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val transition = rememberInfiniteTransition(label = "playing")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1_100, easing = LinearEasing)),
        label = "phase",
    )
    val at = if (animated) phase else 0.35f
    Canvas(modifier) {
        val bars = 4
        val width = size.width / (bars * 2f - 1f)
        val gap = width
        val low = size.height * 0.22f
        val high = size.height
        for (index in 0 until bars) {
            // Each bar starts a little later than the one before it, so the marks ripple across.
            val wave = sin((at + index * 0.16f) * 2f * PI.toFloat())
            val height = low + (high - low) * (0.5f + 0.5f * wave)
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (width + gap), size.height - height),
                size = Size(width, height),
                cornerRadius = CornerRadius(width / 2f),
            )
        }
    }
}

/** Room a [PlayingBars] mark takes in a list row. */
val PlayingMarkSize = 18.dp
