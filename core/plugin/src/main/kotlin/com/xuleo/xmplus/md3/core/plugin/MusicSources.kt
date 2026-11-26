package com.xuleo.xmplus.md3.core.plugin

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.AtomicFile
import com.xuleo.xmplus.md3.core.model.MusicItem
import java.io.File
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Where a song plays from, by MusicFree's `getMediaSource` rules, in order: a downloaded file; the media cache when the
 * plugin's `cacheControl` allows it (`cache`, or `no-cache` while offline); the alternative plugin set for the song's
 * plugin; the plugin itself. A plugin without `getMediaSource` plays the song's own link. A plugin that throws is asked
 * once more after 150 ms; one that answers without a link is not asked again. Null when there is no source.
 */
class MusicSources(
    context: Context,
    private val manager: PluginManager,
    /** A finished download of the song, which plays without asking a plugin (MP-01). */
    private val localFile: (MusicItem) -> File? = { null },
) {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val cache = MediaSourceCache(File(context.applicationContext.filesDir, "music-media-cache.json"))

    suspend fun mediaSource(song: MusicItem, quality: String = DefaultQuality): MediaSource? {
        localFile(song)?.takeIf { withContext(Dispatchers.IO) { it.isFile } }?.let {
            return MediaSource(Uri.fromFile(it).toString(), emptyMap(), quality, it)
        }
        val installed = manager.plugins.value.orEmpty()
        val own = installed.firstOrNull { it.platform == song.platform }?.takeIf { it.enabled } ?: return null
        // The song's own plugin decides about the cache, also when another plugin is asked for the link.
        val cacheControl = (own.info.declared["cacheControl"] as? JsonPrimitive)?.contentOrNull ?: NoCache
        val cached = cache.get(song.key, quality)
        if (cached != null && (cacheControl == Cache || (cacheControl == NoCache && offline()))) return cached
        val parser = own.meta.alternative?.let { name ->
            installed.firstOrNull { it.platform == name && it.enabled && GetMediaSource in it.info.methods }
        } ?: own
        if (GetMediaSource !in parser.info.methods) {
            return (song.qualityUrl(quality) ?: song.raw.string("url"))?.takeIf { it.isNotEmpty() }?.let { authorized(it, emptyMap(), quality) }
        }
        var attempts = 2
        while (true) {
            try {
                val host = manager.host(parser.platform) ?: throw PluginCallException("unavailable")
                val root = host.call(GetMediaSource, listOf(song.raw, JsonPrimitive(quality)))?.takeUnless { it is JsonNull }
                // Nothing returned means the song's own link for that quality; an answer without a link is final.
                val url = (if (root == null) song.qualityUrl(quality) else (root as? JsonObject)?.string("url"))
                    ?.takeIf { it.isNotEmpty() } ?: return null
                val source = authorized(url, ((root as? JsonObject)?.get("headers") as? JsonObject).strings(), quality)
                if (cacheControl != NoStore) cache.put(song.key, quality, source)
                return source
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (--attempts == 0) return null
                delay(RetryDelayMs)
            }
        }
    }

    /** MusicFree's `formatAuthUrl`: `user:pass@` in a link becomes a Basic `Authorization` header. */
    private fun authorized(url: String, headers: Map<String, String>, quality: String): MediaSource {
        val parsed = url.toHttpUrlOrNull()
        if (parsed == null || parsed.username.isEmpty() || parsed.password.isEmpty()) return MediaSource(url, headers, quality)
        val token = Base64.getEncoder().encodeToString("${parsed.username}:${parsed.password}".toByteArray())
        return MediaSource(parsed.newBuilder().username("").password("").build().toString(), headers + ("Authorization" to "Basic $token"), quality)
    }

    private fun offline(): Boolean {
        val manager = connectivity ?: return false
        return try {
            manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true
        } catch (_: SecurityException) {
            false
        }
    }

    private companion object {
        const val DefaultQuality = "standard"
        const val GetMediaSource = "getMediaSource"
        const val Cache = "cache"
        const val NoCache = "no-cache"
        const val NoStore = "no-store"
        const val RetryDelayMs = 150L
    }
}

/**
 * MusicFree's media cache: the links plugins gave, per song and quality, kept across launches. When it holds 800 songs
 * the oldest half is dropped.
 */
private class MediaSourceCache(file: File) {
    private val file = AtomicFile(file)
    private val lock = Mutex()
    private var songs: LinkedHashMap<String, JsonObject>? = null

    suspend fun get(key: String, quality: String): MediaSource? = lock.withLock {
        val entry = load()[key]?.get(quality) as? JsonObject ?: return@withLock null
        val url = entry.string("url")?.takeIf { it.isNotEmpty() } ?: return@withLock null
        val headers = (entry["headers"] as? JsonObject).strings()
        val agent = entry.string("userAgent")?.takeIf { agent -> agent.isNotEmpty() && headers.keys.none { it.equals(UserAgent, true) } }
        MediaSource(url, if (agent == null) headers else headers + (UserAgent to agent), quality)
    }

    suspend fun put(key: String, quality: String, source: MediaSource) = lock.withLock {
        val all = load()
        if (all.size >= MaxSongs) all.keys.take(MaxSongs / 2).forEach { all.remove(it) }
        val previous = all.remove(key) ?: JsonObject(emptyMap())
        all[key] = JsonObject(previous + (quality to buildJsonObject {
            put("url", source.url)
            put("headers", JsonObject(source.headers.mapValues { JsonPrimitive(it.value) }))
            source.headers[UserAgent]?.let { put("userAgent", it) }
        }))
        withContext(Dispatchers.IO) {
            val stream = try { file.startWrite() } catch (_: Exception) { return@withContext }
            try {
                stream.write(JsonObject(all).toString().encodeToByteArray())
                file.finishWrite(stream)
            } catch (_: Exception) {
                // An unsaved link is asked again next time.
                file.failWrite(stream)
            }
        }
    }

    private suspend fun load(): LinkedHashMap<String, JsonObject> = songs ?: withContext(Dispatchers.IO) {
        val read = LinkedHashMap<String, JsonObject>()
        try {
            if (file.baseFile.exists()) Json.parseToJsonElement(file.readFully().decodeToString()).jsonObject.forEach { (key, value) ->
                if (value is JsonObject) read[key] = value
            }
        } catch (_: Exception) {
            // A damaged cache only means the links are asked again.
        }
        read
    }.also { songs = it }

    private companion object {
        const val MaxSongs = 800
        const val UserAgent = "user-agent"
    }
}

private fun MusicItem.qualityUrl(quality: String): String? = ((raw["qualities"] as? JsonObject)?.get(quality) as? JsonObject)?.string("url")

private fun JsonObject?.strings(): Map<String, String> =
    this?.mapNotNull { (key, value) -> (value as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.let { key to it } }?.toMap().orEmpty()

private fun JsonObject.string(name: String): String? {
    val value: JsonElement = this[name] ?: return null
    return (value as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
}
