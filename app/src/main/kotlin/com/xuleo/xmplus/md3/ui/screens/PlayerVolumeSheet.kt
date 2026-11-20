package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.xuleo.xmplus.md3.ui.components.SnapSlider
import kotlin.math.roundToInt

private val VolumeMarkers = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)

/** 0.8 reads as 80. */
internal fun volumePercent(volume: Float) = (volume * 100).roundToInt()

/**
 * The album's volume in 5% steps, applied when the finger lifts, and a mute switch that covers every album. The phone's
 * own volume keys still set the overall level; this one balances an album that was recorded loud or quiet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerVolumeSheet(
    volume: Float,
    muted: Boolean,
    onVolume: (Float) -> Unit,
    onMuted: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var shown by remember(volume) { mutableFloatStateOf(volume) }
    val sheet = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    fun commit(value: Float) {
        if (value != volume) onVolume(value)
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
                    stringResource(R.string.player_volume),
                    Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (shown != 1f) IconButton(onClick = {
                    shown = 1f
                    commit(1f)
                }) {
                    Icon(Icons.Outlined.RestartAlt, stringResource(R.string.player_volume_reset))
                }
            }
            Text(
                stringResource(R.string.player_volume_value, volumePercent(shown)),
                Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
            SnapSlider(
                value = shown,
                onValueChange = { shown = it },
                onValueChangeFinished = { commit(shown) },
                label = stringResource(R.string.player_volume),
                range = 0f..1f,
                step = 0.05f,
                markers = VolumeMarkers,
                markerLabel = { stringResource(R.string.player_volume_value, volumePercent(it)) },
                magnet = 0.03f,
            )
            Text(
                stringResource(R.string.player_volume_scope),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.player_volume_mute), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = muted, onCheckedChange = onMuted)
            }
        }
    }
}