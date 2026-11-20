package com.xuleo.xmplus.md3.core.protocol

import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.AlbumDetail
import com.xuleo.xmplus.md3.core.model.AlbumReplies
import com.xuleo.xmplus.md3.core.model.AlbumReply
import com.xuleo.xmplus.md3.core.model.AlbumReview
import com.xuleo.xmplus.md3.core.model.AlbumReviews
import com.xuleo.xmplus.md3.core.model.AlbumScore
import com.xuleo.xmplus.md3.core.model.AlbumSimilar
import com.xuleo.xmplus.md3.core.model.AlbumStats
import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.Page
import com.xuleo.xmplus.md3.core.model.Track
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.model.dartTrim
import com.xuleo.xmplus.md3.core.model.xmlyIdentifier
import java.net.URI
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * One album's page: its header, introduction, episodes, similar albums, reviews and search inside the album. The reads
 * are public; an account is sent only where the original app sends one.
 */
class XmlyAlbum(private val http: XmlyHttp) {
    private val headerReader = Reader("invalid_album")
    private val catalogueReader = Reader("invalid_catalogue")
    private val introReader = Reader("invalid_introduction")
    private val similarReader = Reader("invalid_similar")
    private val reviewReader = Reader("invalid_comments")
    private val searchReader = Reader("invalid_search")

    /** The header of [albumId], including the server's default episode order. */
    suspend fun detail(albumId: String): AlbumDetail {
        val id = xmlyIdentifier(albumId)
        val info = http.call(Endpoints.AlbumInfo, null, query = mapOf("albumId" to id, "device" to "android"))
        if (headerReader.anyText(info["albumId"]) != id) throw XmlyException("id_mismatch")
        return AlbumDetail(
            album = headerReader.albumOf(info),
            stats = AlbumStats(
                playCount = headerReader.count(info["playTimes"]),
                finished = headerReader.integer(info["isFinished"]) == 2L,
            ),
            paid = headerReader.boolean(info["isPaid"]) == true,
            anchorVerified = headerReader.boolean(info["isVerified"]) == true,
            tags = headerReader.anyText(info["tags"]).split(',').map { it.trim() }.filter { it.isNotEmpty() },
            recordDescending = headerReader.boolean(info["isRecordDesc"]) == true,
        )
    }

    /**
     * The full introduction as the server's HTML. With an account the signed-in route is used, as the original app does;
     * an expired login is not retried anonymously here, so a failure is reported rather than quietly downgraded.
     */
    suspend fun introduction(albumId: String, credentials: Credentials?): String {
        val id = xmlyIdentifier(albumId)
        val rich = if (credentials != null) {
            val query = mapOf("albumId" to id, "identity" to "podcast", "supportWebp" to "true")
            val data = introReader.obj(http.call(Endpoints.AlbumIntroductionAccount, credentials, query = query)["data"])
            if (introReader.anyText(introReader.obj(data["baseAlbum"])["albumId"]) != id) throw XmlyException("id_mismatch")
            stringOf(introReader.obj(data["intro"])["richIntro"])
        } else {
            val query = mapOf("albumId" to id, "supportWebp" to "true")
            val data = introReader.obj(http.call(Endpoints.AlbumIntroduction, null, System.currentTimeMillis(), query = query)["data"])
            val base = introReader.obj(data["album"])
            if (introReader.anyText(base["albumId"]) != id) throw XmlyException("id_mismatch")
            val detail = if (present(data["detail"])) introReader.obj(data["detail"]) else JsonObject(emptyMap())
            if (present(detail["albumId"]) && introReader.anyText(detail["albumId"]) != id) throw XmlyException("id_mismatch")
            stringOf(detail["introRich"]) ?: stringOf(base["introRich"])
        }
        return rich ?: introReader.invalid()
    }

    /**
     * One page of 200 episodes with their catalogue positions. [ascending] is the episode-number order; the server's own
     * order is [recordDescending], so the request asks for the other order when the two differ.
     */
    suspend fun episodePage(albumId: String, page: Int, ascending: Boolean, recordDescending: Boolean, credentials: Credentials?): Page<Track> {
        val id = xmlyIdentifier(albumId)
        if (page !in 1..MaxPage) throw XmlyException("invalid_page", definitive = true)
        return Episodes(id, ascending, recordDescending, credentials).page(page)
    }

