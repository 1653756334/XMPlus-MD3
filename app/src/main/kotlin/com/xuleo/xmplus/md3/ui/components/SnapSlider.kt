package com.xuleo.xmplus.md3.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Material 3 draws the track half a thumb (4 dp wide) in from each end. */
private val ThumbInset = 2.dp

/**
 * A slider that moves in fixed [step]s and pulls toward a few key [markers]. Each marker is labelled under the track and
 * tapping its label sets that value. A drag lands on a marker when it gets within [magnet] of it.
 */
@Composable
fun SnapSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    label: String,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    markers: List<Float>,
    markerLabel: @Composable (Float) -> String,
    modifier: Modifier = Modifier,
    magnet: Float = 0f,
) {
    fun snap(raw: Float): Float {
        val near = markers.minByOrNull { abs(it - raw) }?.takeIf { abs(it - raw) <= magnet }
        val stepped = range.start + ((raw - range.start) / step).roundToInt() * step
        // Rounding to thousandths keeps 1.2 from arriving as 1.2000001 after the step arithmetic.
        return ((near ?: stepped).coerceIn(range.start, range.endInclusive) * 1000f).roundToInt() / 1000f
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Slider(
            value = value,
            onValueChange = { onValueChange(snap(it)) },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
        Layout(
            content = {
                markers.forEach { marker ->
                    val picked = abs(marker - value) < step / 2
                    Text(
                        markerLabel(marker),
                        Modifier.clip(MaterialTheme.shapes.small)
                            .clickable(role = Role.Button) {
                                onValueChange(marker)
                                onValueChangeFinished()
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = ThumbInset),
        ) { measurables, constraints ->
            val texts = measurables.map { it.measure(Constraints()) }
            val width = constraints.maxWidth
            val height = texts.maxOfOrNull { it.height } ?: 0
            layout(width, height) {
                texts.forEachIndexed { i, text ->
                    val fraction = (markers[i] - range.start) / (range.endInclusive - range.start)
                    val x = (fraction * width - text.width / 2f).roundToInt()
                        .coerceIn(0, (width - text.width).coerceAtLeast(0))
                    text.placeRelative(x, 0)
                }
            }
        }
    }
}