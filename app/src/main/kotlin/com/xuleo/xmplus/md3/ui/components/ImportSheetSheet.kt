@file:OptIn(ExperimentalMaterial3Api::class)

package com.xuleo.xmplus.md3.ui.components

import android.content.ClipboardManager
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.media.MusicLibrary
import com.xuleo.xmplus.md3.core.model.MusicItem
import com.xuleo.xmplus.md3.core.plugin.ImportedSheet
import com.xuleo.xmplus.md3.core.plugin.InstalledPlugin
import com.xuleo.xmplus.md3.ui.icons.XmIcons
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** What a link brings back: a sheet's songs or one song, as MusicFree's two import methods. */
private enum class ImportMode(@StringRes val label: Int, val method: String) {
    Sheet(R.string.music_type_sheet, "importMusicSheet"),
    Song(R.string.music_tab_songs, "importMusicItem"),
}

/**
 * The link in [text]: what was shared is usually a sentence with the link inside it, so the first http(s) address is
 * taken, and its host is kept for choosing the plugin. Null when the text holds no link.
 */
private val UrlPattern = Regex("""https?://[^\s　"」』】》〉。，]+""", RegexOption.IGNORE_CASE)

private fun linkIn(text: String): String? = UrlPattern.find(text)?.value?.trimEnd('.', ',', ')', ']', '!')

/** The bare host of a link, without `www.`, lower case; null when it is not a link. */
private fun hostOf(link: String): String? = runCatching {
    java.net.URI(link).host?.lowercase()?.removePrefix("www.")
}.getOrNull()?.takeIf { it.isNotEmpty() }

/**
 * The plugin a [link] belongs to: the capable one whose platform name or installer package names the link's host or is
 * named by it, as `kuwo` matches `kuwo.cn` and `bilibili` matches `bilibili.com`. A host matching nothing leaves the
 * choice alone. A tie goes to the plugin already chosen.
 */
private fun pluginFor(link: String, capable: List<InstalledPlugin>, chosen: String?): InstalledPlugin? {
    val host = hostOf(link) ?: return null
    val hits = capable.filter { candidate ->
        val platform = candidate.info.platform.lowercase()
        platform.isNotEmpty() && (host.contains(platform) || platform.contains(host))
    }
    return hits.firstOrNull { it.platform == chosen } ?: hits.firstOrNull()
}

/** A plugin's hints for [method], as its `hints` field declares them. */
private fun InstalledPlugin.hints(method: String): List<String> {
    val declared = info.declared["hints"] as? JsonObject ?: return emptyList()
    return (declared[method] as? JsonArray).orEmpty()
        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { text -> text.isNotBlank() } }
}

/**
 * 从链接导入, as MusicFree's import panel: a link field with 粘贴, the plugins that can import (chips) and the chosen
 * plugin's hints, then 导入. A sheet's songs become a new playlist that opens; a single song is played or added to a
 * playlist. A plugin that finds nothing, or fails, is reported in one line with 重试 and nothing is created.
 */
