package com.xuleo.xmplus.md3.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.ui.icons.XmIcons
import com.xuleo.xmplus.md3.R
import kotlinx.coroutines.delay

/** A held button waits this long, then repeats, a little faster each time down to [FastestRepeatMillis]. */
private const val HoldDelayMillis = 400L
private const val FirstRepeatMillis = 150L
private const val FastestRepeatMillis = 30L
private const val RepeatSpeedUp = 0.85f

/** Wide enough for the longest value, so the buttons stay put as the digits change. */
private val ValueWidth = 72.dp
private val LargeValueWidth = 136.dp
private val LargeButton = 56.dp
private val LargeIcon = 28.dp

/**
 * − value +, moving [value] by [step] within [range]. A tap moves one step; holding a button keeps stepping, faster the
 * longer it is held, so a long way needs no slider. A value between steps moves to the nearest step in that direction.
 * [text] is the value as shown and [name] what it is, for the buttons' labels. [large] is a sheet's main value; the
 * default sits at the end of a [SheetSetting] row.
 */
@Composable
internal fun Stepper(
    value: Int,
    range: IntRange,
    onValue: (Int) -> Unit,
    text: String,
    name: String,
    modifier: Modifier = Modifier,
    step: Int = 1,
    large: Boolean = false,
) {
    val down = if (value % step == 0) value - step else value / step * step
    val up = value / step * step + step
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(if (large) 16.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(XmIcons.Filled.Remove, stringResource(R.string.stepper_decrease, name), value > range.first, large) {
            onValue(down.coerceIn(range))
        }
        Text(
            text,
            Modifier.widthIn(min = if (large) LargeValueWidth else ValueWidth).semantics { liveRegion = LiveRegionMode.Polite },
            style = (if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleMedium)
                .copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        StepButton(XmIcons.Filled.Add, stringResource(R.string.stepper_increase, name), value < range.last, large) {
            onValue(up.coerceIn(range))
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, enabled: Boolean, large: Boolean, onStep: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val step by rememberUpdatedState(onStep)
    val haptics = LocalHapticFeedback.current
    // Set once a hold has stepped by itself, so the click that ends the hold adds no step of its own. The click comes
    // before the release reaches this effect, which then clears it for a hold that ended at the limit instead.
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) {
            held = false
            return@LaunchedEffect
        }
        delay(HoldDelayMillis)
        var interval = FirstRepeatMillis
        while (true) {
            held = true
            step()
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            delay(interval)
            interval = (interval * RepeatSpeedUp).toLong().coerceAtLeast(FastestRepeatMillis)
        }
    }
    FilledTonalIconButton(
        onClick = { if (held) held = false else step() },
        modifier = if (large) Modifier.size(LargeButton) else Modifier,
        enabled = enabled,
        interactionSource = interaction,
    ) {
        Icon(icon, label, if (large) Modifier.size(LargeIcon) else Modifier)
    }
}