package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.media.SleepTimerState
import com.xuleo.xmplus.md3.ui.components.SnapSlider
import kotlinx.coroutines.launch

/** The common lengths, labelled under the slider. */
private val SleepMarkers = listOf(15f, 30f, 60f, 90f, 120f)
private const val SleepDefaultMinutes = 30f

/**
 * Drag to any whole number of minutes from 1 to 120; a drag lands on a common length when it gets close. Start sets
 * the timer and closes the sheet. Episode end and turning the timer off act at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSleepSheet(
    timer: SleepTimerState,
    onMinutes: (Int) -> Unit,
    onEpisodeEnd: () -> Unit,
    onOff: () -> Unit,
    onDismiss: () -> Unit,
) {
    var minutes by remember { mutableFloatStateOf(SleepDefaultMinutes) }
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
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = XmLayout.ItemGap)) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.player_sleep),
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.player_sleep_minutes, minutes.toInt()),
                    Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                SnapSlider(
                    value = minutes,
                    onValueChange = { minutes = it },
                    onValueChangeFinished = {},
                    label = stringResource(R.string.player_sleep),
                    range = 1f..120f,
                    step = 1f,
                    markers = SleepMarkers,
                    markerLabel = { it.toInt().toString() },
                    magnet = 2f,
                )
                Button(
                    onClick = { choose { onMinutes(minutes.toInt()) } },
                    Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.player_sleep_start))
                }
            }
            SleepOption(stringResource(R.string.player_sleep_episode_end), timer.atEpisodeEnd) { choose(onEpisodeEnd) }
            if (timer.active) SleepOption(stringResource(R.string.player_sleep_off), selected = false, onClick = { choose(onOff) })
        }
    }
}

@Composable
private fun SleepOption(label: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        trailingContent = if (selected) ({ Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) }) else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) {
        Text(label)
    }
}