    /** One page of episodes in this album whose titles match [keyword], in the server's relevance order. */
    suspend fun searchEpisodes(albumId: String, keyword: String, page: Int = 1, pageSize: Int = 10): Page<Track> {
        val id = xmlyIdentifier(albumId)
        val query = keyword.dartTrim()
        if (query.isEmpty() || keyword.length > 200) throw XmlyException("invalid_search", definitive = true)
        if (page !in 1..MaxPage || pageSize !in 1..50) throw XmlyException("invalid_page", definitive = true)
        val response = http.call(Endpoints.AlbumSearch, null, body = buildJsonObject {
            put("core", "track")
            put("condition", "relation")
            put("paidFilter", "false")
            put("recall", "title")
            put("page", "$page")
            put("rows", "$pageSize")
            put("fq", "albumId:$id")
            put("kw", encodeComponent(query))
            put("device", "android")
        })
        val result = searchReader.obj(response["response"])
        val total = searchReader.countInt(result["numFound"]) ?: searchReader.invalid()
        if (searchReader.countInt(result["start"]) != (page - 1) * pageSize) searchReader.invalid()
        val tracks = searchReader.rows(result["docs"], pageSize).map { doc ->
            for (key in listOf("album_id", "albumId")) {
                if (present(doc[key]) && searchReader.anyText(doc[key]) != id) throw XmlyException("id_mismatch")
            }
            searchReader.trackOf(doc)
        }
        return Page(tracks.distinctBy { it.id }, page, pageSize, total)
    }

    /** Other albums by the same anchor, and albums listeners also play. */
    suspend fun similar(albumId: String): AlbumSimilar {
        val id = xmlyIdentifier(albumId)
        val data = similarReader.obj(http.call(Endpoints.AlbumSimilar, null, System.currentTimeMillis(), query = mapOf("albumId" to id))["data"])
        val anchor = similarReader.optionalObj(data["anchorOtherAlbums"])
        val recommended = similarReader.optionalObj(data["recommendAlbumResult"])
        if (recommended.isNotEmpty() && similarReader.integer(recommended["ret"]) != 0L) throw XmlyException("invalid_similar")
        fun albums(value: JsonElement?): List<Album> {
            if (!present(value)) return emptyList()
            val rows = if (value is JsonObject) value["list"] else value
            if (!present(rows)) return emptyList()
            val seen = hashSetOf(id)
            // Promoted cards carry an ad marker; they are not albums of this list.
            return similarReader.rows(rows, MaxSimilar)
                .filter { it["adInfo"] == null }
                .map { similarReader.albumOf(it) }
                .filter { seen.add(it.id) }
        }
        return AlbumSimilar(
            anchorAlbums = albums(anchor["albums"]),
            anchorMore = similarReader.boolean(anchor["isShowMoreButton"]) == true,
            recommendations = albums(recommended["albums"]),
        )
    }

    /** One page of reviews, 20 at a time, ordered by score or by time. Page one carries the album's score summary. */
    suspend fun reviews(albumId: String, page: Int = 1, newest: Boolean = false): AlbumReviews {
        val id = xmlyIdentifier(albumId)
        if (page !in 1..MaxPage) reviewReader.invalid()
        val query = mapOf(
            "albumId" to id, "pageId" to "$page", "pageSize" to "$ReviewPageSize",
            "order" to if (newest) "time-desc" else "content-score-desc",
        )
        val data = reviewReader.obj(http.call(Endpoints.AlbumReviews, null, System.currentTimeMillis(), query = query)["data"])
        val pagination = reviewReader.obj(data["comments"])
        val lastPage = checkedPagination(pagination, page, ReviewPageSize)
        val items = mutableListOf<AlbumReview>()
        val seen = HashSet<String>()
        fun add(row: JsonObject) {
            val review = review(row)
            if (seen.add(review.id)) items += review
        }
        if (page == 1 && present(data["ToppedComment"])) add(reviewReader.obj(data["ToppedComment"]))
        for (row in reviewReader.rows(pagination["list"], ReviewPageSize)) add(row)
        return AlbumReviews(score(data), items, page, hasMore = page < lastPage)
    }

