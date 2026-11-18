package com.xuleo.xmplus.md3.core.protocol

import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.model.dartTrim
import com.xuleo.xmplus.md3.core.model.xmlyIdentifier
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class CloudPlaybackProgress(val positionMs: Long, val observedAtMs: Long)

/** The read side of XMPlus history. It neither uploads history nor reads or spends listening time. */
class XmlyHistory(private val http: XmlyHttp, private val clock: () -> Long = System::currentTimeMillis) {
    private val reader = Reader("invalid_history")

    private data class Record(val albumId: String, val progress: CloudPlaybackProgress)

    suspend fun progress(credentials: Credentials, albumId: String, trackId: String): CloudPlaybackProgress? {
        val album = xmlyIdentifier(albumId)
        val track = xmlyIdentifier(trackId)
        var latest: Record? = null
        // A timeout may still have a usable result from a fully validated page; an invalid response may not.
        withTimeoutOrNull(ScanTimeoutMs) {
            val digest = MessageDigest.getInstance("SHA-256")
            val seenPages = hashSetOf<List<Byte>>()
            for (page in 1..MaxPages) {
                val data = readPage(credentials, page)
                val rows = data["listenModels"] as? JsonArray ?: reader.invalid()
                val hasMore = when (val value = data["hasMore"]) {
                    null, JsonNull -> false
                    else -> reader.boolean(value) ?: reader.invalid()
                }
                if (rows.size > PageSize || (hasMore && rows.isEmpty())) reader.invalid()
                if (rows.isNotEmpty() && !seenPages.add(digest.digest(rows.toString().encodeToByteArray()).toList())) {
                    throw XmlyException("repeated_page")
                }

                val newest = rows.asSequence().mapNotNull { record(it, track) }.maxByOrNull { it.progress.observedAtMs }
                currentCoroutineContext().ensureActive()
                if (newest != null && newest.progress.observedAtMs > (latest?.progress?.observedAtMs ?: -1)) latest = newest
                if (!hasMore) return@withTimeoutOrNull
            }
            throw XmlyException("history_limit")
        }
        // Like the original list, resolve duplicate track IDs before checking the album.
        return latest?.takeIf { it.albumId == album }?.progress
    }

    private suspend fun readPage(credentials: Credentials, page: Int): JsonObject {
        val response = http.call(Endpoints.HistoryQuery, credentials, query = mapOf(
            "pageId" to page.toString(),
            "pageSize" to PageSize.toString(),
            "syncPoint" to "0",
            "timeStamp" to clock().toString(),
            "uid" to credentials.uid,
        ))
        val data = response["data"]
        return reader.obj(if (data is JsonPrimitive && data.isString) {
            try {
                Json.parseToJsonElement(data.content)
            } catch (_: IllegalArgumentException) {
                reader.invalid()
            }
        } else data)
    }

    private fun record(raw: JsonElement, track: String): Record? {
        val row = raw as? JsonObject ?: return null
        if (reader.anyText(row["childId"]).removePrefix("xmly:") != track) return null
        val type = integer(row["type"])
        if ((type != 1L && type != 9L) || integer(row["deleteTime"]) > 0) return null
        val album = reader.anyText(row["itemId"]).removePrefix("xmly:").takeIf(HistoryId::matches) ?: return null
        val duration = integer(row["length"]).coerceIn(0, MaxSeconds)
        val seconds = integer(row["breakSecond"]).coerceIn(0, duration.takeIf { it > 0 } ?: MaxSeconds)
        val observedAt = integer(row["endedAt"]).coerceIn(0, MaxObservedAtMs)
        return Record(album, CloudPlaybackProgress(seconds * 1_000L, observedAt))
    }

    private fun integer(value: JsonElement?): Long = reader.anyText(value).dartTrim().toLongOrNull() ?: 0L

    private companion object {
        const val ScanTimeoutMs = 2_000L
        const val PageSize = 100
        const val MaxPages = 50
        const val MaxSeconds = Int.MAX_VALUE.toLong()
        const val MaxObservedAtMs = 2_115_354_119_000L
        val HistoryId = Regex("[1-9][0-9]{0,18}")
    }
}
