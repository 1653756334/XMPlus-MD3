package com.xuleo.xmplus.md3.core.media

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.util.TreeSet

/** Least recently used first, like Media3's own evictor, but with a limit that can change while the cache is open. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class LimitEvictor(@Volatile var limit: Long) : CacheEvictor {
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
