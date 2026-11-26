package com.xuleo.xmplus.md3.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmPreview
import com.xuleo.xmplus.md3.core.designsystem.XmPreviews
import com.xuleo.xmplus.md3.core.model.MusicSheet
import com.xuleo.xmplus.md3.core.plugin.InstalledPlugin
import com.xuleo.xmplus.md3.music.MusicBrowseViewModel
import com.xuleo.xmplus.md3.ui.components.ModeSwitch
import com.xuleo.xmplus.md3.ui.components.XmMenu
import com.xuleo.xmplus.md3.ui.components.XmMenuChoice
import com.xuleo.xmplus.md3.ui.icons.XmIcons

/** 推荐 lists sheets by tag, 榜单 the top lists; a plugin is offered for a mode only when it has that method. */
enum class MusicMode(@StringRes val label: Int, val method: String) {
    Recommend(R.string.music_mode_recommend, "getRecommendSheetsByTag"),
    Ranking(R.string.music_mode_ranking, "getTopLists"),
}

/**
 * The 音乐 tab: mode headings as on 听书, and at the end of the same row the plugin the content comes from, changed
 * from a menu since it rarely changes. Both modes share the chosen plugin; a mode it cannot show uses the first
 * plugin that can, without changing the choice. Without plugins it shows only the way to import one.
 * [onOpenSheet] says whether the sheet is a ranking, whose songs come from another method.
 */
@Composable
fun MusicScreen(
    plugins: List<InstalledPlugin>?,
    onPlugins: () -> Unit,
    onSearch: (() -> Unit)? = null,
    source: String? = null,
    onSource: (String) -> Unit = {},
    browse: MusicBrowseViewModel? = null,
    onOpenSheet: (MusicSheet, Boolean) -> Unit = { _, _ -> },
) {
    var mode by rememberSaveable { mutableStateOf(MusicMode.Recommend) }
    val usable = plugins.orEmpty().filter { it.enabled }
    val supporting = usable.filter { mode.method in it.info.methods }
    val current = supporting.firstOrNull { it.platform == source } ?: supporting.firstOrNull()
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            ModeSwitch(MusicMode.entries, mode, { stringResource(it.label) }, { mode = it }, Modifier.weight(1f))
            if (current != null) SourcePicker(supporting, current.platform, onSource)
            if (onSearch != null && usable.isNotEmpty()) {
                IconButton(onSearch) { Icon(XmIcons.Filled.Search, stringResource(R.string.search_submit)) }
            }
            Spacer(Modifier.width(4.dp))
        }
        when {
            plugins == null -> Unit
            usable.isEmpty() -> MusicMessage(R.string.music_empty, R.string.music_import_plugin, onPlugins, filled = true)
            current == null -> MusicMessage(R.string.music_unsupported, R.string.plugins_title, onPlugins, filled = false)
            browse == null -> Box(Modifier.weight(1f).fillMaxWidth())
            else -> Box(Modifier.weight(1f).fillMaxWidth()) {
                when (mode) {
                    MusicMode.Recommend -> RecommendPane(current.platform, browse) { onOpenSheet(it, false) }
                    MusicMode.Ranking -> RankingPane(current.platform, browse) { onOpenSheet(it, true) }
                }
            }
        }
    }
}

/**
 * The plugin's name in a tonal pill, opening the expressive menu of the others: one rounded group whose chosen item
 * is filled and checked. Just the name when there is no other.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SourcePicker(plugins: List<InstalledPlugin>, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val description = stringResource(R.string.music_source_value, selected)
    val single = plugins.size <= 1
    val arrow by animateFloatAsState(if (open) 180f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "arrow")
    Box {
        Surface(
            onClick = { open = true },
            enabled = !single,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.heightIn(min = 32.dp).semantics { contentDescription = if (single) selected else description },
        ) {
            Row(
                Modifier.padding(start = 14.dp, end = if (single) 14.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selected, Modifier.widthIn(max = 120.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                if (!single) {
                    Icon(XmIcons.Filled.ArrowDropDown, contentDescription = null, Modifier.size(20.dp).graphicsLayer { rotationZ = arrow })
                }
            }
        }
        XmMenu(open, onDismissRequest = { open = false }) {
            plugins.forEachIndexed { index, plugin ->
                val chosen = plugin.platform == selected
                XmMenuChoice(plugin.platform, chosen, { open = false; if (!chosen) onSelect(plugin.platform) }, index, plugins.size)
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