    /** One page of replies under the review [commentId]. */
    suspend fun replies(albumId: String, commentId: String, page: Int = 1): AlbumReplies {
        val id = xmlyIdentifier(albumId)
        val root = xmlyIdentifier(commentId)
        if (page !in 1..MaxPage) reviewReader.invalid()
        val query = mapOf("albumId" to id, "commentId" to root, "pageId" to "$page", "pageSize" to "$ReviewPageSize")
        val data = reviewReader.obj(http.call(Endpoints.AlbumReviewReplies, null, System.currentTimeMillis(), query = query)["data"])
        if (present(data["RootComment"]) && review(reviewReader.obj(data["RootComment"])).id != root) reviewReader.invalid()
        val pagination = reviewReader.obj(data["commentReplys"])
        val lastPage = checkedPagination(pagination, page, ReviewPageSize)
        val items = mutableListOf<AlbumReply>()
        val seen = HashSet<String>()
        for (row in reviewReader.rows(pagination["list"], ReviewPageSize)) {
            val reply = reply(row, root)
            if (seen.add(reply.id)) items += reply
        }
        return AlbumReplies(items, page, hasMore = page < lastPage)
    }

    /** One review. A reply reference without text is accepted and dropped, as the original app drops it. */
    private fun review(row: JsonObject): AlbumReview {
        val id = xmlyIdentifier(reviewReader.anyText(row["commentId"]))
        val content = stringOf(row["content"]) ?: reviewReader.invalid()
        val replies = row["replies"]
        if (present(replies) && replies !is JsonArray) reviewReader.invalid()
        var preview: AlbumReply? = null
        val first = (replies as? JsonArray)?.firstOrNull()
        if (first != null) {
            val reply = first as? JsonObject ?: reviewReader.invalid()
            if (reply.size == 2 && reply.containsKey("replyId") && reviewReader.boolean(reply["unFollowAnchor"]) != null) {
                xmlyIdentifier(reviewReader.anyText(reply["replyId"]))
            } else {
                preview = reply(reply, id)
            }
        }
        return AlbumReview(
            id = id,
            uid = reviewUid(row["uid"]),
            author = reviewReader.text(row["nickname"]),
            content = content,
            createdAtMillis = timestamp(row["createdAt"]),
            replyCount = reviewReader.countInt(if (present(row["replyCount"])) row["replyCount"] else JsonPrimitive(0))
                ?: reviewReader.invalid(),
            likes = row["likes"]?.takeIf { present(it) }?.let { reviewReader.count(it) },
            avatarUrl = avatar(row["smallHeader"]),
            highQuality = reviewReader.boolean(row["isHighQuality"]) == true,
            preview = preview,
        )
    }

    private fun reply(row: JsonObject, rootId: String): AlbumReply {
        if (xmlyIdentifier(reviewReader.anyText(row["commentId"])) != rootId) reviewReader.invalid()
        return AlbumReply(
            id = xmlyIdentifier(reviewReader.anyText(row["replyId"])),
            commentId = rootId,
            uid = reviewUid(row["uid"]),
            author = reviewReader.text(row["nickname"]),
            content = stringOf(row["content"]) ?: reviewReader.invalid(),
            createdAtMillis = timestamp(row["createdAt"]),
            avatarUrl = avatar(row["smallHeader"]),
            parentNickname = reviewReader.text(row["parentReplyNickName"]),
            likes = row["likes"]?.takeIf { present(it) }?.let { reviewReader.count(it) },
        )
    }

