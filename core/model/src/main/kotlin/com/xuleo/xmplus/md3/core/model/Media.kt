package com.xuleo.xmplus.md3.core.model

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Where a playable item comes from. Stored by [key], so keys never change. */
enum class ContentKind(val key: String) {
    Episode("episode"), Song("song");

    companion object {
        /** Rows written before music existed carry no kind; they are 听书 episodes. */
        fun fromKey(key: String?): ContentKind = entries.firstOrNull { it.key == key } ?: Episode
    }
}

/** What the shared UI (mini player, notification, 最近播放, downloads) shows, whichever kind of content it is. */
sealed interface Playable {
    val kind: ContentKind
    /** `xmly:<id>` for episodes, `mf:<platform>:<id>` for songs. */
    val key: String
    val title: String
    val subtitle: String
    val coverUrl: String?
    val durationSeconds: Double

    data class Episode(val track: Track, val album: Album) : Playable {
        override val kind get() = ContentKind.Episode
        override val key get() = track.key
        override val title get() = track.title.ifBlank { album.title }
        override val subtitle get() = album.title.ifBlank { track.albumTitle }
        override val coverUrl get() = track.coverUrl ?: album.coverUrl
        override val durationSeconds get() = track.durationSeconds
    }

    data class Song(val music: MusicItem) : Playable {
        override val kind get() = ContentKind.Song
        override val key get() = music.key
        override val title get() = music.title
        override val subtitle get() = music.artist
        override val coverUrl get() = music.artwork
        override val durationSeconds get() = music.durationSeconds
    }
}

/**
 * An object returned by a plugin. [raw] is kept exactly as the plugin returned it and is passed back unchanged on
 * every later call, so fields the app does not know survive (MusicFree keeps the whole object the same way).
 */
sealed interface PluginMedia {
    val platform: String
    val id: String
    val raw: JsonObject
}

/** A song, MusicFree's `IMusicItem`. */
data class MusicItem(
    override val platform: String,
    override val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val artwork: String?,
    val durationSeconds: Double,
    override val raw: JsonObject,
) : PluginMedia {
    val key: String get() = musicKey(platform, id)
    fun isSame(other: MusicItem) = platform == other.platform && id == other.id

    companion object {
        /** Null when the object has no `id`; [platform] is the plugin's and replaces whatever the object said. */
        fun from(raw: JsonObject, platform: String): MusicItem? {
            val id = raw.text("id") ?: return null
            val fixed = raw.withPlatform(platform)
            return MusicItem(
                platform = platform,
                id = id,
                title = raw.text("title").orEmpty(),
                artist = raw.text("artist").orEmpty(),
                album = raw.text("album").orEmpty(),
                artwork = raw.text("artwork")?.takeIf { it.isNotBlank() },
                durationSeconds = raw.number("duration") ?: 0.0,
                raw = fixed,
            )
        }
    }
}

/** A sheet or a ranking, MusicFree's `IMusicSheetItemBase`. */
data class MusicSheet(
    override val platform: String,
    override val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val description: String,
    val worksNum: Int?,
    /** Play count as the plugin gave it, when it did. */
    val playCount: Long?,
    override val raw: JsonObject,
) : PluginMedia {
    companion object {
        fun from(raw: JsonObject, platform: String): MusicSheet? {
            val id = raw.text("id") ?: return null
            return MusicSheet(
                platform = platform,
                id = id,
                title = raw.text("title").orEmpty(),
                artist = raw.text("artist").orEmpty(),
                coverUrl = (raw.text("coverImg") ?: raw.text("artwork"))?.takeIf { it.isNotBlank() },
                description = raw.text("description").orEmpty(),
                worksNum = raw.number("worksNum")?.toInt(),
                playCount = raw.number("playCount")?.toLong(),
                raw = raw.withPlatform(platform),
            )
        }
    }
}

/** An album, MusicFree's `IAlbumItemBase`. */
data class MusicAlbum(
    override val platform: String,
    override val id: String,
    val title: String,
    val artist: String,
    val artwork: String?,
    val date: String,
    val description: String,
    val worksNum: Int?,
    override val raw: JsonObject,
) : PluginMedia {
    companion object {
        fun from(raw: JsonObject, platform: String): MusicAlbum? {
            val id = raw.text("id") ?: return null
            return MusicAlbum(
                platform = platform,
                id = id,
                title = raw.text("title").orEmpty(),
                artist = raw.text("artist").orEmpty(),
                artwork = raw.text("artwork")?.takeIf { it.isNotBlank() },
                date = raw.text("date").orEmpty(),
                description = raw.text("description").orEmpty(),
                worksNum = raw.number("worksNum")?.toInt(),
                raw = raw.withPlatform(platform),
            )
        }
    }
}

/** An artist, MusicFree's `IArtistItemBase`. */
data class MusicArtist(
    override val platform: String,
    override val id: String,
    val name: String,
    val avatar: String?,
    val fans: Long?,
    val description: String,
    val worksNum: Int?,
    override val raw: JsonObject,
) : PluginMedia {
    companion object {
        fun from(raw: JsonObject, platform: String): MusicArtist? {
            val id = raw.text("id") ?: return null
            return MusicArtist(
                platform = platform,
                id = id,
                name = raw.text("name").orEmpty(),
                avatar = raw.text("avatar")?.takeIf { it.isNotBlank() },
                fans = raw.number("fans")?.toLong(),
                description = raw.text("description").orEmpty(),
                worksNum = raw.number("worksNum")?.toInt(),
                raw = raw.withPlatform(platform),
            )
        }
    }
}

fun musicKey(platform: String, id: String) = "mf:$platform:$id"

/** MusicFree sets `platform` on every item a plugin returns, overriding the plugin's own value. */
private fun JsonObject.withPlatform(platform: String): JsonObject =
    if ((this["platform"] as? JsonPrimitive)?.content == platform) this
    else JsonObject(this + ("platform" to JsonPrimitive(platform)))

/** Strings and numbers both read as text: plugins return ids as either. */
private fun JsonObject.text(name: String): String? {
    val value: JsonElement = this[name] ?: return null
    if (value is JsonNull || value !is JsonPrimitive) return null
    if (value.isString) return value.content
    return value.longOrNull?.toString() ?: value.doubleOrNull?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
}

private fun JsonObject.number(name: String): Double? {
    val value = this[name] as? JsonPrimitive ?: return null
    if (value is JsonNull) return null
    return value.doubleOrNull ?: value.content.toDoubleOrNull()
}
