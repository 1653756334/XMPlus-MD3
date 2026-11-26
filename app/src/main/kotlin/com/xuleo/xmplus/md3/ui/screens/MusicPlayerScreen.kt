@file:OptIn(ExperimentalMaterial3Api::class)

package com.xuleo.xmplus.md3.ui.screens


import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.media.MusicFavorites
import com.xuleo.xmplus.md3.core.media.MusicPlayMode
import com.xuleo.xmplus.md3.core.media.MusicPlayback
import com.xuleo.xmplus.md3.core.media.MusicPlaybackState
import com.xuleo.xmplus.md3.core.media.MusicQuality
import com.xuleo.xmplus.md3.core.media.MusicQueueState
import com.xuleo.xmplus.md3.core.media.PlaybackPhase
import com.xuleo.xmplus.md3.core.model.MusicAlbum
import com.xuleo.xmplus.md3.core.model.MusicArtist
import com.xuleo.xmplus.md3.core.model.MusicItem
import com.xuleo.xmplus.md3.core.model.PluginMedia
import com.xuleo.xmplus.md3.core.plugin.LyricLine
import com.xuleo.xmplus.md3.core.plugin.LyricSource
import com.xuleo.xmplus.md3.core.plugin.MusicCatalog
import com.xuleo.xmplus.md3.core.plugin.MusicComment
import com.xuleo.xmplus.md3.core.plugin.MusicLyrics
import com.xuleo.xmplus.md3.core.plugin.SearchType
import com.xuleo.xmplus.md3.core.plugin.parseLrc
import com.xuleo.xmplus.md3.library.ListPage
import com.xuleo.xmplus.md3.library.ListSection
import com.xuleo.xmplus.md3.music.MusicDetailKind
import com.xuleo.xmplus.md3.ui.components.Avatar
import com.xuleo.xmplus.md3.ui.components.DetailArtworkBackdrop
import com.xuleo.xmplus.md3.ui.components.InlineError
import com.xuleo.xmplus.md3.ui.components.LoadMoreEffect
import com.xuleo.xmplus.md3.ui.components.PagingFooter
import com.xuleo.xmplus.md3.ui.components.SettlingPager
import com.xuleo.xmplus.md3.ui.components.SheetHeader
import com.xuleo.xmplus.md3.ui.components.SongRow
import com.xuleo.xmplus.md3.ui.components.XmMenu
import com.xuleo.xmplus.md3.ui.components.XmMenuItem
import com.xuleo.xmplus.md3.ui.components.XmSwap
import com.xuleo.xmplus.md3.ui.components.formatCount
import com.xuleo.xmplus.md3.ui.components.formatDate
import com.xuleo.xmplus.md3.ui.components.formatDuration
import com.xuleo.xmplus.md3.ui.icons.XmIcons
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** What the music player reads and controls. */
class MusicPlayerDeps(
    val playback: MusicPlayback,
    val lyrics: MusicLyrics,
    val catalog: MusicCatalog,
    val favorites: MusicFavorites,
)

/** The music player's pages, in the order they swipe. 评论 is left out when the song's plugin has no comments. */
private enum class MusicPage(@StringRes val label: Int) {
    Playing(R.string.player_page_playing),
    Lyrics(R.string.player_page_lyrics),
    Comments(R.string.player_page_comments),
}

