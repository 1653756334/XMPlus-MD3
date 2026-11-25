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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.media.SleepTimerState
import com.xuleo.xmplus.md3.ui.components.SheetActions
import com.xuleo.xmplus.md3.ui.components.SheetHeader
import com.xuleo.xmplus.md3.ui.components.SheetPresets
import com.xuleo.xmplus.md3.ui.components.SheetSetting
import com.xuleo.xmplus.md3.ui.components.Stepper
import com.xuleo.xmplus.md3.ui.components.formatDuration
import kotlinx.coroutines.launch

private const val MinSleepMinutes = 1
private const val MaxSleepMinutes = 120

/** One tap on the sheet's grid: a length in minutes, or the end of the episode. */
private sealed interface SleepChoice {
    data class Minutes(val minutes: Int) : SleepChoice
    data object EpisodeEnd : SleepChoice
}

private val SleepChoices = listOf(15, 30, 45, 60, 90).map(SleepChoice::Minutes) + SleepChoice.EpisodeEnd

/**
 * The common lengths and 本集播完 as a grid; one tap starts that timer and closes the sheet. Any other length is set on
 * the stepper, in whole minutes from 1 to 120, and started from the footer; it opens at the length last started on this
 * device. The header says what is running.
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
    /** The preset and status for pausing at the end of what is playing: the episode, or the song. */
    @androidx.annotation.StringRes endPreset: Int = R.string.player_sleep_preset_episode_end,
    @androidx.annotation.StringRes endStatus: Int = R.string.player_sleep_status_episode_end,
) {
    var minutes by remember { mutableIntStateOf(initialMinutes.coerceIn(MinSleepMinutes, MaxSleepMinutes)) }
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
    val remaining = timer.remainingMs
    val status = when {
        remaining != null -> stringResource(R.string.player_sleep_remaining, formatDuration(remaining / 1000.0))
        timer.atEpisodeEnd -> stringResource(endStatus)
        else -> null
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
            SheetHeader(stringResource(R.string.player_sleep), status)
            SheetPresets(
                options = SleepChoices,
                selected = if (timer.atEpisodeEnd) SleepChoice.EpisodeEnd else null,
                label = { choice ->
                    when (choice) {
                        is SleepChoice.Minutes -> stringResource(R.string.player_sleep_minutes, choice.minutes)
                        SleepChoice.EpisodeEnd -> stringResource(endPreset)
                    }
                },
                onSelect = { choice ->
                    when (choice) {
                        is SleepChoice.Minutes -> choose { onMinutes(choice.minutes) }
                        SleepChoice.EpisodeEnd -> choose(onEpisodeEnd)
                    }
                },
                columns = 3,
            )
            SheetSetting(stringResource(R.string.player_sleep_custom)) {
                Stepper(
                    value = minutes,
                    range = MinSleepMinutes..MaxSleepMinutes,
                    onValue = { minutes = it },
                    text = stringResource(R.string.player_sleep_minutes, minutes),
                    name = stringResource(R.string.player_sleep_length),
                )
            }
            SheetActions(
                secondary = stringResource(R.string.player_sleep_off),
                secondaryEnabled = timer.active,
                onSecondary = { choose(onOff) },
                primary = stringResource(R.string.player_sleep_start),
                onPrimary = { choose { onMinutes(minutes) } },
            )
        }
    }
}