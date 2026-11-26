package com.xuleo.xmplus.md3.core.media

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.util.TreeSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Songs heard lately, kept on disk so seeking and hearing them again do not ask the network. The oldest go first once
 * the cache passes its limit. Music only: 听书 never uses it.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class MusicMediaCache(context: Context) {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences("music-cache", Context.MODE_PRIVATE)
    private val evictor = LimitEvictor(preferences.getLong(LimitKey, DefaultLimit))

    /** Links of the songs on disk, oldest first; [SourceLimit] of them are enough for any recent replay. */
    private val sources = LinkedHashMap<String, String>()

    init {
        val saved = preferences.getString(SourcesKey, null)
        if (!saved.isNullOrEmpty()) try {
            val json = JSONObject(saved)
            for (key in json.keys()) sources[key] = json.optString(key)
        } catch (_: Exception) {
            // Unreadable or from an older version: the songs on disk simply ask for their link again.
        }
    }

    internal val cache: SimpleCache by lazy {
        SimpleCache(File(this.context.cacheDir, "music-media"), evictor, StandaloneDatabaseProvider(this.context))
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

    /** True when the whole of [key] is on disk. Reads the disk on first use. */
    internal fun isCached(key: String): Boolean {
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        return length > 0 && cache.isCached(key, 0, length)
    }

    /**
     * The link [key] was downloaded from, so a song already on disk opens without asking the plugin again — the point
     * of the cache: it plays at once, and plays offline. Null when it was never written down.
     */
    internal fun sourceOf(key: String): String? = synchronized(sources) { sources[key] }

    internal fun rememberSource(key: String, url: String) {
        if (url.isEmpty()) return
        synchronized(sources) {
            if (sources[key] == url) return
            sources.remove(key)
            sources[key] = url
            while (sources.size > SourceLimit) sources.remove(sources.keys.first())
            preferences.edit().putString(SourcesKey, JSONObject(sources as Map<*, *>).toString()).apply()
        }
    }

    /** Forgets the link of [key], once it stopped working. */
    internal fun forgetSource(key: String) {
        synchronized(sources) {
            if (sources.remove(key) == null) return
            preferences.edit().putString(SourcesKey, JSONObject(sources as Map<*, *>).toString()).apply()
        }
    }

    suspend fun usedBytes(): Long = withContext(Dispatchers.IO) { cache.cacheSpace }

    suspend fun clear() = withContext(Dispatchers.IO) {
        val cache = cache
        cache.keys.toList().forEach(cache::removeResource)
        synchronized(sources) {
            sources.clear()
            preferences.edit().remove(SourcesKey).apply()
        }
    }

    companion object {
        /** The limits offered; 512 MB is MusicFree's default. */
        val Limits = listOf(256_000_000L, 512_000_000L, 1_000_000_000L, 2_000_000_000L, 4_000_000_000L)
        const val DefaultLimit = 512_000_000L
        private const val LimitKey = "limit"
        private const val SourcesKey = "sources"
        private const val SourceLimit = 300
    }
}

/** Least recently used first, like Media3's own evictor, but with a limit that can change while the cache is open. */
@androidx.annotation.OptIn(UnstableApi::class)
private class LimitEvictor(@Volatile var limit: Long) : CacheEvictor {
    private val spans = TreeSet<CacheSpan> { a, b ->
        when {
            a.lastTouchTimestamp != b.lastTouchTimestamp -> a.lastTouchTimestamp.compareTo(b.lastTouchTimestamp)
            else -> a.compareTo(b)
        }
    }
    private var size = 0L

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() = Unit

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) evict(cache, length)
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        spans.add(span)
        size += span.length
        evict(cache, 0)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        spans.remove(span)
        size -= span.length
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    /** Called with the cache's lock held. */
    fun trim(cache: Cache) = evict(cache, 0)

    private fun evict(cache: Cache, required: Long) {
        while (size + required > limit && spans.isNotEmpty()) cache.removeSpan(spans.first())
    }
}
