package com.xuleo.xmplus.md3.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.library.LibraryToggle

/**
 * Shows [checkedLabel] as an outlined button when the account has it, [uncheckedLabel] as a tonal one otherwise.
 * [compact] is the short form that sits beside a title; the default is a button of its own.
 */
@Composable
fun LibraryToggleButton(
    state: LibraryToggle,
    @StringRes checkedLabel: Int,
    @StringRes uncheckedLabel: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val enabled = state.on != null && !state.busy
    val size = if (compact) Modifier.height(32.dp) else Modifier
    val padding = if (compact) PaddingValues(horizontal = 14.dp) else ButtonDefaults.ContentPadding
    Column(modifier, horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.on == true) {
            OutlinedButton(onClick = onClick, modifier = size, enabled = enabled, contentPadding = padding) {
                Text(stringResource(checkedLabel))
            }
        } else {
            FilledTonalButton(onClick = onClick, modifier = size, enabled = enabled, contentPadding = padding) {
                Text(stringResource(uncheckedLabel))
            }
        }
        if (state.failed) {
            Text(stringResource(R.string.library_action_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}