/** The player for songs: the same full-height sheet as 听书, with 播放, 歌词 and 评论. */
@Composable
internal fun MusicPlayerSheet(
    deps: MusicPlayerDeps,
    onDismiss: () -> Unit,
    onOpenMusic: (MusicDetailKind, PluginMedia) -> Unit,
) {
    val music = deps.playback
    val state by music.state.collectAsStateWithLifecycle()
    val queue by music.queue.collectAsStateWithLifecycle()
    val speed by music.speed.collectAsStateWithLifecycle()
    val sleep by music.sleepTimer.collectAsStateWithLifecycle()
    val liked by deps.favorites.songs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val addedText = stringResource(R.string.music_added)
    val qualityFailed = stringResource(R.string.music_quality_failed)
    val sheet = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    fun closeThen(next: () -> Unit = {}) {
        scope.launch {
            sheet.hide()
            if (!sheet.isVisible) {
                onDismiss()
                next()
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        sheetMaxWidth = Dp.Unspecified,
        shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = Color.Transparent,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        val song = state.song ?: return@ModalBottomSheet
        val lyric = rememberSongLyric(song, deps.lyrics)
        val pages = if (deps.lyrics.supportsComments(song.platform)) MusicPage.entries
        else listOf(MusicPage.Playing, MusicPage.Lyrics)
        val pager = rememberPagerState { pages.size }
        var showQueue by remember { mutableStateOf(false) }
        var showQuality by remember { mutableStateOf(false) }
        var showSleep by remember { mutableStateOf(false) }
        var showSpeed by remember { mutableStateOf(false) }
        var showSearch by remember(song.key) { mutableStateOf(false) }
        val artist = remember(song.key) { song.artistItem() }
        val album = remember(song.key) { song.albumItem() }
        fun openPage(page: MusicPage) {
            val index = pages.indexOf(page)
            if (index >= 0) scope.launch { pager.animateScrollToPage(index) }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (song.artwork != null) {
                DetailArtworkBackdrop(song.artwork, Modifier.fillMaxWidth().height((maxHeight * 0.68f).coerceAtMost(540.dp)))
            }
            Column(Modifier.fillMaxSize().padding(WindowInsets.safeDrawing.asPaddingValues())) {
                PlayerTopBar(
                    pages = pages,
                    page = pages[pager.currentPage.coerceIn(pages.indices)],
                    label = { stringResource(it.label) },
                    onMinimize = { closeThen() },
                    onPage = ::openPage,
                )
                SettlingPager(pager, Modifier.weight(1f).fillMaxWidth()) { index ->
                    when (pages.getOrNull(index)) {
                        MusicPage.Playing -> PlayerLayout(
                            cover = { PlayerCover(song.artwork, playing = state.wantsToPlay) },
                            controls = {
                                MusicControls(
                                    song = song,
                                    state = state,
                                    queue = queue,
                                    liked = liked.any { it.isSame(song) },
                                    currentLine = lyric.lyric?.lineAt(state.positionMs),
                                    music = music,
                                    onLike = { deps.favorites.toggle(song) },
                                    onAddToFavorites = {
                                        deps.favorites.add(song)
                                        Toast.makeText(context, addedText, Toast.LENGTH_SHORT).show()
                                    },
                                    onOpenArtist = artist?.let { { closeThen { onOpenMusic(MusicDetailKind.Artist, it) } } },
                                    onOpenAlbum = album?.let { { closeThen { onOpenMusic(MusicDetailKind.Album, it) } } },
                                    onOpenLyrics = { openPage(MusicPage.Lyrics) },
                                    onOpenQueue = { showQueue = true },
                                    onOpenQuality = { showQuality = true },
                                    onOpenSleep = { showSleep = true },
                                    sleepActive = sleep.active,
                                    onOpenComments = if (MusicPage.Comments in pages) {
                                        { openPage(MusicPage.Comments) }
                                    } else null,
                                    onOpenSpeed = { showSpeed = true },
                                )
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        MusicPage.Lyrics -> PlayerPageFrame {
                            LyricsPage(
                                holder = lyric,
                                state = state,
                                queue = queue,
                                music = music,
                                onSearch = { showSearch = true },
                                onOpenQueue = { showQueue = true },
                            )
                        }
                        MusicPage.Comments -> PlayerPageFrame { MusicComments(song, deps.lyrics, Modifier.fillMaxSize()) }
                        null -> Unit
                    }
                }
            }
        }
        if (showQueue) {
            MusicQueueSheet(music, onClear = { closeThen { music.clear() } }, onDismiss = { showQueue = false })
        }
        if (showQuality) {
            MusicQualitySheet(
                current = state.quality,
                onSelect = { quality ->
                    scope.launch {
                        if (!music.changeQuality(quality)) Toast.makeText(context, qualityFailed, Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { showQuality = false },
            )
        }
        if (showSpeed) {
            PlayerSpeedSheet(speed, onSpeed = music::setSpeed, onDismiss = { showSpeed = false }, scope = null)
        }
        if (showSleep) {
            PlayerSleepSheet(
                timer = sleep,
                initialMinutes = music.lastSleepMinutes(),
                onMinutes = music::startSleepTimer,
                onEpisodeEnd = music::sleepAtSongEnd,
                onOff = music::cancelSleepTimer,
                onDismiss = { showSleep = false },
                endPreset = R.string.music_sleep_song_end,
                endStatus = R.string.music_sleep_status_song_end,
            )
        }
        if (showSearch) {
            LyricSearchSheet(
                song = song,
                deps = deps,
                onChosen = {
                    showSearch = false
                    lyric.reload()
                },
                onDismiss = { showSearch = false },
            )
        }
    }
}

/**
 * Top to bottom: the song, its artist and the line being sung; 喜欢, 加入歌单 and 更多; the progress with the quality;
 * then the transport with the play mode and the queue at its ends.
 */
@Composable
private fun MusicControls(
    song: MusicItem,
    state: MusicPlaybackState,
    queue: MusicQueueState,
    liked: Boolean,
    currentLine: String?,
    music: MusicPlayback,
    onLike: () -> Unit,
    onAddToFavorites: () -> Unit,
    onOpenArtist: (() -> Unit)?,
    onOpenAlbum: (() -> Unit)?,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenQuality: () -> Unit,
    onOpenSleep: () -> Unit,
    sleepActive: Boolean,
    onOpenComments: (() -> Unit)?,
    onOpenSpeed: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // The song, its actions, the progress and the transport keep even gaps, as in QQ音乐.
    Column(Modifier.fillMaxWidth()) {
        Text(
            song.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (song.artist.isNotBlank()) {
            Row(
                Modifier.heightIn(min = 40.dp).clip(MaterialTheme.shapes.small)
                    .clickable(enabled = onOpenArtist != null, onClickLabel = stringResource(R.string.music_view_artist)) { onOpenArtist?.invoke() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    song.artist, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (onOpenArtist != null) {
                    Icon(XmIcons.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
                }
            }
        }
        // The line being sung keeps its height, so the controls do not jump between lines.
        Text(
            currentLine.orEmpty(),
            Modifier.fillMaxWidth().heightIn(min = 32.dp).clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = stringResource(R.string.player_page_lyrics), onClick = onOpenLyrics)
                .padding(vertical = 6.dp),
            style = MaterialTheme.typography.bodyMedium, color = scheme.primary,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        SongActions(
            liked = liked,
            platform = song.platform,
            onLike = onLike,
            onAddToFavorites = onAddToFavorites,
            onOpenArtist = onOpenArtist,
            onOpenAlbum = onOpenAlbum,
            onOpenQuality = onOpenQuality,
            onOpenSleep = onOpenSleep,
            sleepActive = sleepActive,
            onOpenComments = onOpenComments,
            onOpenSpeed = onOpenSpeed,
            modifier = Modifier.padding(top = ControlsGap),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = ControlsGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlayerSeekBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                enabled = state.durationMs > 0 && state.phase != PlaybackPhase.Failed && state.phase != PlaybackPhase.Preparing,
                trackId = song.key,
                onSeek = music::seekTo,
                modifier = Modifier.weight(1f),
            )
            QualityTag(state.quality ?: MusicQuality.Standard, onOpenQuality)
        }
        val hasQueue = queue.items.isNotEmpty()
        Row(
            Modifier.fillMaxWidth().padding(top = TransportGap),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = music::toggleMode) {
                XmSwap(queue.mode) { mode -> Icon(mode.icon, stringResource(mode.label)) }
            }
            IconButton(music::previous, Modifier.size(48.dp), enabled = hasQueue) {
                Icon(XmIcons.Filled.SkipPrevious, stringResource(R.string.music_previous), Modifier.size(36.dp))
            }
            PlayerPlayButton(state.phase, state.wantsToPlay, loadingQueue = false, onClick = music::toggle)
            IconButton(music::next, Modifier.size(48.dp), enabled = hasQueue) {
                Icon(XmIcons.Filled.SkipNext, stringResource(R.string.music_next), Modifier.size(36.dp))
            }
            IconButton(onClick = onOpenQueue, enabled = hasQueue) {
                Icon(XmIcons.Outlined.QueueMusic, stringResource(R.string.player_queue))
            }
        }
        val error = state.errorCode
        if (error != null) {
            Text(
                stringResource(playbackMessage(error)), Modifier.fillMaxWidth().padding(top = 8.dp),
                color = scheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            )
        }
    }
}

/** 喜欢, 加入歌单 and 更多; the last two open menus under their buttons. */
@Composable
private fun SongActions(
    liked: Boolean,
    platform: String,
    onLike: () -> Unit,
    onAddToFavorites: () -> Unit,
    onOpenArtist: (() -> Unit)?,
    onOpenAlbum: (() -> Unit)?,
    onOpenQuality: () -> Unit,
    onOpenSleep: () -> Unit,
    sleepActive: Boolean,
    onOpenComments: (() -> Unit)?,
    onOpenSpeed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    var adding by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth()) {
        ActionSlot {
            IconButton(
                onClick = onLike,
                colors = IconButtonDefaults.iconButtonColors(contentColor = if (liked) scheme.primary else scheme.onSurfaceVariant),
            ) {
                XmSwap(liked) { on ->
                    Icon(
                        if (on) XmIcons.Filled.Favorite else XmIcons.Outlined.FavoriteBorder,
                        stringResource(if (on) R.string.music_unlike else R.string.music_like),
                    )
                }
            }
        }
        ActionSlot {
            Box {
                IconButton(
                    onClick = { adding = true },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = scheme.onSurfaceVariant),
                ) { Icon(XmIcons.Outlined.PlaylistAdd, stringResource(R.string.music_add_to_sheet)) }
                XmMenu(adding, onDismissRequest = { adding = false }) {
                    XmMenuItem(
                        stringResource(R.string.music_favorites),
                        onClick = {
                            adding = false
                            onAddToFavorites()
                        },
                        index = 0, count = 1, icon = XmIcons.Outlined.FavoriteBorder,
                    )
                }
            }
        }
        ActionSlot {
            IconButton(
                onClick = onOpenSleep,
                colors = IconButtonDefaults.iconButtonColors(contentColor = if (sleepActive) scheme.primary else scheme.onSurfaceVariant),
            ) {
                Icon(if (sleepActive) XmIcons.Filled.Timer else XmIcons.Outlined.Timer, stringResource(R.string.player_sleep))
            }
        }
        if (onOpenComments != null) {
            ActionSlot {
                IconButton(
                    onClick = onOpenComments,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = scheme.onSurfaceVariant),
                ) { Icon(XmIcons.Outlined.ChatBubbleOutline, stringResource(R.string.player_page_comments)) }
            }
        }
        ActionSlot {
            Box {
                IconButton(
                    onClick = { more = true },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = scheme.onSurfaceVariant),
                ) { Icon(XmIcons.Outlined.MoreHoriz, stringResource(R.string.player_more)) }
                XmMenu(more, onDismissRequest = { more = false }) {
                    fun pick(action: () -> Unit): () -> Unit = {
                        more = false
                        action()
                    }
                    val count = 5
                    XmMenuItem(
                        stringResource(R.string.music_view_artist), pick { onOpenArtist?.invoke() }, 0, count,
                        icon = XmIcons.Outlined.Person, enabled = onOpenArtist != null,
                    )
                    XmMenuItem(
                        stringResource(R.string.music_view_album), pick { onOpenAlbum?.invoke() }, 1, count,
                        icon = XmIcons.Outlined.Album, enabled = onOpenAlbum != null,
                    )
                    XmMenuItem(stringResource(R.string.music_quality), pick(onOpenQuality), 2, count, icon = XmIcons.Outlined.Tune)
                    XmMenuItem(stringResource(R.string.player_speed), pick(onOpenSpeed), 3, count, icon = XmIcons.Outlined.Speed)
                    XmMenuItem(stringResource(R.string.music_source_value, platform), pick {}, 4, count, icon = XmIcons.Outlined.Link)
                }
            }
        }
    }
}

@Composable
private fun RowScope.ActionSlot(content: @Composable () -> Unit) {
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { content() }
}

/** The playing quality as a small outlined tag at the end of the progress bar. */
@Composable
private fun QualityTag(quality: MusicQuality, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        stringResource(quality.label),
        Modifier.clip(CircleShape).border(1.dp, scheme.outlineVariant, CircleShape)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.music_quality), onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1,
    )
}

/** 低品质 to 超高品质; the playing one is checked. */
@Composable
private fun MusicQualitySheet(current: MusicQuality?, onSelect: (MusicQuality) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = { WindowInsets.safeDrawing },
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = XmLayout.ItemGap)) {
            Box(Modifier.padding(horizontal = XmLayout.PagePadding)) { SheetHeader(stringResource(R.string.music_quality)) }
            MusicQuality.entries.forEach { quality ->
                val selected = quality == current
                ListItem(
                    headlineContent = { Text(stringResource(quality.label)) },
                    trailingContent = if (selected) {
                        { Icon(XmIcons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(role = Role.RadioButton) {
                        scope.launch {
                            sheet.hide()
                            if (!sheet.isVisible) {
                                onDismiss()
                                if (!selected) onSelect(quality)
                            }
                        }
                    }.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

/** A song's lyric, parsed: the lines, and the translation of each line by its time. */
private class SongLyric(val lines: List<LyricLine>, val translations: Map<Long, String>) {
    fun indexAt(positionMs: Long): Int = lines.indexOfLast { it.timeMs <= positionMs }

    fun lineAt(positionMs: Long): String? = lines.getOrNull(indexAt(positionMs))?.text?.takeIf { it.isNotBlank() }
}

/** Loads the lyric of a song once per song; [reload] asks again after the user chose another one. */
private class LyricHolder {
    var lyric by mutableStateOf<SongLyric?>(null)
    var loaded by mutableStateOf(false)
    var version by mutableIntStateOf(0)

    fun reload() {
        version++
    }
}

@Composable
private fun rememberSongLyric(song: MusicItem, lyrics: MusicLyrics): LyricHolder {
    val holder = remember(song.key) { LyricHolder() }
    LaunchedEffect(song.key, holder.version) {
        val source = try {
            lyrics.lyric(song)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        holder.lyric = source?.parsed()
        holder.loaded = true
    }
    return holder
}

private fun LyricSource.parsed(): SongLyric? {
    val main = parseLrc(rawLrc)
    val translation = parseLrc(translation)
    if (main.isEmpty() && translation.isEmpty()) return null
    if (main.isEmpty()) return SongLyric(translation, emptyMap())
    if (translation.isEmpty()) return main.withInlineTranslation()
    return SongLyric(main, translation.filter { it.text.isNotBlank() }.associate { it.timeMs to it.text })
}

/**
 * Some plugins (酷我's lyrics) put the translation in the lyric itself: each translated line follows its original but
 * carries the next line's time, so that time appears twice. As LX Music reads them, the line before a repeated time is
 * the translation of the line before it. Applied only when most times repeat, so ordinary lyrics stay as they are.
 */
private fun List<LyricLine>.withInlineTranslation(): SongLyric {
    val counts = groupingBy { it.timeMs }.eachCount()
    val shared = counts.count { it.value == 2 }
    if (shared < InlineTranslationMinPairs || shared * 2 < counts.size) return SongLyric(this, emptyMap())
    val seen = HashSet<Long>()
    val lines = mutableListOf<LyricLine>()
    val translations = mutableMapOf<Long, String>()
    for (line in this) {
        if (!seen.add(line.timeMs) && lines.size >= 2) {
            val translation = lines.removeAt(lines.lastIndex)
            if (translation.text.isNotBlank()) translations[lines.last().timeMs] = translation.text
        }
        lines += line
    }
    return SongLyric(lines, translations)
}

/** Fewer pairs than this are lines that happen to share a time, such as 词 and 曲 at the start. */
private const val InlineTranslationMinPairs = 3

/** 歌词: the lyric over a compact transport, or 暂无歌词 with 搜索歌词. */
@Composable
private fun LyricsPage(
    holder: LyricHolder,
    state: MusicPlaybackState,
    queue: MusicQueueState,
    music: MusicPlayback,
    onSearch: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val lyric = holder.lyric
    var translated by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !holder.loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                lyric == null -> Column(
                    Modifier.align(Alignment.Center).padding(horizontal = PlayerPagePadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(XmIcons.Outlined.Lyrics, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.size(12.dp))
                    Text(stringResource(R.string.music_lyric_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(16.dp))
                    FilledTonalButton(onClick = onSearch) { Text(stringResource(R.string.music_lyric_search)) }
                }
                else -> {
                    LyricLines(
                        lyric = lyric,
                        translated = translated,
                        positionMs = state.positionMs,
                        onSeek = music::seekTo,
                        onPlayFrom = { position ->
                            music.seekTo(position)
                            if (!music.state.value.wantsToPlay) music.resume()
                        },
                    )
                    if (lyric.translations.isNotEmpty()) {
                        FilterChip(
                            selected = translated,
                            onClick = { translated = !translated },
                            label = { Text(stringResource(R.string.music_lyric_translation)) },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = PlayerPagePadding),
                        )
                    }
                }
            }
        }
        LyricTransport(state, queue, music, onOpenQueue)
    }
}

/**
 * Left-aligned lines in one size: the current one bright and a little larger, the rest fading with distance, kept in
 * the middle and fading out at the edges. A drag shows a time line with a play button at the middle; the lyric follows
 * the song again a few seconds after the drag ends.
 */
@Composable
private fun LyricLines(
    lyric: SongLyric,
    translated: Boolean,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    onPlayFrom: (Long) -> Unit,
) {
    val animated = rememberAnimationsEnabled()
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val current = lyric.indexAt(positionMs)
    val list = rememberLazyListState()
    val dragged by list.interactionSource.collectIsDraggedAsState()
    var browsing by remember { mutableStateOf(false) }
    LaunchedEffect(dragged) {
        if (dragged) {
            browsing = true
        } else if (browsing) {
            snapshotFlow { list.isScrollInProgress }.first { !it }
            delay(LyricFollowDelayMs)
            browsing = false
        }
    }
    var placed by remember(lyric) { mutableStateOf(false) }
    LaunchedEffect(lyric, current, browsing) {
        if (browsing) return@LaunchedEffect
        list.anchor(current.coerceAtLeast(0), if (animated && placed) spec else null)
        placed = true
    }
    val centred by remember(list) {
        derivedStateOf {
            val info = list.layoutInfo
            val target = info.anchorOffset()
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - target) }?.index ?: 0
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The lyric starts at the top and the line being sung settles a third of the way down.
        LazyColumn(
            Modifier.fillMaxSize().lyricFade(),
            state = list,
            contentPadding = PaddingValues(top = maxHeight * LyricFadeEdge, bottom = maxHeight * (1f - LyricAnchor)),
        ) {
            itemsIndexed(lyric.lines) { index, line ->
                LyricRow(
                    line = line,
                    translation = if (translated) lyric.translations[line.timeMs] else null,
                    distance = abs(index - current),
                    focused = browsing && index == centred,
                    animated = animated,
                    onClick = { onSeek(line.timeMs) },
                )
            }
        }
        if (browsing) {
            lyric.lines.getOrNull(centred)?.let { line ->
                // The line in focus brightens and a small pill with its time sits at its end to play from there.
                Row(
                    Modifier.align(Alignment.TopStart).offset(y = maxHeight * LyricAnchor - 24.dp)
                        .fillMaxWidth().height(48.dp).padding(start = PlayerPagePadding, end = LyricBandInset + 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.weight(1f))
                    Surface(
                        onClick = {
                            browsing = false
                            onPlayFrom(line.timeMs)
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Row(
                            Modifier.heightIn(min = 28.dp).padding(start = 6.dp, end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(XmIcons.Filled.PlayArrow, stringResource(R.string.music_lyric_play_here), Modifier.size(18.dp))
                            Text(
                                formatDuration(line.timeMs / 1000.0), Modifier.padding(start = 2.dp),
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One line and its translation; [distance] is how many lines it is from the one being sung. */
@Composable
private fun LyricRow(
    line: LyricLine,
    translation: String?,
    distance: Int,
    focused: Boolean,
    animated: Boolean,
    onClick: () -> Unit,
) {
    val active = distance == 0
    val motion = MaterialTheme.motionScheme
    val alpha by animateFloatAsState(
        when {
            active -> 1f
            focused -> 0.8f
            distance == 1 -> 0.55f
            else -> 0.38f
        },
        if (animated) motion.defaultEffectsSpec() else snap(),
        label = "lyricAlpha",
    )
    val scale by animateFloatAsState(
        if (active) ActiveLyricScale else 1f,
        if (animated) motion.defaultSpatialSpec() else snap(),
        label = "lyricScale",
    )
    val color = MaterialTheme.colorScheme.onSurface
    // While browsing, the line the pill plays from sits on a light band.
    val band by animateColorAsState(
        color.copy(alpha = if (focused) FocusedLyricBand else 0f),
        if (animated) motion.defaultEffectsSpec() else snap(),
        label = "lyricBand",
    )
    Column(
        Modifier.fillMaxWidth().padding(horizontal = LyricBandInset).clip(MaterialTheme.shapes.medium).background(band)
            .clickable(onClick = onClick)
            .padding(start = PlayerPagePadding - LyricBandInset, end = PlayerPagePadding + 4.dp, top = 10.dp, bottom = 10.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
                this.alpha = alpha
            },
    ) {
        Text(
            line.text,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, lineHeight = 30.sp),
            color = color,
        )
        if (!translation.isNullOrBlank()) {
            Text(
                translation, Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyLarge, color = color.copy(alpha = 0.8f),
            )
        }
    }
}

/** The lyric fades out at its top and bottom instead of being cut off. */
private fun Modifier.lyricFade(): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    LyricFadeEdge to Color.Black,
                    1f - LyricFadeEdge to Color.Black,
                    1f to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
            )
        }

/** Under the lyric: a thin progress bar with both times, then the play mode, the transport and the queue. */
@Composable
private fun LyricTransport(state: MusicPlaybackState, queue: MusicQueueState, music: MusicPlayback, onOpenQueue: () -> Unit) {
    val hasQueue = queue.items.isNotEmpty()
    Column(Modifier.fillMaxWidth().padding(start = PlayerPagePadding, end = PlayerPagePadding, top = ControlsGap, bottom = 24.dp)) {
        PlayerSeekBar(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            enabled = state.durationMs > 0 && state.phase != PlaybackPhase.Failed && state.phase != PlaybackPhase.Preparing,
            trackId = state.song?.key,
            onSeek = music::seekTo,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = TransportGap),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = music::toggleMode) {
                XmSwap(queue.mode) { mode -> Icon(mode.icon, stringResource(mode.label)) }
            }
            IconButton(music::previous, Modifier.size(48.dp), enabled = hasQueue) {
                Icon(XmIcons.Filled.SkipPrevious, stringResource(R.string.music_previous), Modifier.size(32.dp))
            }
            PlayerPlayButton(state.phase, state.wantsToPlay, loadingQueue = false, onClick = music::toggle, size = 56.dp)
            IconButton(music::next, Modifier.size(48.dp), enabled = hasQueue) {
                Icon(XmIcons.Filled.SkipNext, stringResource(R.string.music_next), Modifier.size(32.dp))
            }
            IconButton(onClick = onOpenQueue, enabled = hasQueue) {
                Icon(XmIcons.Outlined.QueueMusic, stringResource(R.string.player_queue))
            }
        }
    }
}

/** The gap between the groups of the player's controls. */
private val ControlsGap = 16.dp

/** The gap above the transport, wider so the tall play button does not crowd the progress bar. */
private val TransportGap = 20.dp

private const val ActiveLyricScale = 1.05f
private const val FocusedLyricBand = 0.08f
private val LyricBandInset = 12.dp
private const val LyricFadeEdge = 0.1f

/** Where the line being sung settles, as a share of the lyric's height. */
private const val LyricAnchor = 0.35f

private fun LazyListLayoutInfo.anchorOffset(): Int =
    viewportStartOffset + ((viewportEndOffset - viewportStartOffset) * LyricAnchor).toInt()

/** Scrolls so the item at [index] sits at the anchor, or as close as the list allows; instantly when [spec] is null. */
private suspend fun LazyListState.anchor(index: Int, spec: AnimationSpec<Float>?) {
    repeat(2) {
        val info = layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
        if (item == null) {
            scrollToItem(index)
            return@repeat
        }
        val delta = (item.offset + item.size / 2 - info.anchorOffset()).toFloat()
        if (spec == null) scrollBy(delta) else animateScrollBy(delta, spec)
        return
    }
}

/** Lyrics found by the plugins that search lyrics; choosing one uses it for the song from now on. */
@Composable
private fun LyricSearchSheet(song: MusicItem, deps: MusicPlayerDeps, onChosen: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val failed = stringResource(R.string.music_lyric_failed)
    val scope = rememberCoroutineScope()
    var results by remember(song.key) { mutableStateOf<List<MusicItem>?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(song.key) {
        val platforms = deps.lyrics.lyricSearchPlatforms()
        results = coroutineScope {
            platforms.map { platform ->
                async {
                    try {
                        deps.catalog.search(platform, song.title, 1, SearchType.Lyric).items.filterIsInstance<MusicItem>()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = { WindowInsets.safeDrawing },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.padding(horizontal = XmLayout.PagePadding)) { SheetHeader(stringResource(R.string.music_lyric_search), song.title) }
            val found = results
            when {
                found == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                found.isEmpty() -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.music_nothing), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                    items(found) { match ->
                        SongRow(match, onClick = {
                            if (busy) return@SongRow
                            busy = true
                            scope.launch {
                                val chosen = try {
                                    deps.lyrics.choose(song, match)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    false
                                }
                                busy = false
                                if (chosen) onChosen() else Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
                            }
                        })
                    }
                }
            }
        }
    }
}

/** 评论 for a song: the plugin's pages of comments, read only. */
@Composable
private fun MusicComments(song: MusicItem, lyrics: MusicLyrics, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0]
    val list = remember(song.key) {
        ListSection(scope, { true }) { cursor ->
            val page = (cursor ?: 1L).toInt()
            val result = lyrics.comments(song, page)
            ListPage(result.items, if (result.isEnd) null else page + 1L)
        }
    }
    val state by list.flow.collectAsStateWithLifecycle()
    val lazy = rememberLazyListState()
    LaunchedEffect(list) { list.ensureLoaded() }
    LoadMoreEffect(
        { lazy.layoutInfo.visibleItemsInfo.lastOrNull()?.index },
        { state.items.size + 1 },
        state.next,
        state.items.size,
        onLoadMore = { list.loadMore() },
    )
    val failure = state.failure
    Box(modifier) {
        when {
            state.items.isEmpty() && failure != null -> InlineError(failure, onRetry = { list.refresh() }, modifier = Modifier.fillMaxSize())
            state.items.isEmpty() && (!state.loaded || state.loading) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.items.isEmpty() -> Column(
                Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(XmIcons.Outlined.ChatBubbleOutline, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.size(12.dp))
                Text(stringResource(R.string.comment_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(Modifier.fillMaxSize(), state = lazy) {
                items(state.items) { comment -> MusicCommentRow(comment, locale) }
                item(key = "footer") {
                    PagingFooter(state.loading, failure != null, state.next != null, onRetry = { list.loadMore() })
                }
            }
        }
    }
}

/** Avatar, name, the comment, then when and where it was written and its likes; replies sit under it in a quiet box. */
@Composable
private fun MusicCommentRow(comment: MusicComment, locale: Locale) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = PlayerPagePadding, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(comment.avatar, Modifier.size(36.dp))
        Column(Modifier.weight(1f)) {
            Text(
                comment.nickName, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                comment.comment, Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp), color = scheme.onSurface,
            )
            if (comment.replies.isNotEmpty()) {
                Column(
                    Modifier.padding(top = 8.dp).fillMaxWidth().clip(MaterialTheme.shapes.medium)
                        .padding(0.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    comment.replies.forEach { reply ->
                        Text(
                            "${reply.nickName}: ${reply.comment}", style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val meta = listOfNotNull(
                    comment.createAt?.let { formatDate(it, locale) },
                    comment.location?.takeIf { it.isNotBlank() },
                    comment.replies.size.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.comment_replies, it, it) },
                ).joinToString(" · ")
                Text(
                    meta, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val likes = comment.like ?: 0
                if (likes > 0) {
                    Icon(XmIcons.Outlined.ThumbUpOutlined, null, Modifier.size(16.dp), tint = scheme.onSurfaceVariant)
                    Text(
                        formatCount(likes), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** False when the system has animations removed. */
@Composable
internal fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

internal val MusicPlayMode.icon: ImageVector
    get() = when (this) {
        MusicPlayMode.Queue -> XmIcons.Outlined.Repeat
        MusicPlayMode.Single -> XmIcons.Outlined.RepeatOne
        MusicPlayMode.Shuffle -> XmIcons.Outlined.Shuffle
    }

@get:StringRes
internal val MusicPlayMode.label: Int
    get() = when (this) {
        MusicPlayMode.Queue -> R.string.music_mode_queue
        MusicPlayMode.Single -> R.string.music_mode_single
        MusicPlayMode.Shuffle -> R.string.music_mode_shuffle
    }

@get:StringRes
private val MusicQuality.label: Int
    get() = when (this) {
        MusicQuality.Low -> R.string.music_quality_low
        MusicQuality.Standard -> R.string.music_quality_standard
        MusicQuality.High -> R.string.music_quality_high
        MusicQuality.Super -> R.string.music_quality_super
    }

/** The song's artist as MusicFree opens it: the `artistId` the song carries, or the name, on the song's plugin. */
private fun MusicItem.artistItem(): MusicArtist? {
    if (artist.isBlank()) return null
    val raw = buildJsonObject {
        put("id", raw.text("artistId") ?: artist)
        put("name", artist)
        put("platform", platform)
    }
    return MusicArtist.from(raw, platform)
}

/** The song's album: the `albumId` the song carries, or the album's title, on the song's plugin. */
private fun MusicItem.albumItem(): MusicAlbum? {
    if (album.isBlank()) return null
    val raw = buildJsonObject {
        put("id", raw.text("albumId") ?: album)
        put("title", album)
        put("artist", artist)
        artwork?.let { put("artwork", it) }
        put("platform", platform)
    }
    return MusicAlbum.from(raw, platform)
}

private fun kotlinx.serialization.json.JsonObject.text(name: String): String? {
    val value = this[name] as? JsonPrimitive ?: return null
    if (value is JsonNull) return null
    return value.contentOrNull?.takeIf { it.isNotBlank() }
}

/** How long the lyric waits after a drag before it follows the song again. */
private const val LyricFollowDelayMs = 3_000L
