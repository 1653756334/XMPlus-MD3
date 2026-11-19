package com.xuleo.xmplus.md3.core.protocol

import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.model.dartTrim
import com.xuleo.xmplus.md3.core.model.xmlyIdentifier
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * One comment on a track. [likes] and [liked] are the server's totals, with [liked] the account's own state; [uid] is
 * empty when the server does not name the author, and only the author may delete a comment.
 */
data class TrackComment(
    val id: String,
    val uid: String,
    val author: String,
    val content: String,
    val createdAtMillis: Long?,
    val likes: Long,
    val liked: Boolean,
    val replyCount: Int,
    val avatarUrl: String?,
)

/** One page of a track's comments; [hasMore] follows the server's last page number. */
data class TrackCommentPage(val items: List<TrackComment>, val page: Int, val hasMore: Boolean, val total: Int)

/**
 * A track's comments. Reading works without an account; posting, liking and deleting need one. A write returns only once
 * the server has confirmed it, so the caller can show the new state without guessing.
 */
class XmlyComments(private val http: XmlyHttp, private val clock: () -> Long = System::currentTimeMillis) {
    private val reader = Reader("invalid_comments")

    suspend fun load(trackId: String, page: Int, credentials: Credentials?): TrackCommentPage {
        if (page !in 1..MaxPage) throw XmlyException("comment_limit", definitive = true)
        val number = xmlyIdentifier(trackId)
        val response = http.call(
            Endpoints.TrackComments, credentials, timestamp = clock(),
            query = mapOf(
                "trackId" to number, "pageId" to "$page", "pageSize" to "$PageSize", "hotPageId" to "1",
                "hotPageSize" to "0", "order" to "0", "showVersion" to "1", "source" to "2",
            ),
        )
        val data = reader.obj(response["comment"])
        if (reader.count(data["pageId"]) != page.toLong()) reader.invalid()
        val total = reader.count(data["totalCount"]) ?: reader.invalid()
        val pages = reader.count(data["maxPageId"]) ?: reader.invalid()
        val ids = HashSet<String>()
        val items = reader.rows(data["list"], PageSize).map { row ->
            val id = reader.id(row["id"])
            if (!ids.add(id)) reader.invalid()
            if (present(row["trackId"]) && reader.id(row["trackId"]) != number) reader.invalid()
            val content = (row["content"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: reader.invalid()
            val created = reader.count(row["createdAt"])
            if (created != null && created > MaxDartMillis) reader.invalid()
            TrackComment(
                id = id,
                uid = if (present(row["uid"])) reader.id(row["uid"]) else "",
                author = reader.text(row["nickname"]),
                content = content,
                createdAtMillis = created,
                likes = if (present(row["likes"])) reader.count(row["likes"]) ?: reader.invalid() else 0L,
                liked = reader.boolean(row["liked"]) == true || reader.integer(row["likeStatus"]) == 1L,
                replyCount = if (present(row["replyCount"])) reader.countInt(row["replyCount"]) ?: reader.invalid() else 0,
                avatarUrl = reader.image(row["smallHeader"]),
            )
        }
        return TrackCommentPage(items, page, page < pages, total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    /** Posts a comment. Blank text is refused before anything is sent. */
    suspend fun create(credentials: Credentials, trackId: String, content: String) {
        val text = content.dartTrim()
        if (text.isEmpty()) throw XmlyException("empty_comment", definitive = true)
        http.call(
            Endpoints.CommentCreate, credentials,
            form = mapOf(
                "uid" to credentials.uid, "token" to credentials.token.substringAfter('&'), "setTop" to "false",
                "topExpireTime" to "0", "trackId" to xmlyIdentifier(trackId), "startTime" to "0", "synchaos" to "0",
                "endTime" to "0", "content" to text,
            ),
        )
    }

    suspend fun setLiked(credentials: Credentials, trackId: String, comment: TrackComment, liked: Boolean) {
        http.call(
            Endpoints.CommentLike, credentials,
            form = mapOf(
                "uid" to credentials.uid, "islike" to "$liked", "trackId" to xmlyIdentifier(trackId),
                "commentId" to xmlyIdentifier(comment.id), "device" to "android",
            ),
        )
    }

    /** Only the account's own comments can be deleted; anything else is refused before a request is sent. */
    suspend fun delete(credentials: Credentials, trackId: String, comment: TrackComment) {
        if (comment.uid.isEmpty() || comment.uid != credentials.uid) throw XmlyException("permission_denied", definitive = true)
        http.call(
            Endpoints.CommentDelete, credentials, timestamp = clock(),
            form = mapOf("trackId" to xmlyIdentifier(trackId), "commentId" to xmlyIdentifier(comment.id), "device" to "android"),
        )
    }

    private fun present(value: JsonElement?): Boolean = value != null && value != JsonNull

    private companion object {
        const val PageSize = 20
        const val MaxPage = 250
        const val MaxDartMillis = 8_640_000_000_000_000L
    }
}