package com.xuleo.xmplus.md3.core.media

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.MainThread
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.xuleo.xmplus.md3.core.model.MusicItem
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient

/**
 * Plays songs on the player that 听书 also uses, by MusicFree's `TrackPlayer` rules: qualities are tried in
 * [qualityOrder], a song that cannot play is skipped after 500 ms, and the queue wraps at both ends. Songs never go
 * through the 听时 ledger, accounts or Ximalaya's link checks. All calls belong to the main thread.
 */
@MainThread
@androidx.annotation.OptIn(UnstableApi::class)
class MusicPlayback(context: Context, private val resolver: SongSourceResolver, private val mediaCache: MusicMediaCache) {
    /** Called when a song is about to start, so that 听书 stops. */
    var onStart: () -> Unit = {}

    private val context = context.applicationContext
    private val sessions = MusicSessionStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engine = MutableStateFlow<ExoPlayer?>(null)
    private val list = MusicQueue()
    private val mutableQueue = MutableStateFlow(MusicQueueState(mode = sessions.mode))
    val queue = mutableQueue.asStateFlow()
    private val mutableState = MutableStateFlow(MusicPlaybackState())
    val state = mutableState.asStateFlow()
    private val mutableSpeed = MutableStateFlow(1f)
    /** The speed songs play at until the app closes. */
    val speed = mutableSpeed.asStateFlow()
    private val sleepStore = SleepTimerStore(context)
    private val mutableSleep = MutableStateFlow(SleepTimerState())
    /** [SleepTimerState.atEpisodeEnd] means at the end of the current song. */
    val sleepTimer = mutableSleep.asStateFlow()
    private var sleepJob: Job? = null
    /** Downloads the playing song whole into the cache, so seeking within it needs no new request. */
    private var preloading: Job? = null
    /** Caches the next song once the playing one is nearly over, so it starts without waiting. */
    private var prefetching: Job? = null
    /** The opening ([revision]) whose next song was already fetched, so each song asks once. */
    private var prefetchedFor = -1L

