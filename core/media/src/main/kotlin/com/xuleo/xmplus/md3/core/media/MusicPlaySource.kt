package com.xuleo.xmplus.md3.core.media

import com.xuleo.xmplus.md3.core.model.MusicItem
import com.xuleo.xmplus.md3.core.model.PluginMedia
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * The list a queue was played from, kept with 最近播放 so that the list can be named and opened again. [id] is a user
 * sheet's id for [Type.Sheet]; plugin lists carry the plugin's [platform], [id] and full object [raw].
 */
data class MusicPlaySource(
    val type: Type,
    val platform: String? = null,
    val id: String? = null,
    val raw: JsonObject? = null,
) {
    enum class Type(val key: String) {
        Likes("likes"), Sheet("sheet"), PluginSheet("pluginSheet"), TopList("topList"), Album("album"),
        Artist("artist"), Search("search"), Single("single");

        companion object {
            fun fromKey(key: String?): Type? = entries.firstOrNull { it.key == key }
        }
    }

    fun toJson(): String = buildJsonObject {
        put("type", type.key)
        platform?.let { put("platform", it) }
        id?.let { put("id", it) }
        raw?.let { put("raw", it) }
    }.toString()

    companion object {
        val Likes = MusicPlaySource(Type.Likes, id = MusicLibrary.LikesId)

        fun sheet(id: String) = MusicPlaySource(Type.Sheet, id = id)

        fun of(type: Type, media: PluginMedia) = MusicPlaySource(type, media.platform, media.id, media.raw)

        fun fromJson(text: String?): MusicPlaySource? {
            if (text.isNullOrEmpty()) return null
            return try {
                val root = Json.parseToJsonElement(text).jsonObject
                val type = Type.fromKey((root["type"] as? JsonPrimitive)?.contentOrNull) ?: return null
                MusicPlaySource(
                    type,
                    (root["platform"] as? JsonPrimitive)?.contentOrNull,
                    (root["id"] as? JsonPrimitive)?.contentOrNull,
                    root["raw"] as? JsonObject,
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

/**
 * A list played as a queue, as saved with 最近播放: [token] names this play of the list, [songs] is the list as it was
 * then, at most [MaxSongs] around the song played.
 */
class SongQueue(val token: String, val source: MusicPlaySource?, val songs: List<MusicItem>) {
    /** This queue with [more] songs added at the end, up to [MaxSongs]. */
    fun plus(more: List<MusicItem>): SongQueue {
        val room = MaxSongs - songs.size
        if (room <= 0 || more.isEmpty()) return this
        val known = songs.mapTo(HashSet()) { it.key }
        val fresh = more.filter { known.add(it.key) }.take(room)
        return if (fresh.isEmpty()) this else SongQueue(token, source, songs + fresh)
    }

    companion object {
        const val MaxSongs = 500

        /** [songs] cut to [MaxSongs] around [index]. */
        fun window(songs: List<MusicItem>, index: Int): List<MusicItem> {
            val unique = songs.distinctBy { it.key }
            if (unique.size <= MaxSongs) return unique
            val at = unique.indexOfFirst { it.key == songs.getOrNull(index)?.key }.coerceAtLeast(0)
            val start = (at - MaxSongs / 2).coerceIn(0, unique.size - MaxSongs)
            return unique.subList(start, start + MaxSongs)
        }
    }
}

/**
 * What 继续听 needs to play a song again: the list it came from, where it is in it and where it stopped. [queue] is the
 * saved list when it is still kept; null means the song comes back alone.
 */
data class SongResume(
    val songs: List<MusicItem>,
    val index: Int,
    val positionMs: Long,
    val source: MusicPlaySource?,
    val queue: SongQueue?,
)
