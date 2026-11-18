package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.media.SleepTimerState
import kotlinx.coroutines.launch

private val SleepMinutes = listOf(15, 30, 60, 90)

/** Choose when playback pauses by itself. Picking an option closes the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSleepSheet(
    timer: SleepTimerState,
    onMinutes: (Int) -> Unit,
    onEpisodeEnd: () -> Unit,
    onOff: () -> Unit,
    onDismiss: () -> Unit,
) {
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
            Text(
                stringResource(R.string.player_sleep),
                Modifier.padding(horizontal = XmLayout.PagePadding, vertical = 4.dp).semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            SleepOption(stringResource(R.string.player_sleep_episode_end), timer.atEpisodeEnd) { choose(onEpisodeEnd) }
            SleepMinutes.forEach { minutes ->
                SleepOption(stringResource(R.string.player_sleep_minutes, minutes), selected = false) {
                    choose { onMinutes(minutes) }
                }
            }
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