    /** Plain client: plugin links go anywhere and redirect freely, with no Ximalaya headers or checks. */
    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()
    }

    private var revision = 0L
    private var media = 0L
    private var loading: Job? = null
    private var opened: String? = null
    private var clock: Job? = null
    private var sessionVersion = 0L
    /** Songs that failed one after another; the auto skip stops once every song in the queue failed. */
    private var failures = 0
    private var loadMore: (suspend () -> List<MusicItem>?)? = null
    private var paging: Job? = null
    private var pagesEnded = false

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (!isCurrent(player)) return
            val request = revision
            val error = player.playerError
            when {
                error != null -> scope.launch(Dispatchers.Main) {
                    if (request == revision && isCurrent(player) && player.playerError === error) fail(request, SongUnavailable)
                }
                player.playbackState == Player.STATE_ENDED -> scope.launch(Dispatchers.Main) {
                    if (request == revision && isCurrent(player) && player.playbackState == Player.STATE_ENDED) engine.value?.let(::finishSong)
                }
                else -> publish(player)
            }
        }
    }

    init { restore() }

    internal fun attach(player: ExoPlayer) {
        check(engine.value == null)
        player.addListener(listener)
        engine.value = player
        clock = scope.launch {
            var ticks = 0
            while (true) {
                delay(250)
                if (isCurrent(player)) {
                    publish(player)
                    if (player.isPlaying) prefetchNext(player)
                    if (player.isPlaying && ++ticks % 20 == 0) remember()
                }
            }
        }
    }

    internal fun detach(player: ExoPlayer) {
        if (engine.value !== player) return
        val loaded = isCurrent(player)
        val position = if (loaded) player.currentPosition.coerceAtLeast(0) else mutableState.value.positionMs
        revision++
        loading?.cancel()
        player.removeListener(listener)
        engine.value = null
        opened = null
        clock?.cancel()
        val current = mutableState.value
        if (current.song != null && current.phase != PlaybackPhase.Failed) {
            mutableState.value = current.copy(phase = PlaybackPhase.Paused, wantsToPlay = false, positionMs = position)
        }
        remember()
    }

    /** Replaces the queue with [songs] and plays the one at [index]. [loadMore] reads the list's next page. */
    fun playList(songs: List<MusicItem>, index: Int, loadMore: (suspend () -> List<MusicItem>?)? = null) {
        val target = songs.getOrNull(index) ?: return
        failures = 0
        paging?.cancel()
        this.loadMore = loadMore
        pagesEnded = false
        list.replace(songs, shuffle = mutableQueue.value.mode == MusicPlayMode.Shuffle)
        open(target)
    }

    /** Plays [song] now, right after the current song; the rest of the queue is kept. */
    fun playNow(song: MusicItem) {
        failures = 0
        list.insertAfterCurrent(song)
        open(song)
    }

    /** Queues [song] after the current one, and plays it when nothing is queued. */
    fun addNext(song: MusicItem) {
        val start = list.current == null
        list.insertAfterCurrent(song)
        if (start) {
            failures = 0
            open(song)
        } else {
            publishQueue()
            remember()
        }
    }

    fun playAt(key: String) {
        val song = list.items.firstOrNull { it.key == key } ?: return
        if (key == list.currentKey && engine.value?.let(::isCurrent) == true) {
            resume()
            return
        }
        failures = 0
        open(song)
    }

    fun next() = skip(1)

    fun previous() = skip(-1)

    /** Removing the current song plays the one that took its place, or only shows it when paused. */
    fun remove(key: String) {
        val index = list.items.indexOfFirst { it.key == key }
        if (index == -1) return
        val wasCurrent = key == list.currentKey
        list.remove(key)
        when {
            !wasCurrent -> {
                publishQueue()
                remember()
            }
            list.items.isEmpty() -> clear()
            mutableState.value.wantsToPlay -> open(list.items[index % list.items.size])
            else -> show(list.items[index % list.items.size])
        }
    }

    fun move(from: Int, to: Int) {
        list.move(from, to, keepOrder = mutableQueue.value.mode == MusicPlayMode.Shuffle)
        publishQueue()
        remember()
    }

    fun setSpeed(value: Float) {
        if (!value.isFinite() || value < PlaybackMinSpeed || value > PlaybackMaxSpeed) return
        mutableSpeed.value = value
        engine.value?.takeIf(::isCurrent)?.setPlaybackSpeed(value)
    }

    /** Pause after [minutes] of wall-clock time. */
    fun startSleepTimer(minutes: Int) {
        if (mutableState.value.song == null) return
        sleepStore.setMinutes(minutes)
        cancelSleepTimer()
        val deadline = SystemClock.elapsedRealtime() + minutes * 60_000L
        sleepJob = scope.launch {
            while (true) {
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0) break
                mutableSleep.value = SleepTimerState(remainingMs = (remaining + 999) / 1000 * 1000)
                delay(minOf(remaining, 1_000L))
            }
            sleepJob = null
            mutableSleep.value = SleepTimerState()
            pause()
        }
    }

    /** The length of the last sleep timer started on this device, shared with 听书. */
    fun lastSleepMinutes(): Int = sleepStore.minutes()

    /** Pause when the current song ends, in place of moving to the next one. */
    fun sleepAtSongEnd() {
        if (mutableState.value.song == null) return
        cancelSleepTimer()
        mutableSleep.value = SleepTimerState(atEpisodeEnd = true)
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        if (mutableSleep.value.active) mutableSleep.value = SleepTimerState()
    }

    /** Empties the queue and stops. */
    fun clear() {
        cancelSleepTimer()
        revision++
        loading?.cancel()
        paging?.cancel()
        loadMore = null
        unload()
        list.clear()
        mutableState.value = MusicPlaybackState()
        publishQueue()
        remember()
    }

    fun toggleMode() = setMode(mutableQueue.value.mode.next)

    /** Entering 随机 shuffles the queue; leaving it restores the order songs were added in. */
    fun setMode(mode: MusicPlayMode) {
        val old = mutableQueue.value.mode
        if (old == mode) return
        if (mode == MusicPlayMode.Shuffle) list.shuffle() else if (old == MusicPlayMode.Shuffle) list.unshuffle()
        sessions.mode = mode
        mutableQueue.value = mutableQueue.value.copy(mode = mode)
        publishQueue()
        remember()
    }

    fun toggle() {
        if (mutableState.value.wantsToPlay) pause() else resume()
    }

    fun pause() {
        val current = mutableState.value
        if (current.song == null) return
        val player = engine.value
        if (player != null && isCurrent(player)) {
            player.pause()
            publish(player)
        } else if (current.wantsToPlay) {
            // A song still looking for its link stops looking; play asks again.
            revision++
            loading?.cancel()
            mutableState.value = current.copy(phase = PlaybackPhase.Paused, wantsToPlay = false)
        }
        remember()
    }

    fun resume() {
        val current = mutableState.value
        val song = current.song ?: return
        if (current.wantsToPlay) return
        val player = engine.value
        if (player != null && isCurrent(player) && player.playbackState != Player.STATE_IDLE && current.phase != PlaybackPhase.Failed) {
            onStart()
            player.setAudioAttributes(MusicAttributes, true)
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
            publish(player)
            return
        }
        failures = 0
        open(song, if (current.phase == PlaybackPhase.Ended) 0L else current.positionMs)
    }

    fun seekTo(positionMs: Long) {
        val current = mutableState.value
        if (current.song == null) return
        val target = positionMs.coerceAtLeast(0).let { if (current.durationMs > 0) it.coerceAtMost(current.durationMs) else it }
        val player = engine.value
        if (player != null && isCurrent(player)) {
            player.seekTo(target)
            publish(player)
        } else {
            mutableState.value = current.copy(positionMs = target)
        }
        remember()
    }

    /**
     * Reloads the current song in [quality] at the same position. False, with the old source kept, when the song has
     * no source in that quality.
     */
    suspend fun changeQuality(quality: MusicQuality): Boolean = withContext(Dispatchers.Main.immediate) {
        val current = mutableState.value
        val song = current.song ?: return@withContext false
        if (quality == current.quality) return@withContext true
        val player = engine.value?.takeIf(::isCurrent) ?: return@withContext false
        val request = revision
        val source = resolve(song, quality) ?: return@withContext false
        if (request != revision || !isCurrent(player)) return@withContext false
        load(player, song, quality, source, player.currentPosition.coerceAtLeast(0), play = player.playWhenReady)
        remember()
        true
    }

    /** 听书 took the player: the music queue stops and is not brought back at the next launch. */
    fun stopForEpisode() {
        sessionVersion++
        if (mutableState.value.song == null && list.items.isEmpty()) {
            scope.launch { sessions.save(sessionVersion, null) }
            return
        }
        clear()
    }

    private fun skip(step: Int, automatic: Boolean = false) {
        if (list.items.isEmpty()) return
        if (!automatic) failures = 0
        val index = list.currentIndex
        val target = list.at(if (index == -1 && step < 0) 0 else index + step) ?: return
        open(target)
    }

    private fun open(song: MusicItem, positionMs: Long = 0) {
        val request = ++revision
        loading?.cancel()
        onStart()
        unload()
        list.currentKey = song.key
        val quality = mutableState.value.quality
        mutableState.value = MusicPlaybackState(
            song, PlaybackPhase.Preparing, wantsToPlay = true, positionMs = positionMs,
            durationMs = (song.durationSeconds * 1_000).toLong().coerceAtLeast(0), quality = quality,
        )
        publishQueue()
        remember()
        readNextPage()
        loading = scope.launch {
            val player = withTimeoutOrNull(5_000) { engine.filterNotNull().first() }
            if (request != revision) return@launch
            if (player == null) return@launch fail(request, PlayerUnavailable)
            var found: Pair<MusicQuality, ResolvedSong>? = null
            for (candidate in qualityOrder(DefaultQuality)) {
                val source = resolve(song, candidate)
                // Another song was chosen meanwhile: this one's link is dropped.
                if (request != revision) return@launch
                if (source != null) {
                    found = candidate to source
                    break
                }
            }
            // MusicFree's last resort: the link the song itself carries.
            if (found == null) found = song.ownUrl()?.let { DefaultQuality to ResolvedSong(it) }
            if (found == null) return@launch fail(request, SongUnavailable)
            load(player, song, found.first, found.second, positionMs, play = true)
            remember()
        }
    }

    private suspend fun resolve(song: MusicItem, quality: MusicQuality): ResolvedSong? = try {
        resolver.resolve(song, quality)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private fun load(player: ExoPlayer, song: MusicItem, quality: MusicQuality, source: ResolvedSong, positionMs: Long, play: Boolean) {
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title).setArtist(song.artist).setAlbumTitle(song.album)
            .setArtworkUri(song.artwork?.let(Uri::parse))
            .build()
        val uri = source.uri()
        val mediaId = "song:${++media}"
        val hls = uri.isHls()
        val cacheKey = cacheKey(song, quality, source)
        val item = MediaItem.Builder().setMediaId(mediaId).setUri(uri).setMediaMetadata(metadata)
            .apply { if (hls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .apply { cacheKey?.let(::setCustomCacheKey) }
            .build()
        val upstream = upstream(source)
        val cached = cacheKey?.let { cached(upstream) }
        val factory = DefaultMediaSourceFactory(cached ?: upstream)
        preloading?.cancel()
        preloading = if (cached != null && cacheKey != null) scope.writeToCache(cached, uri, cacheKey) else null
        opened = mediaId
        // Music asks other apps' sounds to duck rather than pause it; 听书 sets speech again when it opens.
        player.setAudioAttributes(MusicAttributes, true)
        player.setPlaybackSpeed(mutableSpeed.value)
        player.setMediaSource(factory.createMediaSource(item), positionMs)
        player.prepare()
        player.playWhenReady = play
        mutableState.value = mutableState.value.copy(
            phase = if (play) PlaybackPhase.Buffering else PlaybackPhase.Paused, wantsToPlay = play,
            positionMs = positionMs, quality = quality, errorCode = null,
        )
        publish(player)
    }

    private fun finishSong(player: ExoPlayer) {
        failures = 0
        if (mutableSleep.value.atEpisodeEnd) {
            cancelSleepTimer()
            player.pause()
            player.seekTo(0)
            publish(player)
            remember()
            return
        }
        if (mutableQueue.value.mode == MusicPlayMode.Single) {
            player.seekTo(0)
            player.play()
            publish(player)
        } else {
            skip(1, automatic = true)
        }
    }

    /** MusicFree's `handlePlayFail`: show the error, then try the next song after 500 ms. */
    private fun fail(request: Long, code: String) {
        if (request != revision) return
        unload()
        val current = mutableState.value
        mutableState.value = current.copy(phase = PlaybackPhase.Failed, wantsToPlay = false, errorCode = code)
        remember()
        if (++failures >= list.items.size) return
        loading = scope.launch {
            delay(500)
            if (request == revision) skip(1, automatic = true)
        }
    }

    /** Shows [song] as current, paused and not loaded. */
    private fun show(song: MusicItem) {
        revision++
        loading?.cancel()
        unload()
        list.currentKey = song.key
        mutableState.value = MusicPlaybackState(
            song, PlaybackPhase.Paused, durationMs = (song.durationSeconds * 1_000).toLong().coerceAtLeast(0),
            quality = mutableState.value.quality,
        )
        publishQueue()
        remember()
    }

    /** Near the end of a list that has more pages, its next page joins the queue. */
    private fun readNextPage() {
        val read = loadMore ?: return
        if (pagesEnded || paging?.isActive == true || mutableQueue.value.mode == MusicPlayMode.Single) return
        if (list.currentIndex < list.items.size - PagingDistance) return
        paging = scope.launch {
            val songs = try {
                read()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } ?: return@launch
            if (loadMore !== read) return@launch
            val fresh = if (mutableQueue.value.mode == MusicPlayMode.Shuffle) songs.shuffled() else songs
            if (list.append(fresh) == 0) pagesEnded = true
            publishQueue()
            remember()
        }
    }

    private fun upstream(source: ResolvedSong): DefaultDataSource.Factory {
        val agent = source.headers.entries.firstOrNull { it.key.equals(UserAgent, true) }?.value
        val http = OkHttpDataSource.Factory(client).setUserAgent(agent)
            .setDefaultRequestProperties(source.headers.filterKeys { !it.equals(UserAgent, true) })
        return DefaultDataSource.Factory(context, http)
    }

    private fun cached(upstream: DefaultDataSource.Factory): CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(mediaCache.cache)
        .setUpstreamDataSourceFactory(upstream)
        .setCacheWriteDataSinkFactory(CacheDataSink.Factory().setCache(mediaCache.cache).setFragmentSize(CacheFragmentBytes))
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /**
     * Writes the whole song into the cache; parts already there are skipped, so a song cached before costs nothing. The
     * player reads what is there and fetches the rest.
     */
    private fun CoroutineScope.writeToCache(factory: CacheDataSource.Factory, uri: Uri, key: String): Job {
        val writer = CacheWriter(
            factory.createDataSourceForDownloading(), DataSpec.Builder().setUri(uri).setKey(key).build(), null, null,
        )
        return launch(Dispatchers.IO) {
            try {
                runInterruptible { writer.cache() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The player still streams what is missing.
            }
        }.also { job -> job.invokeOnCompletion { writer.cancel() } }
    }

    /**
     * At 90 % of a song, the next one in the queue is cached. Skipped in 单曲循环, when the timer stops at this song's
     * end, and when the next song is already cached whole in any quality.
     */
    private fun prefetchNext(player: Player) {
        if (prefetchedFor == revision) return
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        if (player.currentPosition < duration * PrefetchAt) return
        prefetchedFor = revision
        if (mutableQueue.value.mode == MusicPlayMode.Single || mutableSleep.value.atEpisodeEnd) return
        val current = list.current ?: return
        val next = list.at(list.currentIndex + 1)?.takeIf { it.key != current.key } ?: return
        val request = revision
        prefetching?.cancel()
        prefetching = scope.launch {
            val qualities = qualityOrder(DefaultQuality)
            if (withContext(Dispatchers.IO) { qualities.any { mediaCache.isCached(cacheKey(next, it)) } }) return@launch
            for (candidate in qualities) {
                val source = resolve(next, candidate)
                if (request != revision) return@launch
                if (source == null) continue
                val key = cacheKey(next, candidate, source) ?: return@launch
                writeToCache(cached(upstream(source)), source.uri(), key)
                return@launch
            }
        }
    }

    private fun unload() {
        preloading?.cancel()
        preloading = null
        // A next song half fetched goes on from what is cached when it opens.
        prefetching?.cancel()
        prefetching = null
        val player = engine.value
        val loaded = player != null && isCurrent(player)
        opened = null
        if (loaded) {
            player.stop()
            player.clearMediaItems()
        }
    }

    private fun publish(player: Player) {
        val current = mutableState.value
        if (current.song == null) return
        val phase = when (player.playbackState) {
            Player.STATE_BUFFERING -> if (player.playWhenReady) PlaybackPhase.Buffering else PlaybackPhase.Paused
            Player.STATE_READY -> if (player.playWhenReady) PlaybackPhase.Playing else PlaybackPhase.Paused
            Player.STATE_ENDED -> PlaybackPhase.Ended
            else -> current.phase
        }
        if (player.isPlaying) failures = 0
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: current.durationMs
        mutableState.value = current.copy(
            phase = phase, wantsToPlay = player.playWhenReady && phase != PlaybackPhase.Ended,
            positionMs = player.currentPosition.coerceAtLeast(0), durationMs = duration,
        )
    }

    private fun publishQueue() {
        mutableQueue.value = mutableQueue.value.copy(items = list.items, currentKey = list.currentKey)
    }

    /** Saves the queue, the song and where it is; called on changes, on pause and every 5 s while playing. */
    private fun remember() {
        val version = ++sessionVersion
        val player = engine.value
        val position = if (player != null && isCurrent(player)) player.currentPosition.coerceAtLeast(0) else mutableState.value.positionMs
        val session = if (list.items.isEmpty()) null
        else MusicSession(list.items, list.stamps(), list.currentKey, position, mutableState.value.quality)
        scope.launch { sessions.save(version, session) }
    }

    /** Brings back, paused, the queue of the last launch. Links are asked again on play. */
    private fun restore() {
        val version = sessionVersion
        val request = revision
        scope.launch {
            val saved = sessions.read() ?: return@launch
            if (version != sessionVersion || request != revision || list.items.isNotEmpty()) return@launch
            list.restore(saved.songs, saved.order, saved.currentKey)
            publishQueue()
            val song = list.current ?: return@launch
            mutableState.value = MusicPlaybackState(
                song, PlaybackPhase.Paused, positionMs = saved.positionMs,
                durationMs = (song.durationSeconds * 1_000).toLong().coerceAtLeast(0), quality = saved.quality,
            )
        }
    }

    private fun isCurrent(player: Player) = player === engine.value && opened != null && player.currentMediaItem?.mediaId == opened

    private companion object {
        val DefaultQuality = MusicQuality.Standard
        const val SongUnavailable = "song_unavailable"
        const val PlayerUnavailable = "playback_unavailable"
        const val UserAgent = "user-agent"
        /** How close to the end of the queue the next page is read. */
        const val PagingDistance = 3
        /** Cache files are split at this size so the oldest parts of a long song can go first. */
        const val CacheFragmentBytes = 4L * 1024 * 1024
        /** How far into a song the next one is cached. */
        const val PrefetchAt = 0.9
    }
}

private fun ResolvedSong.uri(): Uri = localFile?.let(Uri::fromFile) ?: Uri.parse(url)

/** MusicFree marks `.m3u8` links as HLS. */
private fun Uri.isHls() = path.orEmpty().endsWith(".m3u8", ignoreCase = true)

/** Cached by song and quality rather than link, since plugin links change while the sound does not. */
private fun cacheKey(song: MusicItem, quality: MusicQuality) = "${song.key}#${quality.key}"

/** Null for local files and HLS, which are not cached. */
private fun cacheKey(song: MusicItem, quality: MusicQuality, source: ResolvedSong): String? =
    if (source.localFile != null || source.uri().isHls()) null else cacheKey(song, quality)

private fun MusicItem.ownUrl(): String? =
    (raw["url"] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull?.takeIf { it.isNotEmpty() }
