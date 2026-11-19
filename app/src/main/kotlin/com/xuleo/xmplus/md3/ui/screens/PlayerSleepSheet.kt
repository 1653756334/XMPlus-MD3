package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.media.SleepTimerState
import com.xuleo.xmplus.md3.ui.components.SheetActions
import com.xuleo.xmplus.md3.ui.components.SheetSetting
import com.xuleo.xmplus.md3.ui.components.SnapSlider
import kotlinx.coroutines.launch

/** The common lengths, labelled under the slider. */
private val SleepMarkers = listOf(15f, 30f, 60f, 90f, 120f)

/**
 * Drag to any whole number of minutes from 1 to 120; a drag lands on a common length when it gets close. The sheet opens at
 * the length last started on this device. 本集播完后 is a chip that acts at once; the footer turns the timer off or starts it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSleepSheet(
    timer: SleepTimerState,
    initialMinutes: Int,
    onMinutes: (Int) -> Unit,
    onEpisodeEnd: () -> Unit,
    onOff: () -> Unit,
    onDismiss: () -> Unit,
) {
    var minutes by remember { mutableFloatStateOf(initialMinutes.toFloat()) }
    val sheet = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    fun choose(action: () -> Unit) {
        if (sheet.targetValue == SheetValue.Hidden) return
        scope.launch {
            sheet.hide()
            if (!sheet.isVisible) {
                onDismiss()
                action()
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = { WindowInsets.safeDrawing },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = XmLayout.PagePadding, end = XmLayout.PagePadding, bottom = XmLayout.ItemGap),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                stringResource(R.string.player_sleep),
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            SheetSetting(stringResource(R.string.player_sleep_length), stringResource(R.string.player_sleep_minutes, minutes.toInt())) {
                SnapSlider(
                    value = minutes,
                    onValueChange = { minutes = it },
                    onValueChangeFinished = {},
                    label = stringResource(R.string.player_sleep_length),
                    range = 1f..120f,
                    step = 1f,
                    markers = SleepMarkers,
                    markerLabel = { it.toInt().toString() },
                    magnet = 2f,
                )
            }
            FilterChip(
                selected = timer.atEpisodeEnd,
                onClick = { choose(onEpisodeEnd) },
                label = { Text(stringResource(R.string.player_sleep_episode_end)) },
            )
            SheetActions(
                secondary = stringResource(R.string.player_sleep_off),
                secondaryEnabled = timer.active,
                onSecondary = { choose(onOff) },
                primary = stringResource(R.string.player_sleep_start),
                onPrimary = { choose { onMinutes(minutes.toInt()) } },
            )
        }
    }
}
