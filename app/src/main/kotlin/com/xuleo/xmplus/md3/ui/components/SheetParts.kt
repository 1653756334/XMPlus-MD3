package com.xuleo.xmplus.md3.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** How long a value must stay put before it is applied, so holding a stepper button applies once, not at every step. */
private const val SettleMillis = 350L
private val PresetHeight = 40.dp

/** A player sheet's title, a line under it saying what the setting covers, and an action such as reset at the end. */
@Composable
internal fun SheetHeader(title: String, supporting: String? = null, action: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        action()
    }
}

/** One setting in a player sheet: its name at the start and its control, usually a [Stepper], at the end. */
@Composable
internal fun SheetSetting(name: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        control()
    }
}

/**
 * Common values one tap away, as equal cells in rows of [columns]. The cell for [selected] is filled; a value set
 * between them leaves every cell plain.
 */
@Composable
internal fun <T> SheetPresets(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    columns: Int = options.size,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    val active = option == selected
                    Surface(
                        selected = active,
                        onClick = { onSelect(option) },
                        modifier = Modifier.weight(1f).heightIn(min = PresetHeight),
                        shape = MaterialTheme.shapes.medium,
                        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                label(option),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Applies [value] once it has stopped changing for a moment, and again as the sheet closes so the last change is kept.
 * [apply] also receives values that are already set, and decides itself whether anything changes.
 */
@Composable
internal fun <T> ApplyWhenSettled(value: T, apply: (T) -> Unit) {
    val currentApply by rememberUpdatedState(apply)
    val latest by rememberUpdatedState(value)
    LaunchedEffect(value) {
        delay(SettleMillis)
        currentApply(value)
    }
    DisposableEffect(Unit) { onDispose { currentApply(latest) } }
}

/** The footer of a player sheet: a secondary and a primary action of equal width, in the same place on every sheet. */
@Composable
internal fun SheetActions(
    secondary: String,
    secondaryEnabled: Boolean,
    onSecondary: () -> Unit,
    primary: String,
    onPrimary: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onSecondary, Modifier.weight(1f), enabled = secondaryEnabled) { Text(secondary) }
        Button(onClick = onPrimary, Modifier.weight(1f)) { Text(primary) }
    }
}