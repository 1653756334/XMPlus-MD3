package com.xuleo.xmplus.md3.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** One setting in a player sheet: its name and value on one line, with the control under them. */
@Composable
internal fun SheetSetting(name: String, value: String, control: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        control()
    }
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
