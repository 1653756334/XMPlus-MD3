package com.xuleo.xmplus.md3.core.media

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.AlbumDetail
import com.xuleo.xmplus.md3.core.model.AlbumStats
import com.xuleo.xmplus.md3.core.model.Track
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** The episode that was loaded, under [account], and the album queue around it. */
internal data class PlaybackSession(
    val account: String,
    val trackId: String,
    val detail: AlbumDetail,
    val items: List<Track>,
    val firstPage: Int,
    val lastPage: Int,
    val total: Int,
)

/**
 * What was loaded when the app last ran, so that it comes back paused after the app is swiped away, killed or the
 * phone restarts. No position (that is [PlaybackProgressStore]'s), credential or media URL is kept here.
 */
class PlaybackSessionStore(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, "playback-session.json"))
    private val access = Mutex()
    private var written = 0L

    internal suspend fun read(): PlaybackSession? = access.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (!file.baseFile.exists()) return@withContext null
                decode(Json.parseToJsonElement(file.readFully().decodeToString()))
            } catch (_: Exception) {
                // A damaged or older file only means there is nothing to bring back.
                null
            }
        }
    }

    /** Writes [session], or forgets it when null. A write older than one already made is dropped. */
    internal suspend fun save(version: Long, session: PlaybackSession?) = access.withLock {
        if (version <= written) return@withLock
        written = version
        withContext(Dispatchers.IO) {
            try {
                if (session == null) {
                    file.delete()
                    return@withContext
                }
                val stream = file.startWrite()
                try {
                    stream.write(encode(session).toString().encodeToByteArray())
                    file.finishWrite(stream)
                } catch (failure: IOException) {
                    file.failWrite(stream)
                    throw failure
                }
            } catch (_: IOException) {
                Log.w("PlaybackSession", "The last playback session could not be saved")
            }
        }
    }

    private fun encode(session: PlaybackSession) = buildJsonObject {
        put("version", FormatVersion)
        put("account", session.account)
        put("track", session.trackId)
        put("detail", encodeDetail(session.detail))
        put("items", buildJsonArray { session.items.forEach { add(encodeTrack(it)) } })
        put("firstPage", session.firstPage)
        put("lastPage", session.lastPage)
        put("total", session.total)
    }

    private fun decode(element: JsonElement): PlaybackSession? {
        val root = element as? JsonObject ?: return null
        if (root.int("version") != FormatVersion) return null
        return PlaybackSession(
            account = root.text("account") ?: return null,
            trackId = root.text("track") ?: return null,
            detail = decodeDetail(root["detail"] as? JsonObject ?: return null),
            items = (root["items"] as? JsonArray ?: return null).map { decodeTrack(it as JsonObject) },
            firstPage = root.int("firstPage") ?: return null,
            lastPage = root.int("lastPage") ?: return null,
            total = root.int("total") ?: return null,
        )
    }

    private fun encodeDetail(detail: AlbumDetail) = buildJsonObject {
        put("album", buildJsonObject {
            val album = detail.album
            put("id", album.id)
            put("title", album.title)
            put("artist", album.artist)
            put("intro", album.intro)
            put("coverUrl", album.coverUrl)
            put("trackCount", album.trackCount)
            put("anchorUid", album.anchorUid)
            put("anchorAvatarUrl", album.anchorAvatarUrl)
        })
        put("stats", buildJsonObject {
            put("score", detail.stats.score)
            put("playCount", detail.stats.playCount)
            put("subscribeCount", detail.stats.subscribeCount)
            put("finished", detail.stats.finished)
        })
        put("paid", detail.paid)
        put("anchorVerified", detail.anchorVerified)
        put("tags", buildJsonArray { detail.tags.forEach { add(JsonPrimitive(it)) } })
        put("recordDescending", detail.recordDescending)
    }

    private fun decodeDetail(row: JsonObject): AlbumDetail {
        val album = row["album"] as JsonObject
        val stats = row["stats"] as JsonObject
        return AlbumDetail(
            album = Album(
                id = album.text("id")!!,
                title = album.text("title")!!,
                artist = album.text("artist")!!,
                intro = album.text("intro")!!,
                coverUrl = album.text("coverUrl"),
                trackCount = album.int("trackCount")!!,
                anchorUid = album.text("anchorUid"),
                anchorAvatarUrl = album.text("anchorAvatarUrl"),
            ),
            stats = AlbumStats(
                score = stats.double("score"),
                playCount = stats.long("playCount"),
                subscribeCount = stats.long("subscribeCount"),
                finished = stats.flag("finished")!!,
            ),
            paid = row.flag("paid")!!,
            anchorVerified = row.flag("anchorVerified")!!,
            tags = (row["tags"] as JsonArray).map { (it as JsonPrimitive).content },
            recordDescending = row.flag("recordDescending")!!,
        )
    }

    private fun encodeTrack(track: Track) = buildJsonObject {
        put("id", track.id)
        put("title", track.title)
        put("artist", track.artist)
        put("albumTitle", track.albumTitle)
        put("albumId", track.albumId)
        put("durationSeconds", track.durationSeconds)
        put("orderNo", track.orderNo)
        put("episode", track.episode)
        put("paid", track.paid)
        put("authorized", track.authorized)
        put("coverUrl", track.coverUrl)
        put("intro", track.intro)
        put("likes", track.likes)
        put("playCount", track.playCount)
        put("publishedAtMillis", track.publishedAtMillis)
        put("commentCount", track.commentCount)
        put("officialPosition", track.officialPosition)
        put("historyDirection", track.historyDirection)
    }

    private fun decodeTrack(row: JsonObject) = Track(
        id = row.text("id")!!,
        title = row.text("title")!!,
        artist = row.text("artist")!!,
        albumTitle = row.text("albumTitle")!!,
        albumId = row.text("albumId")!!,
        durationSeconds = row.double("durationSeconds")!!,
        orderNo = row.int("orderNo"),
        episode = row.int("episode"),
        paid = row.flag("paid"),
        authorized = row.flag("authorized"),
        coverUrl = row.text("coverUrl"),
        intro = row.text("intro")!!,
        likes = row.int("likes"),
        playCount = row.long("playCount"),
        publishedAtMillis = row.long("publishedAtMillis"),
        commentCount = row.int("commentCount"),
        officialPosition = row.int("officialPosition"),
        historyDirection = row.int("historyDirection"),
    )

    private fun JsonObject.primitive(name: String) = (get(name) as? JsonPrimitive)?.takeUnless { it is JsonNull }
    private fun JsonObject.text(name: String) = primitive(name)?.takeIf { it.isString }?.content
    private fun JsonObject.int(name: String) = primitive(name)?.takeUnless { it.isString }?.intOrNull
    private fun JsonObject.long(name: String) = primitive(name)?.takeUnless { it.isString }?.longOrNull
    private fun JsonObject.double(name: String) = primitive(name)?.takeUnless { it.isString }?.doubleOrNull
    private fun JsonObject.flag(name: String) = primitive(name)?.takeUnless { it.isString }?.booleanOrNull

    private companion object {
        const val FormatVersion = 1
    }
}
