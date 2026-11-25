package com.xuleo.xmplus.md3.core.media

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.xuleo.xmplus.md3.core.model.MusicItem
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** The music queue as it was when the app last ran. Songs are kept as the plugins' objects, links are not kept. */
internal data class MusicSession(
    val songs: List<MusicItem>,
    /** When each song was added, so that leaving 随机 still finds the order songs were added in. */
    val order: List<Long>,
    val currentKey: String?,
    val positionMs: Long,
    val quality: MusicQuality?,
)

/** Brings the music queue back paused after the app is swiped away, killed or the phone restarts. */
internal class MusicSessionStore(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, "music-session.json"))
    private val preferences = context.applicationContext.getSharedPreferences("music-playback", Context.MODE_PRIVATE)
    private val access = Mutex()
    private var written = 0L

    /** 播放模式 outlives the queue, as in MusicFree. */
    var mode: MusicPlayMode
        get() = MusicPlayMode.fromKey(preferences.getString(ModeKey, null))
        set(value) = preferences.edit().putString(ModeKey, value.key).apply()

    suspend fun read(): MusicSession? = access.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (!file.baseFile.exists()) return@withContext null
                val root = Json.parseToJsonElement(file.readFully().decodeToString()).jsonObject
                val songs = (root["songs"] as? JsonArray).orEmpty().mapNotNull { element ->
                    val raw = element as? JsonObject ?: return@mapNotNull null
                    val platform = (raw["platform"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                    MusicItem.from(raw, platform)
                }
                if (songs.isEmpty()) return@withContext null
                MusicSession(
                    songs = songs,
                    order = (root["order"] as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.longOrNull ?: 0L },
                    currentKey = (root["current"] as? JsonPrimitive)?.contentOrNull,
                    positionMs = (root["position"] as? JsonPrimitive)?.longOrNull?.coerceAtLeast(0) ?: 0L,
                    quality = MusicQuality.fromKey((root["quality"] as? JsonPrimitive)?.contentOrNull),
                )
            } catch (_: Exception) {
                // A damaged file only means there is nothing to bring back.
                null
            }
        }
    }

    /** Writes in the order [version]s were taken; a save that lost the race to a newer one is dropped. */
    suspend fun save(version: Long, session: MusicSession?) = access.withLock {
        if (version < written) return@withLock
        written = version
        withContext(Dispatchers.IO) {
            if (session == null) {
                file.delete()
                return@withContext
            }
            val text = buildJsonObject {
                put("songs", JsonArray(session.songs.map { it.raw }))
                put("order", buildJsonArray { session.order.forEach { add(JsonPrimitive(it)) } })
                session.currentKey?.let { put("current", it) }
                put("position", session.positionMs)
                session.quality?.let { put("quality", it.key) }
            }.toString()
            val stream = try {
                file.startWrite()
            } catch (_: Exception) {
                Log.w("MusicSession", "The music queue could not be saved")
                return@withContext
            }
            try {
                stream.write(text.encodeToByteArray())
                file.finishWrite(stream)
            } catch (_: Exception) {
                file.failWrite(stream)
                Log.w("MusicSession", "The music queue could not be saved")
            }
        }
    }

    private companion object {
        const val ModeKey = "mode"
    }
}