@Composable
fun ImportSheetSheet(
    plugins: List<InstalledPlugin>,
    library: MusicLibrary,
    onImportSheet: suspend (platform: String, url: String) -> ImportedSheet,
    onImportItem: suspend (platform: String, url: String) -> MusicItem?,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit,
    onPlay: (MusicItem) -> Unit,
    onPlugins: () -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(ImportMode.Sheet) }
    var url by rememberSaveable { mutableStateOf("") }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<MusicItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val capable = plugins.filter { it.enabled && mode.method in it.info.methods }
    val plugin = capable.firstOrNull { it.platform == chosen } ?: capable.firstOrNull()
    val link = url.trim()
    // A shared message is pasted whole; the link inside it is what a plugin reads, and its host picks the plugin.
    val found = remember(link, capable) { linkIn(link) }
    val value = found ?: link
    LaunchedEffect(found, capable) {
        if (found != null) pluginFor(found, capable, chosen)?.let { chosen = it.platform }
    }

    fun run() {
        val platform = plugin?.platform ?: return
        if (value.isEmpty() || busy) return
        busy = true
        failed = false
        scope.launch {
            try {
                if (mode == ImportMode.Sheet) {
                    val found = onImportSheet(platform, value)
                    if (found.songs.isEmpty()) {
                        failed = true
                    } else {
                        val name = found.title
                            ?: found.songs.firstOrNull { it.album.isNotBlank() }?.album
                            ?: platform
                        val id = library.createSheet(name)
                        library.addSongs(id, found.songs)
                        onCreated(id)
                    }
                } else {
                    val song = onImportItem(platform, value)
                    if (song == null) failed = true else picked = song
                }
            } catch (_: Exception) {
                failed = true
            } finally {
                busy = false
            }
        }
    }

    picked?.let { song ->
        // One song: play it now or keep it in a playlist, then the import is over.
        if (adding) {
            AddToSheetSheet(listOf(song), onDismiss = { adding = false }, onDone = onDismiss)
            return
        }
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentWindowInsets = { WindowInsets.safeDrawing },
        ) {
            Text(
                song.title.ifBlank { song.artist }, Modifier.padding(horizontal = XmLayout.PagePadding),
                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            LazyColumn(Modifier.fillMaxWidth()) {
                item(key = "play") { PickedRow(XmIcons.Outlined.PlayArrow, R.string.action_play) { onDismiss(); onPlay(song) } }
                item(key = "sheet") { PickedRow(XmIcons.Outlined.PlaylistAdd, R.string.music_add_to_sheet) { adding = true } }
            }
        }
        return
    }

    if (capable.isEmpty()) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentWindowInsets = { WindowInsets.safeDrawing },
        ) {
            Text(
                stringResource(R.string.library_sheet_import), Modifier.padding(horizontal = XmLayout.PagePadding),
                style = MaterialTheme.typography.titleMedium,
            )
            Column(
                Modifier.fillMaxWidth().padding(XmLayout.PagePadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
            ) {
                Text(
                    stringResource(R.string.music_import_none), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onPlugins) { Text(stringResource(R.string.music_import_plugin)) }
            }
        }
        return
    }

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = { WindowInsets.safeDrawing },
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.library_sheet_import), Modifier.padding(horizontal = XmLayout.PagePadding),
                style = MaterialTheme.typography.titleMedium,
            )
            ModeSwitch(
                ImportMode.entries, mode, { stringResource(it.label) }, { mode = it; failed = false },
                textStyle = MaterialTheme.typography.titleSmall,
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    url, { url = it; failed = false }, Modifier.weight(1f), singleLine = true, enabled = !busy,
                    label = { Text(stringResource(R.string.music_import_link), maxLines = 1) },
                    // A pasted message gets shorter as the link is found in it, so the field shows what will be read.
                    supportingText = if (found != null && found != link) {
                        { Text(found, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    } else null,
                )
                TextButton(
                    onClick = {
                        val text = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                            ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                        if (!text.isNullOrBlank()) url = text.trim()
                    },
                    enabled = !busy,
                ) { Text(stringResource(R.string.music_import_paste)) }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = XmLayout.PagePadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                capable.forEach { candidate ->
                    FilterChip(
                        selected = candidate.platform == plugin?.platform,
                        onClick = { chosen = candidate.platform; failed = false },
                        label = { Text(candidate.platform, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        enabled = !busy,
                    )
                }
            }
            val hints = plugin?.hints(mode.method).orEmpty()
            if (hints.isNotEmpty()) {
                Text(
                    hints.joinToString(" · "),
                    Modifier.padding(horizontal = XmLayout.PagePadding), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = XmLayout.PagePadding).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
            ) {
                if (failed) {
                    Text(
                        stringResource(R.string.music_import_failed), Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = ::run, enabled = !busy) { Text(stringResource(R.string.action_retry)) }
                } else {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            stringResource(R.string.music_importing), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
                Button(onClick = ::run, enabled = value.isNotEmpty() && !busy) { Text(stringResource(R.string.library_sheet_import)) }
            }
        }
    }
}

@Composable
private fun PickedRow(icon: androidx.compose.ui.graphics.vector.ImageVector, @StringRes label: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp)
            .padding(horizontal = XmLayout.PagePadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap),
    ) {
        Icon(icon, contentDescription = null)
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
    }
}
