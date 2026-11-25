package com.xuleo.xmplus.md3.core.media

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.xuleo.xmplus.md3.core.model.MusicItem
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * 我喜欢: the songs the user liked, newest first. Songs are kept as the plugins' objects, so they can be handed back
 * to the plugins unchanged and reused by the user's own sheets later.
 */
class MusicFavorites(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.filesDir, "music-favorites.json"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val access = Mutex()
    private val loaded = MutableStateFlow(false)
    private val mutableSongs = MutableStateFlow<List<MusicItem>>(emptyList())

    /** Liked songs, the last liked first. */
    val songs: StateFlow<List<MusicItem>> = mutableSongs.asStateFlow()

    init {
        scope.launch {
            val saved = read()
            // Songs liked before the file was read stay in front.
            mutableSongs.update { early -> early + saved.filter { song -> early.none { it.isSame(song) } } }
            loaded.value = true
            if (mutableSongs.value.size != saved.size) save()
        }
    }

    fun contains(song: MusicItem): Boolean = mutableSongs.value.any { it.isSame(song) }

    /** Adds [song] to the front, or removes it when it is already liked. Returns whether it is liked now. */
    fun toggle(song: MusicItem): Boolean {
        val liked = !contains(song)
        if (liked) add(song) else remove(song)
        return liked
    }

    fun add(song: MusicItem) {
        if (contains(song)) return
        mutableSongs.update { listOf(song) + it }
        save()
    }

    fun remove(song: MusicItem) {
        if (!contains(song)) return
        mutableSongs.update { list -> list.filterNot { it.isSame(song) } }
        save()
    }

    private fun save() {
        if (!loaded.value) return
        val snapshot = mutableSongs.value
        scope.launch { write(snapshot) }
    }

    private suspend fun read(): List<MusicItem> = access.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (!file.baseFile.exists()) return@withContext emptyList()
                val root = Json.parseToJsonElement(file.readFully().decodeToString()).jsonObject
                (root["songs"] as? JsonArray).orEmpty().mapNotNull { element ->
                    val raw = element as? JsonObject ?: return@mapNotNull null
                    val platform = (raw["platform"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                    MusicItem.from(raw, platform)
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    private suspend fun write(songs: List<MusicItem>) = access.withLock {
        withContext(Dispatchers.IO) {
            val text = buildJsonObject { put("songs", JsonArray(songs.map { it.raw })) }.toString()
            val stream = try {
                file.startWrite()
            } catch (_: Exception) {
                Log.w("MusicFavorites", "The liked songs could not be saved")
                return@withContext
            }
            try {
                stream.write(text.encodeToByteArray())
                file.finishWrite(stream)
            } catch (_: Exception) {
                file.failWrite(stream)
                Log.w("MusicFavorites", "The liked songs could not be saved")
            }
        }
    }
}
