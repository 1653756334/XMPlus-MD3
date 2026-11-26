package com.xuleo.xmplus.md3.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.media.MusicMediaCache
import com.xuleo.xmplus.md3.ui.icons.XmIcons
import kotlinx.coroutines.launch

/** 歌曲缓存: how much space songs heard lately may take, and a way to free it. */
@Composable
internal fun MusicCacheSettings(cache: MusicMediaCache) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var used by rememberSaveable { mutableLongStateOf(-1L) }
    var limit by rememberSaveable { mutableLongStateOf(cache.limit) }
    var open by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(open) { if (!open) used = cache.usedBytes() }
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)

    ListItem(
        onClick = { open = true },
        leadingContent = { Icon(XmIcons.Filled.MusicNote, contentDescription = null) },
        supportingContent = {
            Text(if (used < 0) size(limit) else stringResource(R.string.music_cache_usage, size(used), size(limit)))
        },
        trailingContent = { Icon(XmIcons.Outlined.ChevronRight, contentDescription = null) },
    ) { Text(stringResource(R.string.music_cache_title)) }

    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.music_cache_title)) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                MusicMediaCache.Limits.forEach { option ->
                    Row(Modifier.fillMaxWidth().selectable(option == limit, role = Role.RadioButton, onClick = {
                        limit = option
                        scope.launch { cache.setLimit(option); used = cache.usedBytes() }
                    }).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = option == limit, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(size(option))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { scope.launch { cache.clear(); used = 0 }; open = false }, enabled = used != 0L) {
                Text(stringResource(R.string.action_clear))
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_close)) } },
    )
}
