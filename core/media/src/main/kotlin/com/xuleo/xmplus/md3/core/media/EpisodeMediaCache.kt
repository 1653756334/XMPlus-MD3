package com.xuleo.xmplus.md3.core.media

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Episodes heard lately, kept on disk so hearing them again and seeking in them do not ask the network. The oldest go
 * first once the cache passes its limit. 听书 only: songs have a cache of their own, since they come from plugins.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class EpisodeMediaCache(context: Context) {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences("episode-cache", Context.MODE_PRIVATE)
    private val evictor = LimitEvictor(preferences.getLong(LimitKey, DefaultLimit))

    internal val cache: SimpleCache by lazy {
        SimpleCache(File(this.context.cacheDir, "episode-media"), evictor, StandaloneDatabaseProvider(this.context))
    }

    /** How large the cache may grow, in bytes. */
    val limit: Long get() = evictor.limit

    suspend fun setLimit(bytes: Long) {
        evictor.limit = bytes
        preferences.edit().putLong(LimitKey, bytes).apply()
        withContext(Dispatchers.IO) {
            val cache = cache
            synchronized(cache) { evictor.trim(cache) }
        }
    }

    suspend fun usedBytes(): Long = withContext(Dispatchers.IO) { cache.cacheSpace }

    suspend fun clear() = withContext(Dispatchers.IO) {
        val cache = cache
        cache.keys.toList().forEach(cache::removeResource)
    }

    companion object {
        /** The limits offered, the same as the songs' cache. */
        val Limits = listOf(256_000_000L, 512_000_000L, 1_000_000_000L, 2_000_000_000L, 4_000_000_000L)
        const val DefaultLimit = 512_000_000L
        private const val LimitKey = "limit"
    }
}
