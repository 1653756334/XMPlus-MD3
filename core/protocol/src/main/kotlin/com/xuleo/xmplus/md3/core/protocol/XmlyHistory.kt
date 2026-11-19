package com.xuleo.xmplus.md3.core.protocol

import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.ExternalRetry
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.model.dartTrim
import com.xuleo.xmplus.md3.core.model.xmlyIdentifier
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class CloudPlaybackProgress(val positionMs: Long, val observedAtMs: Long)

/** A track in cloud history with the record that places it there. [durationSeconds] is 0 when the server leaves it out. */
data class CloudListen(
    val albumId: String,
    val albumTitle: String,
    val coverUrl: String?,
    val trackId: String,
    val trackTitle: String,
    val durationSeconds: Long,
    val progress: CloudPlaybackProgress,
)

/** A page of the 云历史 list: the tracks it adds, and the page to ask for next when the history has more. */
data class CloudListingPage(val items: List<CloudListen>, val nextPage: Int?)

/** XMPlus listening history. Neither operation reads or spends listening time. */
class XmlyHistory(private val http: XmlyHttp, private val clock: () -> Long = System::currentTimeMillis) {
    private val reader = Reader("invalid_history")

    private data class Record(
        val albumId: String,
        val albumTitle: String,
        val coverUrl: String?,
        val trackId: String,
        val trackTitle: String,
        val durationSeconds: Long,
        val progress: CloudPlaybackProgress,
    )

    suspend fun progress(credentials: Credentials, albumId: String, trackId: String): CloudPlaybackProgress? {
        val album = xmlyIdentifier(albumId)
        val track = xmlyIdentifier(trackId)
        var latest: Record? = null
        // History comes newest first, so the first record of the track is its latest listen and the read stops there.
        // A timeout may still have a usable result from a fully validated page; an invalid response may not.
        withTimeoutOrNull(ResumeScanTimeoutMs) {
            scan(credentials, track) { records ->
                latest = records.firstOrNull() ?: latest
                latest == null
            }
        }
        // Like the original list, resolve duplicate track IDs before checking the album.
        return latest?.takeIf { it.albumId == album }?.progress
    }

    /** Cloud history for the 云历史 list, one page at a time, newest first. A list keeps the pages it has read. */
    fun listing(): Listing = Listing()

    /**
     * The 云历史 list read a page at a time. A track is listed once, at its newest record, so a track played again on a
     * later page is not listed twice. A page that adds nothing new is read past, until one adds a track or the history ends.
     */
    inner class Listing {
        private val lock = Mutex()
        private val listed = HashSet<String>()
        private val pageDigests = HashSet<List<Byte>>()

        /** The page [number] of the list. Page 1 starts the list again. Nothing is kept unless the whole call succeeds. */
        suspend fun page(credentials: Credentials, number: Int): CloudListingPage = lock.withLock {
            if (number == 1) {
                listed.clear()
                pageDigests.clear()
            }
            val items = ArrayList<CloudListen>()
            val fresh = HashSet<String>()
            val digests = HashSet<List<Byte>>()
            var page = number
            var next: Int? = null
            while (true) {
                if (page > MaxListingPages) throw XmlyException("history_limit")
                val data = readPage(credentials, page)
                val rows = data["listenModels"] as? JsonArray ?: reader.invalid()
                val hasMore = when (val value = data["hasMore"]) {
                    null, JsonNull -> false
                    else -> reader.boolean(value) ?: reader.invalid()
                }
                if (rows.size > PageSize || (hasMore && rows.isEmpty())) reader.invalid()
                if (rows.isNotEmpty()) {
                    val digest = MessageDigest.getInstance("SHA-256").digest(rows.toString().encodeToByteArray()).toList()
                    if (digest in pageDigests || !digests.add(digest)) throw XmlyException("repeated_page")
                }
                for (record in rows.mapNotNull { record(it, null) }) {
                    if (record.trackId !in listed && fresh.add(record.trackId)) {
                        items += CloudListen(
                            record.albumId, record.albumTitle, record.coverUrl, record.trackId, record.trackTitle,
                            record.durationSeconds, record.progress,
                        )
                    }
                }
                page++
                next = page.takeIf { hasMore }
                if (!hasMore || items.isNotEmpty()) break
            }
            listed += fresh
            pageDigests += digests
            CloudListingPage(items, next)
        }
    }

