package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.ui.components.ApplyWhenSettled
import com.xuleo.xmplus.md3.ui.components.SheetHeader
import com.xuleo.xmplus.md3.ui.components.SheetPresets
import com.xuleo.xmplus.md3.ui.components.Stepper
import kotlin.math.roundToInt

/** The common levels, in percent, one tap away under the stepper. */
private val VolumePresets = listOf(25, 50, 75, 100)
private const val VolumeStep = 5
private const val FullVolume = 100
private const val MutedAlpha = 0.38f

/** 0.8 reads as 80. */
internal fun volumePercent(volume: Float) = (volume * 100).roundToInt()

/**
 * The album's volume in 5% steps, with the common levels one tap away, and a mute switch that covers every album. The
 * level a held button stops on applies a moment later. The phone's own volume keys still set the overall level; this one
 * balances an album that was recorded loud or quiet. While muted the level is dimmed but can still be set.
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
    var shown by remember(volume) { mutableIntStateOf(volumePercent(volume)) }
    val sheet = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    fun commit(value: Int) {
        if (value != volumePercent(volume)) onVolume(value / 100f)
    }
    ApplyWhenSettled(shown, ::commit)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = { WindowInsets.safeDrawing },
    ) {
        Column(
            Modifier.fillMaxWidth()
                .padding(start = XmLayout.PagePadding, end = XmLayout.PagePadding, bottom = XmLayout.ItemGap),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val name = stringResource(R.string.player_volume)
            SheetHeader(name) {
                if (shown != FullVolume) IconButton(onClick = {
                    shown = FullVolume
                    commit(FullVolume)
                }) {
                    Icon(Icons.Outlined.RestartAlt, stringResource(R.string.player_volume_reset))
                }
            }
            Stepper(
                value = shown,
                range = 0..FullVolume,
                onValue = { shown = it },
                text = stringResource(R.string.player_volume_value, shown),
                name = name,
                modifier = Modifier.align(Alignment.CenterHorizontally).alpha(if (muted) MutedAlpha else 1f),
                step = VolumeStep,
                large = true,
            )
            SheetPresets(
                options = VolumePresets,
                selected = shown,
                label = { stringResource(R.string.player_volume_value, it) },
                onSelect = {
                    shown = it
                    commit(it)
                },
            )
            Text(
                stringResource(R.string.player_volume_scope),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth().toggleable(value = muted, role = Role.Switch, onValueChange = onMuted),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.player_volume_mute), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = muted, onCheckedChange = null)
            }
        }
    }
}