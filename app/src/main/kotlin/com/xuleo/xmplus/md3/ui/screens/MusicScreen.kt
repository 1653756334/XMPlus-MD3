package com.xuleo.xmplus.md3.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.designsystem.XmPreview
import com.xuleo.xmplus.md3.core.designsystem.XmPreviews
import com.xuleo.xmplus.md3.core.plugin.InstalledPlugin
import com.xuleo.xmplus.md3.ui.components.ModeSwitch
import com.xuleo.xmplus.md3.ui.icons.XmIcons

/** 推荐 lists sheets by tag, 榜单 the top lists; a plugin shows under a mode only when it has that method. */
enum class MusicMode(@StringRes val label: Int, val method: String) {
    Recommend(R.string.music_mode_recommend, "getRecommendSheetsByTag"),
    Ranking(R.string.music_mode_ranking, "getTopLists"),
}

/**
 * The 音乐 tab: mode headings as on 听书, the enabled plugins of that mode as tabs under them. Without plugins it shows
 * only the way to import one. The plugin content arrives in M2.
 */
@Composable
fun MusicScreen(plugins: List<InstalledPlugin>?, onPlugins: () -> Unit, onSearch: (() -> Unit)? = null) {
    var mode by rememberSaveable { mutableStateOf(MusicMode.Recommend) }
    val usable = plugins.orEmpty().filter { it.enabled }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            ModeSwitch(MusicMode.entries, mode, { stringResource(it.label) }, { mode = it }, Modifier.weight(1f))
            if (onSearch != null && usable.isNotEmpty()) {
                IconButton(onSearch, Modifier.padding(end = 4.dp)) { Icon(XmIcons.Filled.Search, stringResource(R.string.search_submit)) }
            }
        }
        when {
            plugins == null -> Unit
            usable.isEmpty() -> MusicMessage(R.string.music_empty, R.string.music_import_plugin, onPlugins, filled = true)
            else -> {
                val supporting = usable.filter { mode.method in it.info.methods }
                if (supporting.isEmpty()) {
                    MusicMessage(R.string.music_unsupported, R.string.plugins_title, onPlugins, filled = false)
                } else {
                    var tab by rememberSaveable(mode) { mutableIntStateOf(0) }
                    val selected = tab.coerceIn(0, supporting.lastIndex)
                    PrimaryScrollableTabRow(selected, containerColor = Color.Transparent, edgePadding = XmLayout.PagePadding, divider = {}) {
                        supporting.forEachIndexed { index, plugin ->
                            Tab(index == selected, onClick = { tab = index }, text = { Text(plugin.platform, maxLines = 1) })
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

/** One line and one action, centred in the space under the header. */
@Composable
private fun ColumnScope.MusicMessage(@StringRes text: Int, @StringRes action: Int, onAction: () -> Unit, filled: Boolean) {
    Column(
        Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(XmIcons.Filled.MusicNote, contentDescription = null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        if (filled) Button(onAction) { Text(stringResource(action)) }
        else TextButton(onAction) { Text(stringResource(action)) }
        // Keeps the message optically centred above the mini player and navigation bar.
        Spacer(Modifier.height(48.dp))
    }
}

@XmPreviews
@Composable
private fun MusicEmptyPreview() = XmPreview { MusicScreen(emptyList(), {}) }
