package com.xuleo.xmplus.md3.core.media

import android.content.Context

/** The speed and volume of one album. */
data class AlbumAudio(val speed: Float = 1f, val volume: Float = 1f)

/**
 * The speed and volume each album plays at, kept on this device. Each album has its own: one that was never adjusted
 * plays at 1× and full volume, whatever another album is set to.
 */
class AlbumAudioStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("album-audio", Context.MODE_PRIVATE)

    init {
        // Earlier builds let a new album borrow the speed used last anywhere.
        if (preferences.contains(LegacyLastSpeed)) preferences.edit().remove(LegacyLastSpeed).apply()
    }

    fun get(albumId: String): AlbumAudio {
        val parts = preferences.getString(albumId, null)?.split(',')
        val speed = parts?.getOrNull(0)?.toFloatOrNull()?.takeIf { it in PlaybackMinSpeed..PlaybackMaxSpeed }
        val volume = parts?.getOrNull(1)?.toFloatOrNull()?.takeIf { it in 0f..1f }
        return AlbumAudio(speed ?: 1f, volume ?: 1f)
    }

    fun setSpeed(albumId: String, speed: Float) = write(albumId, get(albumId).copy(speed = speed))

    fun setVolume(albumId: String, volume: Float) = write(albumId, get(albumId).copy(volume = volume))

    private fun write(albumId: String, audio: AlbumAudio) {
        preferences.edit().putString(albumId, "${audio.speed},${audio.volume}").apply()
    }

    private companion object {
        const val LegacyLastSpeed = "_last-speed"
    }
}
