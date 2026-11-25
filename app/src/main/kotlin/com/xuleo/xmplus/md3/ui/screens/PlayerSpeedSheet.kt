package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.ui.icons.XmIcons
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.media.PlaybackMaxSpeed
import com.xuleo.xmplus.md3.core.media.PlaybackMinSpeed
import com.xuleo.xmplus.md3.ui.components.ApplyWhenSettled
import com.xuleo.xmplus.md3.ui.components.SheetHeader
import com.xuleo.xmplus.md3.ui.components.SheetPresets
import com.xuleo.xmplus.md3.ui.components.Stepper
import kotlin.math.roundToInt

/** The common speeds, in tenths, one tap away under the stepper. */
private val SpeedPresets = listOf(8, 10, 12, 15, 20)
private const val NormalTenths = 10

/** 1.0 reads as 1, 1.25 as 1.25. */
internal fun speedLabel(value: Float) = if (value % 1f == 0f) value.toInt().toString() else value.toString()

private fun tenths(speed: Float) = (speed * 10).roundToInt()

private fun tenthsLabel(tenths: Int) = speedLabel(tenths / 10f)

/**
 * The speed in 0.1× steps from 0.5× to 3×, with the common speeds one tap away. Holding − or + runs through the
 * speeds, and the one it stops on applies a moment later, so the meter is not resampled at every step. The sheet stays
 * open while you listen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSpeedSheet(
    speed: Float,
    onSpeed: (Float) -> Unit,
    onDismiss: () -> Unit,
    /** What the speed applies to; null hides the note. */
    @androidx.annotation.StringRes scope: Int? = R.string.player_speed_scope,
) {
    var shown by remember(speed) { mutableIntStateOf(tenths(speed)) }
    val sheet = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    fun commit(value: Int) {
        if (value != tenths(speed)) onSpeed(value / 10f)
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
            val name = stringResource(R.string.player_speed)
            SheetHeader(name) {
                if (shown != NormalTenths) IconButton(onClick = {
                    shown = NormalTenths
                    commit(NormalTenths)
                }) {
                    Icon(XmIcons.Outlined.RestartAlt, stringResource(R.string.player_speed_reset))
                }
            }
            Stepper(
                value = shown,
                range = tenths(PlaybackMinSpeed)..tenths(PlaybackMaxSpeed),
                onValue = { shown = it },
                text = stringResource(R.string.player_speed_value, tenthsLabel(shown)),
                name = name,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                large = true,
            )
            SheetPresets(
                options = SpeedPresets,
                selected = shown,
                label = { stringResource(R.string.player_speed_value, tenthsLabel(it)) },
                onSelect = {
                    shown = it
                    commit(it)
                },
            )
            if (scope != null) Text(
                stringResource(scope),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}