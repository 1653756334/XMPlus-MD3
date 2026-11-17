package com.xuleo.xmplus.md3.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.settings.AppLanguage
import com.xuleo.xmplus.md3.settings.setAppLanguage

@StringRes
private fun AppLanguage.label(): Int = when (this) {
    AppLanguage.System -> R.string.language_system
    AppLanguage.Chinese -> R.string.language_zh
    AppLanguage.English -> R.string.language_en
}

@Composable
fun MeScreen() {
    var language by remember { mutableStateOf(AppLanguage.current()) }
    var showDialog by remember { mutableStateOf(false) }

    TabScaffold(com.xuleo.xmplus.md3.ui.Destination.Me.label) { padding ->
        Column(Modifier.padding(padding)) {
            ListItem(
                onClick = { showDialog = true },
                leadingContent = { Icon(Icons.Outlined.Language, contentDescription = null) },
                supportingContent = { Text(stringResource(language.label())) },
            ) {
                Text(stringResource(R.string.setting_language))
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.setting_language)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    AppLanguage.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = option == language,
                                    role = Role.RadioButton,
                                    onClick = {
                                        showDialog = false
                                        language = option
                                        setAppLanguage(option)
                                    },
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == language, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Text(stringResource(option.label()))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}