package com.xuleo.xmplus.md3.core.media

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The quality songs play and download in, MusicFree's `basic.defaultPlayQuality` and `basic.defaultDownloadQuality`.
 * Both are the first quality tried; when the plugin has no link in it, [qualityOrder] decides the next ones (higher
 * first, then lower), exactly as playback falls back.
 */
class MusicQualitySettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("music-quality", Context.MODE_PRIVATE)
    private val mutablePlay = MutableStateFlow(preferences.quality(PlayKey))
    private val mutableDownload = MutableStateFlow(preferences.quality(DownloadKey))

    /** The quality playback asks for first. */
    val play: StateFlow<MusicQuality> = mutablePlay.asStateFlow()

    /** The quality a download asks for first. */
    val download: StateFlow<MusicQuality> = mutableDownload.asStateFlow()

    fun setPlay(quality: MusicQuality) {
        mutablePlay.value = quality
        preferences.edit().putString(PlayKey, quality.key).apply()
    }

    fun setDownload(quality: MusicQuality) {
        mutableDownload.value = quality
        preferences.edit().putString(DownloadKey, quality.key).apply()
    }

    /** The qualities to try, best first: the chosen one, then higher, then lower from the nearest. */
    fun order(preferred: MusicQuality): List<MusicQuality> = qualityOrder(preferred)

    private fun SharedPreferences.quality(key: String) = MusicQuality.fromKey(getString(key, null)) ?: MusicQuality.Standard

    private companion object {
        const val PlayKey = "play"
        const val DownloadKey = "download"
    }
}