    private fun score(data: JsonObject): AlbumScore {
        val summary = reviewReader.optionalObj(data["recScoreSummary"])
        val diagram = reviewReader.optionalObj(summary["diagram"])
        val albumScore = data["albumScore"]?.takeIf { present(it) }?.let { value ->
            reviewReader.score(value)?.takeIf { it >= 0 } ?: reviewReader.invalid()
        }
        return AlbumScore(
            albumScore = albumScore,
            ratingCount = reviewReader.countInt(data["albumValidScoreNum"]),
            commentCount = reviewReader.countInt(data["allCommentsCount"]),
            recommendationScore = ratio(summary["score"]),
            recommendation = reviewReader.text(summary["label"]),
            recommendedCount = reviewReader.countInt(summary["totalNum"]),
            recommended = ratio(diagram["1"]),
            neutral = ratio(diagram["2"]),
            notRecommended = ratio(diagram["3"]),
        )
    }

    /** The server's page numbers, checked against the request. Returns the last page number. */
    private fun checkedPagination(pagination: JsonObject, page: Int, requested: Int): Int {
        val pageId = reviewReader.countInt(pagination["pageId"]) ?: reviewReader.invalid()
        val size = reviewReader.countInt(pagination["pageSize"]) ?: reviewReader.invalid()
        val pages = reviewReader.countInt(pagination["maxPageId"]) ?: reviewReader.invalid()
        reviewReader.countInt(pagination["totalCount"]) ?: reviewReader.invalid()
        val list = pagination["list"] as? JsonArray ?: reviewReader.invalid()
        if (pageId != page || size !in 1..requested || list.size > size || (pages > 0 && page > pages && list.isNotEmpty())) {
            reviewReader.invalid()
        }
        return pages
    }

    private fun reviewUid(value: JsonElement?): String {
        val text = reviewReader.anyText(value)
        return if (!present(value) || text.isEmpty() || text == "0") "" else xmlyIdentifier(text)
    }

    private fun timestamp(value: JsonElement?): Long {
        val millis = reviewReader.integer(value) ?: reviewReader.invalid()
        if (millis < 0 || millis > MaxTimestamp) reviewReader.invalid()
        return millis
    }

    private fun ratio(value: JsonElement?): Double? {
        if (!present(value)) return null
        return reviewReader.score(value)?.takeIf { it in 0.0..1.0 } ?: reviewReader.invalid()
    }

    /** An avatar, accepted only from the image host over https; anything else is dropped. */
    private fun avatar(value: JsonElement?): String? {
        val text = stringOf(value) ?: return null
        if (text.length > 16384) return null
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val accepted = (scheme == "http" || scheme == "https") && uri.userInfo == null && uri.rawFragment == null &&
            uri.port == -1 && (host == "xmcdn.com" || host.endsWith(".xmcdn.com"))
        return if (accepted) "https" + text.substring(scheme.length) else null
    }

