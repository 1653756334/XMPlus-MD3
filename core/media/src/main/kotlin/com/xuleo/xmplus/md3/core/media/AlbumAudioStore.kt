package com.xuleo.xmplus.md3.core.media

import android.content.Context

/** The speed and volume of one album. */
data class AlbumAudio(val speed: Float, val volume: Float)

/**
 * The speed and volume each album plays at, kept on this device. An album that was never adjusted starts at the speed
 * used last, so a listener who always hears novels at 1.5× does not set it again for every new album, and at full volume.
 */
class AlbumAudioStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("album-audio", Context.MODE_PRIVATE)

    fun get(albumId: String): AlbumAudio {
        val parts = preferences.getString(albumId, null)?.split(',')
        val speed = parts?.getOrNull(0)?.toFloatOrNull()?.takeIf { it in PlaybackMinSpeed..PlaybackMaxSpeed }
        val volume = parts?.getOrNull(1)?.toFloatOrNull()?.takeIf { it in 0f..1f }
        return AlbumAudio(speed ?: preferences.getFloat(LastSpeed, 1f).takeIf { it in PlaybackMinSpeed..PlaybackMaxSpeed } ?: 1f, volume ?: 1f)
    }

    fun setSpeed(albumId: String, speed: Float) {
        val current = get(albumId)
        write(albumId, current.copy(speed = speed), lastSpeed = speed)
    }

    fun setVolume(albumId: String, volume: Float) = write(albumId, get(albumId).copy(volume = volume), lastSpeed = null)

    private fun write(albumId: String, audio: AlbumAudio, lastSpeed: Float?) {
        val editor = preferences.edit().putString(albumId, "${audio.speed},${audio.volume}")
        if (lastSpeed != null) editor.putFloat(LastSpeed, lastSpeed)
        editor.apply()
    }

    private companion object {
        const val LastSpeed = "_last-speed"
    }
}