package com.xuleo.xmplus.md3.core.media

import com.xuleo.xmplus.md3.core.model.MusicItem
import java.io.File

/** MusicFree's quality keys, lowest first. */
enum class MusicQuality(val key: String) {
    Low("low"), Standard("standard"), High("high"), Super("super");

    companion object {
        fun fromKey(key: String?): MusicQuality? = entries.firstOrNull { it.key == key }
    }
}

/** MusicFree's `getQualityOrder(quality, "asc")`: the quality, then the higher ones, then the lower ones from the nearest. */
fun qualityOrder(preferred: MusicQuality): List<MusicQuality> {
    val all = MusicQuality.entries
    val index = preferred.ordinal
    return listOf(preferred) + all.subList(index + 1, all.size) + all.subList(0, index).reversed()
}

/** MusicFree's repeat modes: 列表循环, 随机, 单曲循环. Stored by [key]. */
enum class MusicPlayMode(val key: String) {
    Queue("queue"), Shuffle("shuffle"), Single("single");

    /** The order of MusicFree's mode button. */
    val next: MusicPlayMode get() = when (this) {
        Queue -> Shuffle
        Shuffle -> Single
        Single -> Queue
    }

    companion object {
        fun fromKey(key: String?): MusicPlayMode = entries.firstOrNull { it.key == key } ?: Queue
    }
}

/** Where a song plays from: a link with the headers it needs (user agent included), or a downloaded file. */
data class ResolvedSong(val url: String, val headers: Map<String, String> = emptyMap(), val localFile: File? = null)

/** Finds a song's source in one quality. Implemented by the app with the plugins, so playback does not know them. */
fun interface SongSourceResolver {
    /** Null when the song has no source in [quality]. */
    suspend fun resolve(song: MusicItem, quality: MusicQuality): ResolvedSong?
}

data class MusicQueueState(
    val items: List<MusicItem> = emptyList(),
    val currentKey: String? = null,
    val mode: MusicPlayMode = MusicPlayMode.Queue,
)

data class MusicPlaybackState(
    val song: MusicItem? = null,
    val phase: PlaybackPhase = PlaybackPhase.Idle,
    val wantsToPlay: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** The quality the song plays in, once a source was found. */
    val quality: MusicQuality? = null,
    val errorCode: String? = null,
)

/**
 * MusicFree's play list. Each song carries the moment it was added, so that leaving 随机 brings back the order songs
 * were added in, as MusicFree sorts by its timestamp. Owned by [MusicPlayback] on the main thread.
 */
internal class MusicQueue {
    var items: List<MusicItem> = emptyList()
        private set
    var currentKey: String? = null
    private val stamps = HashMap<String, Long>()
    private var clock = 0L

    val currentIndex: Int get() = currentKey?.let { key -> items.indexOfFirst { it.key == key } } ?: -1
    val current: MusicItem? get() = items.getOrNull(currentIndex)

    /** The song at [index], wrapping around both ends as MusicFree does. */
    fun at(index: Int): MusicItem? {
        val size = items.size
        return if (size == 0) null else items[((index % size) + size) % size]
    }

    fun replace(songs: List<MusicItem>, shuffle: Boolean) {
        val unique = songs.distinctBy { it.key }
        stamps.clear()
        unique.forEach { stamps[it.key] = clock++ }
        items = if (shuffle) unique.shuffled() else unique
    }

    /** Adds the songs not already queued at the end. Returns how many were added. */
    fun append(songs: List<MusicItem>): Int {
        val known = items.mapTo(HashSet()) { it.key }
        val fresh = songs.filter { known.add(it.key) }
        fresh.forEach { stamps[it.key] = clock++ }
        items = items + fresh
        return fresh.size
    }

    /** Puts [song] right after the current one, moving it when it is already queued. */
    fun insertAfterCurrent(song: MusicItem) {
        if (song.key == currentKey) return
        val rest = items.filterNot { it.key == song.key }
        val at = rest.indexOfFirst { it.key == currentKey } + 1
        stamps[song.key] = clock++
        items = rest.take(at) + song + rest.drop(at)
    }

    fun remove(key: String) {
        items = items.filterNot { it.key == key }
        stamps.remove(key)
    }

    /** A move by hand outside 随机 becomes the order that 随机 later returns to. */
    fun move(from: Int, to: Int, keepOrder: Boolean) {
        if (from !in items.indices || to !in items.indices || from == to) return
        items = items.toMutableList().apply { add(to, removeAt(from)) }
        if (!keepOrder) items.forEach { stamps[it.key] = clock++ }
    }

    fun shuffle() {
        items = items.shuffled()
    }

    fun unshuffle() {
        items = items.sortedBy { stamps[it.key] ?: Long.MAX_VALUE }
    }

    fun clear() {
        items = emptyList()
        currentKey = null
        stamps.clear()
    }

    fun stamps(): List<Long> = items.map { stamps[it.key] ?: 0L }

    fun restore(songs: List<MusicItem>, order: List<Long>, current: String?) {
        items = songs
        stamps.clear()
        songs.forEachIndexed { index, song -> stamps[song.key] = order.getOrElse(index) { index.toLong() } }
        clock = (order.maxOrNull() ?: -1L) + 1
        currentKey = current?.takeIf { key -> songs.any { it.key == key } } ?: songs.firstOrNull()?.key
    }
}
