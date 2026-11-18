package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.media.PlaybackMaxSpeed
import com.xuleo.xmplus.md3.core.media.PlaybackMinSpeed
import com.xuleo.xmplus.md3.ui.components.SnapSlider

/** The common speeds, labelled under the slider. */
private val SpeedMarkers = listOf(0.5f, 1f, 1.5f, 2f, 2.5f, 3f)

/** 1.0 reads as 1, 1.25 as 1.25. */
internal fun speedLabel(value: Float) = if (value % 1f == 0f) value.toInt().toString() else value.toString()

/**
 * Drag to any speed in 0.1× steps from 0.5× to 3×; a drag lands on a common speed when it gets close. The speed takes
 * effect when the finger lifts, so the sheet stays open while you try values.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSpeedSheet(speed: Float, onSpeed: (Float) -> Unit, onDismiss: () -> Unit) {
    var shown by remember(speed) { mutableFloatStateOf(speed) }
    val sheet = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    fun commit(value: Float) {
        if (value != speed) onSpeed(value)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = { WindowInsets.safeDrawing },
    ) {
        Column(
            Modifier.fillMaxWidth()
                .padding(start = XmLayout.PagePadding, end = XmLayout.PagePadding, bottom = XmLayout.ItemGap),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.player_speed),
                    Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (shown != 1f) TextButton(onClick = {
                    shown = 1f
                    commit(1f)
                }) {
                    Text(stringResource(R.string.player_speed_reset))
                }
            }
            Text(
                stringResource(R.string.player_speed_value, speedLabel(shown)),
                Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
            SnapSlider(
                value = shown,
                onValueChange = { shown = it },
                onValueChangeFinished = { commit(shown) },
                label = stringResource(R.string.player_speed),
                range = PlaybackMinSpeed..PlaybackMaxSpeed,
                step = 0.1f,
                markers = SpeedMarkers,
                markerLabel = { stringResource(R.string.player_speed_value, speedLabel(it)) },
            )
        }
    }
}