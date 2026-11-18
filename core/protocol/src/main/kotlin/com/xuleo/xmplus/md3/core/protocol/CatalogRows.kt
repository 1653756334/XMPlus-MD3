package com.xuleo.xmplus.md3.core.protocol

import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.AnchorProfile
import com.xuleo.xmplus.md3.core.model.Track
import com.xuleo.xmplus.md3.core.model.xmlyIdentifier
import java.net.URI
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** Id fields that mean "a positive number": an integer, or a string of digits. Anything else is absent. */
internal fun Reader.positiveOrNull(value: JsonElement?): Int? {
    val primitive = value as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    val digits = primitive.content.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } } ?: return null
    return digits.toLongOrNull()?.takeIf { it in 1..Int.MAX_VALUE }?.toInt()
}

private fun first(row: JsonObject, vararg names: String): JsonElement? =
    names.firstNotNullOfOrNull { row[it]?.takeIf { value -> value !is JsonNull } }

/** A number that is finite and not negative, as Dart's `num` check allows; strings do not count. */
private fun finiteCount(value: JsonElement?): Long? {
    val primitive = value as? JsonPrimitive ?: return null
    if (primitive.isString) return null
    val number = primitive.doubleOrNull ?: return null
    return if (number.isFinite() && number >= 0) number.toLong() else null
}

/** The covers a catalogue row may carry, best first. */
private fun Reader.coverOf(row: JsonObject): String? = cover(
    row["coverLarge"], row["coverMiddle"], row["coverSmall"], row["cover_path"], row["album_cover_path"], row["cover_url"], row["albumImage"],
)

private fun Reader.avatarOf(value: JsonElement?): String? {
    val url = cover(value) ?: return null
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    return url.takeIf { uri.rawQuery == null && uri.rawFragment == null && (uri.port == -1 || uri.port == 80 || uri.port == 443) }
}

private fun Reader.nickname(row: JsonObject): String =
    anyText(first(row, "nickname") ?: (row["userInfo"] as? JsonObject)?.get("nickname"))

/** An album row from search or the album header. */
internal fun Reader.albumOf(row: JsonObject): Album = Album(
    id = xmlyIdentifier(anyText(first(row, "albumId", "id"))),
    title = anyText(row["title"]),
    artist = nickname(row),
    intro = anyText(row["intro"]),
    coverUrl = coverOf(row),
    trackCount = (finiteCount(first(row, "tracks", "totalCount", "total")) ?: 0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    anchorUid = positiveOrNull(row["uid"])?.toString(),
    anchorAvatarUrl = avatarOf(row["avatarPath"]),
)

/** A track row; [album] fills what the row leaves out, and [position] is the catalogue position that fixes its order. */
internal fun Reader.trackOf(row: JsonObject, album: Album? = null, position: Int? = null): Track {
    val duration = (row["duration"] as? JsonPrimitive)?.takeIf { !it.isString && it !is JsonNull }?.doubleOrNull
    val albumId = anyText(first(row, "album_id", "albumId")).ifEmpty { album?.id.orEmpty() }
    return Track(
        id = xmlyIdentifier(anyText(first(row, "trackId", "id"))),
        title = anyText(row["title"]),
        artist = nickname(row).ifEmpty { album?.artist.orEmpty() },
        albumTitle = anyText(first(row, "album_title", "albumTitle")).ifEmpty { album?.title.orEmpty() },
        albumId = if (albumId.isEmpty()) "" else xmlyIdentifier(albumId),
        durationSeconds = duration?.takeIf { it.isFinite() && it >= 0 } ?: 0.0,
        orderNo = position ?: positiveOrNull(row["orderNo"]) ?: positiveOrNull(row["orderNum"]),
        episode = positiveOrNull(row["episode"]),
        paid = boolean(first(row, "is_paid", "isPaid")),
        authorized = boolean(first(row, "isAuthorized", "is_authorized")),
        coverUrl = coverOf(row) ?: album?.coverUrl,
        intro = anyText(row["intro"]),
        likes = countInt(row["likes"]),
        playCount = count(row["play"]) ?: count(first(row, "playtimes", "count_play")),
        publishedAtMillis = count(row["date"]) ?: count(first(row, "createdAt", "created_at"))?.takeIf { it > 0 },
        commentCount = countInt(row["count_comment"]),
        officialPosition = position,
        historyDirection = when (val direction = row["direction"]) {
            null, JsonNull -> 0
            is JsonPrimitive -> direction.content.takeUnless { direction.isString }?.toIntOrNull()?.takeIf { it in 0..1 }
            else -> null
        },
    )
}

/** An anchor row from search. */
internal fun Reader.anchorOf(row: JsonObject): AnchorProfile = AnchorProfile(
    uid = xmlyIdentifier(anyText(row["uid"])),
    nickname = anyText(row["nickname"]),
    avatarUrl = cover(row["smallPic"]),
    intro = anyText(first(row, "description", "personDescribe")),
    verified = boolean(row["isVerified"]) == true,
    fans = finiteCount(row["followers_counts"])?.toInt(),
    albums = finiteCount(row["album_counts"])?.toInt(),
    tracks = finiteCount(row["tracks_counts"])?.toInt(),
)