    private suspend fun scan(credentials: Credentials, trackId: String? = null, onPage: (List<Record>) -> Boolean) {
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
            val records = rows.mapNotNull { record(it, trackId) }
            currentCoroutineContext().ensureActive()
            val keepReading = onPage(records)
            if (!keepReading || !hasMore) return
        }
        throw XmlyException("history_limit")
    }

    /** The caller supplies a durable snapshot. A timeout, 429 or transport failure is never retried here. */
    suspend fun upload(
        credentials: Credentials,
        albumId: String,
        trackId: String,
        positionMs: Long,
        startedAtMs: Long,
        endedAtMs: Long,
        direction: Int,
    ) {
        val album = xmlyIdentifier(albumId)
        val track = xmlyIdentifier(trackId)
        require(positionMs in 0..MaxSeconds * 1_000L && startedAtMs in 1..<endedAtMs && endedAtMs <= MaxObservedAtMs)
        require(direction in 0..1)
        val row = buildJsonObject {
            put("endedAt", endedAtMs.toString())
            put("startedAt", startedAtMs.toString())
            put("type", "1")
            put("itemId", album)
            put("childId", track)
            put("breakSecond", (positionMs / 1_000L).toString())
            put("direction", direction)
        }
        val records = JsonArray(listOf(row)).toString()
        val response = withContext(ExternalRetry) {
            http.call(Endpoints.HistoryMerge, credentials, historyForm = mapOf(
                "mergeRecords" to records,
                "signature" to uploadSignature(credentials, records),
            ))
        }
        val syncPoint = reader.anyText(responseData(response["data"])["syncPoint"]).dartTrim().toLongOrNull()
        if (syncPoint == null || syncPoint < 0) throw XmlyException("history_unconfirmed")
    }

    private fun uploadSignature(credentials: Credentials, records: String): String {
        val parameters = sortedMapOf(
            "channel" to "and-f5", "device" to "android", "deviceId" to credentials.deviceId,
            "impl" to "com.ximalaya.ting.lite", "mergeRecords" to records,
            "token" to credentials.token.substringAfter('&'), "uid" to credentials.uid, "version" to "3.4.10",
        )
        val payload = buildString {
            for ((name, value) in parameters) append(name).append('=').append(value).append('&')
            append(SignatureMaterial)
            for (index in indices) if (this[index] in 'A'..'Z') setCharAt(index, this[index] + 32)
        }
        return MessageDigest.getInstance("MD5").digest(payload.encodeToByteArray()).toHexString()
    }

    private suspend fun readPage(credentials: Credentials, page: Int): JsonObject {
        val response = http.call(Endpoints.HistoryQuery, credentials, query = mapOf(
            "pageId" to page.toString(),
            "pageSize" to PageSize.toString(),
            "syncPoint" to "0",
            "timeStamp" to clock().toString(),
            "uid" to credentials.uid,
        ))
        return responseData(response["data"])
    }

    private fun responseData(data: JsonElement?): JsonObject = reader.obj(
        if (data is JsonPrimitive && data.isString) {
            try {
                Json.parseToJsonElement(data.content)
            } catch (_: IllegalArgumentException) {
                reader.invalid()
            }
        } else data,
    )

    private fun record(raw: JsonElement, requestedTrack: String?): Record? {
        val row = raw as? JsonObject ?: return null
        val track = reader.anyText(row["childId"]).removePrefix("xmly:")
        if (requestedTrack != null && track != requestedTrack || !HistoryId.matches(track)) return null
        val type = integer(row["type"])
        if ((type != 1L && type != 9L) || integer(row["deleteTime"]) > 0) return null
        val album = reader.anyText(row["itemId"]).removePrefix("xmly:").takeIf(HistoryId::matches) ?: return null
        val duration = integer(row["length"]).coerceIn(0, MaxSeconds)
        val seconds = integer(row["breakSecond"]).coerceIn(0, duration.takeIf { it > 0 } ?: MaxSeconds)
        val observedAt = integer(row["endedAt"]).coerceIn(0, MaxObservedAtMs)
        return Record(
            albumId = album,
            albumTitle = reader.anyText(row["itemTitle"]),
            coverUrl = reader.image(row["itemCoverUrl"]),
            trackId = track,
            trackTitle = reader.anyText(row["childTitle"]),
            durationSeconds = duration,
            progress = CloudPlaybackProgress(seconds * 1_000L, observedAt),
        )
    }

    private fun integer(value: JsonElement?): Long = reader.anyText(value).dartTrim().toLongOrNull() ?: 0L

    private companion object {
        const val ResumeScanTimeoutMs = 2_000L
        const val PageSize = 100
        const val MaxPages = 50
        const val MaxListingPages = 1_000
        const val MaxSeconds = Int.MAX_VALUE.toLong()
        const val MaxObservedAtMs = 2_115_354_119_000L
        const val SignatureMaterial = "23627d1451047b8d257a96af5db359538f081d651df75b4aa169508547208159"
        val HistoryId = Regex("[1-9][0-9]{0,18}")
    }
}
