package com.xuleo.xmplus.md3.core.media

import android.content.Context
import com.xuleo.xmplus.md3.core.model.AlbumSkip

/** The intro and outro each album skips, kept on this device. An album without an entry skips nothing. */
class AlbumSkipStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("album-skip", Context.MODE_PRIVATE)

    fun get(albumId: String): AlbumSkip {
        val parts = preferences.getString(albumId, null)?.split(',') ?: return AlbumSkip()
        val intro = parts.getOrNull(0)?.toIntOrNull()
        val outro = parts.getOrNull(1)?.toIntOrNull()
        return if (parts.size == 2 && intro != null && outro != null) AlbumSkip(intro, outro).coerced() else AlbumSkip()
    }

    fun set(albumId: String, skip: AlbumSkip) {
        val editor = preferences.edit()
        if (skip.isOff) editor.remove(albumId) else editor.putString(albumId, "${skip.introSeconds},${skip.outroSeconds}")
        editor.apply()
    }
}