    /** The episodes of one album, read the way the original app reads them: a short page is filled in from smaller pages. */
    private inner class Episodes(
        private val albumId: String,
        private val ascending: Boolean,
        private val recordDescending: Boolean,
        credentials: Credentials?,
    ) {
        private var account = credentials

        suspend fun page(page: Int): Page<Track> {
            val data = checked(fetch(page, EpisodePageSize), page, EpisodePageSize)
            val total = catalogueReader.countInt(data["totalCount"]) ?: catalogueReader.invalid()
            val rows = resolve(data, page, EpisodePageSize)
            return Page(rows.map { (row, position) -> catalogueReader.trackOf(row, position = position) }, page, EpisodePageSize, total)
        }

        /** One request. An expired login is retried once without the account, and the rest of this album is read anonymously. */
        private suspend fun fetch(page: Int, size: Int): JsonObject {
            val query = mapOf(
                "albumId" to albumId, "pageId" to "$page", "pageSize" to "$size",
                "isAsc" to "${ascending != recordDescending}", "device" to "android",
            )
            return try {
                http.call(Endpoints.AlbumTracks, account, System.currentTimeMillis(), query = query)
            } catch (failure: XmlyException) {
                if (account == null || failure.code != "login_expired") throw failure
                account = null
                http.call(Endpoints.AlbumTracks, null, System.currentTimeMillis(), query = query)
            }
        }

        /** The page's own numbers and rows, checked before any row is used. */
        private fun checked(response: JsonObject, page: Int, size: Int): JsonObject {
            val data = catalogueReader.obj(response["data"])
            val total = catalogueReader.countInt(data["totalCount"]) ?: catalogueReader.invalid()
            val pages = catalogueReader.countInt(data["maxPageId"]) ?: catalogueReader.invalid()
            if (catalogueReader.countInt(data["pageId"]) != page || catalogueReader.countInt(data["pageSize"]) != size ||
                (if (total > 0) pages != (total + size - 1) / size else pages > 1)
            ) {
                catalogueReader.invalid()
            }
            if (present(response["album"]) && catalogueReader.anyText(catalogueReader.obj(response["album"])["albumId"]).let { it.isNotEmpty() && it != albumId }) {
                throw XmlyException("id_mismatch")
            }
            val rows = catalogueReader.rows(data["list"], size)
            if (rows.size > (total - (page - 1) * size).coerceIn(0, size)) catalogueReader.invalid()
            val ids = HashSet<String>()
            for (row in rows) {
                for (key in listOf("albumId", "album_id")) {
                    if (present(row[key]) && catalogueReader.anyText(row[key]) != albumId) throw XmlyException("id_mismatch")
                }
                if (!ids.add(catalogueReader.id(row["trackId"] ?: row["id"]))) throw XmlyException("duplicate_page")
            }
            return data
        }

        /** The rows of [data] with their positions. A page with missing rows is read again from smaller pages. */
        private suspend fun resolve(data: JsonObject, page: Int, size: Int): List<Pair<JsonObject, Int>> {
            val total = catalogueReader.countInt(data["totalCount"]) ?: catalogueReader.invalid()
            val first = (page - 1) * size + 1
            val last = minOf(page * size, total)
            val values = catalogueReader.rows(data["list"], size)
            if (values.size == maxOf(0, last - first + 1)) {
                return values.mapIndexed { index, row -> row to if (ascending) first + index else total - first - index + 1 }
            }
            // A single-row page that is missing proves an unavailable slot; nothing smaller can fill it.
            if (size == 1) return emptyList()
            var childSize = size / 2
            while (size % childSize != 0) childSize--
            val resolved = mutableListOf<Pair<JsonObject, Int>>()
            for (offset in first..last step childSize) {
                val childPage = (offset - 1) / childSize + 1
                val child = checked(fetch(childPage, childSize), childPage, childSize)
                if (catalogueReader.countInt(child["totalCount"]) != total) throw XmlyException("catalogue_changed")
                for (pair in resolve(child, childPage, childSize)) {
                    if (resolved.size >= values.size) throw XmlyException("catalogue_changed")
                    val original = values[resolved.size]
                    if (catalogueReader.id(pair.first["trackId"] ?: pair.first["id"]) !=
                        catalogueReader.id(original["trackId"] ?: original["id"])
                    ) {
                        throw XmlyException("catalogue_changed")
                    }
                    resolved += pair
                }
            }
            if (resolved.size != values.size) throw XmlyException("catalogue_changed")
            return resolved
        }
    }

    private companion object {
        const val MaxPage = 10000
        const val MaxSimilar = 100
        const val EpisodePageSize = AlbumEpisodePageSize
        const val ReviewPageSize = 20
        const val MaxTimestamp = 8_640_000_000_000_000L
    }
}

/** Episodes per page of [XmlyAlbum.episodePage]. Episode number n is on page (n - 1) / 200 + 1. */
const val AlbumEpisodePageSize = 200

private fun present(value: JsonElement?): Boolean = value != null && value !is JsonNull

private fun stringOf(value: JsonElement?): String? = (value as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun Reader.optionalObj(value: JsonElement?): JsonObject = if (present(value)) obj(value) else JsonObject(emptyMap())

/** Dart's Uri.encodeComponent: letters, digits and -_.!~*'() stay; every other byte of the UTF-8 is escaped. */
private fun encodeComponent(text: String): String {
    val safe = "-_.!~*'()"
    val out = StringBuilder()
    for (byte in text.toByteArray(Charsets.UTF_8)) {
        val c = (byte.toInt() and 0xff).toChar()
        if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || safe.indexOf(c) >= 0) out.append(c) else out.append("%%%02X".format(byte.toInt() and 0xff))
    }
    return out.toString()
}