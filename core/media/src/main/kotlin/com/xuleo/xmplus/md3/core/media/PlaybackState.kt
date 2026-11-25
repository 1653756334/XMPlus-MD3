package com.xuleo.xmplus.md3.core.media

import com.xuleo.xmplus.md3.core.model.Playable

const val PlaybackSkipSeconds = 15
const val PlaybackMinSpeed = 0.5f
const val PlaybackMaxSpeed = 3.0f
internal const val PlaybackSkipMillis = PlaybackSkipSeconds * 1_000L

/** The loaded 听书 episode. Songs join as [Playable.Song] when the player learns to play them (M3). */
typealias PlaybackSelection = Playable.Episode

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
