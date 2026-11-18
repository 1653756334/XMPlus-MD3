package com.xuleo.xmplus.md3.core.media

import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.Track

const val PlaybackSkipSeconds = 15
internal const val PlaybackSkipMillis = PlaybackSkipSeconds * 1_000L

data class PlaybackSelection(val track: Track, val album: Album) {
    val title: String get() = track.title.ifBlank { album.title }
    val coverUrl: String? get() = track.coverUrl ?: album.coverUrl
    val subtitle: String get() = album.title.ifBlank { track.albumTitle }
}

enum class PlaybackPhase { Idle, Preparing, Buffering, Playing, Paused, Ended, Failed }

data class PlaybackState(
    val selection: PlaybackSelection? = null,
    val phase: PlaybackPhase = PlaybackPhase.Idle,
    val wantsToPlay: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val canSeek: Boolean = false,
    val errorCode: String? = null,
)

/** A pause scheduled after [remainingMs], or when the current episode ends. */
data class SleepTimerState(val remainingMs: Long? = null, val atEpisodeEnd: Boolean = false) {
    val active: Boolean get() = remainingMs != null || atEpisodeEnd
}
