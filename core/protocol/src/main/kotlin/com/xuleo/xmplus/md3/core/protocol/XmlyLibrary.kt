package com.xuleo.xmplus.md3.core.protocol

import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.Track
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.model.xmlyIdentifier
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** One page of a membership list: the identities only, which is all a state check needs. */
data class LibraryIdsPage(val ids: List<String>, val hasMore: Boolean)

/** One page of 收藏 as tracks; [hasMore] follows the server's page count. */
data class FavoriteTracksPage(val tracks: List<Track>, val hasMore: Boolean)

/** An album the account subscribes to, with its newest episode's title and how many episodes are still unplayed. */
data class SubscribedAlbum(val album: Album, val latestTitle: String, val unreadCount: Long, val updatedAtMillis: Long)

/** One page of 订阅. */
data class SubscriptionsPage(val items: List<SubscribedAlbum>, val hasMore: Boolean)

/** A track that an album the account follows has published, and when it went up. */
data class UpdatedTrack(val track: Track, val publishedAtMillis: Long)

/** One page of the update feed. [nextBefore] is the cursor for the page after it; null once the feed has no cursor left. */
data class UpdatesPage(val items: List<UpdatedTrack>, val hasMore: Boolean, val nextBefore: Long?)

/**
 * 收藏, 订阅, 关注 and the update feed for the primary account. A mutation returns only once the server has
 * confirmed it. Membership is read one page at a time through [favoriteIdPage] and [subscriptionIdPage]; the caller
 * decides how far to read.
 */
class XmlyLibrary(private val http: XmlyHttp, private val clock: () -> Long = System::currentTimeMillis) {
    private val reader = Reader("invalid_library")

    /** 收藏 a track. The album is required because the server files the track under it. */
    suspend fun setFavorite(credentials: Credentials, trackId: String, albumId: String, favorite: Boolean) {
        http.call(
            if (favorite) Endpoints.FavoriteAdd else Endpoints.FavoriteRemove, credentials,
            form = mapOf("resourceId" to xmlyIdentifier(trackId), "albumId" to xmlyIdentifier(albumId), "resourceType" to "1"),
        )
    }

    /** One page of the account's 收藏 track IDs, and whether another page follows. */
    suspend fun favoriteIdPage(credentials: Credentials, page: Int): LibraryIdsPage = favoritePage(credentials, page)

    /** One page of the account's 订阅 album IDs, and whether another page follows. */
    suspend fun subscriptionIdPage(credentials: Credentials, page: Int): LibraryIdsPage {
        val result = subscriptions(credentials, page)
        return LibraryIdsPage(result.items.map { it.album.id }, result.hasMore)
    }

    suspend fun setSubscribed(credentials: Credentials, albumId: String, subscribed: Boolean) {
        http.call(
            if (subscribed) Endpoints.SubscribeAlbum else Endpoints.UnsubscribeAlbum, credentials,
            form = mapOf("albumId" to xmlyIdentifier(albumId)),
        )
    }

    /** The account's 关注 state for [uid], read from the anchor's mobile card. */
    suspend fun isFollowing(credentials: Credentials, uid: String): Boolean {
        val id = xmlyIdentifier(uid)
        val data = responseData(
            http.call(Endpoints.AnchorProfileAccount, credentials, timestamp = clock(), query = mapOf("toUid" to id)),
        )
        val cards = data["cards"] as? JsonArray ?: reader.invalid()
        val card = cards.firstOrNull { reader.text((it as? JsonObject)?.get("cardType")) == "ArtistPageAnchorInfoModule" }
            as? JsonObject ?: reader.invalid()
        val body = card["body"] as? JsonArray ?: reader.invalid()
        if (body.size != 1) reader.invalid()
        val row = body.single() as? JsonObject ?: reader.invalid()
        val anchorUid = (row["anchorExt"] as? JsonObject)?.get("anchorUid")
        if (reader.anyText(row["id"]) != id || reader.anyText(anchorUid) != id) reader.invalid()
        val interact = row["interact"] as? JsonObject ?: reader.invalid()
        return reader.boolean(interact["isFollowed"]) ?: reader.invalid()
    }

    /** 关注 or unfollow [uid]. The website sends this as a signed form; a JSON body is refused by the server. */
    suspend fun setFollowing(credentials: Credentials, uid: String, following: Boolean) {
        val id = xmlyIdentifier(uid)
        if (id == credentials.uid) throw XmlyException("invalid_anchor", definitive = true)
        http.call(
            Endpoints.FollowAnchor, credentials,
            form = mapOf("isFollow" to "$following", "subBizType" to "1", "toUid" to id, "bizType" to "11"),
        )
    }

    /** 收藏 as tracks, in the order the server lists them. */
    suspend fun favorites(credentials: Credentials, page: Int): FavoriteTracksPage {
        val (rows, more) = favoriteRows(credentials, page)
        return FavoriteTracksPage(rows.map { favoriteTrack(it) }, more)
    }

    /** One page of 订阅, with the newest episode and the unplayed count of each album. */
    suspend fun subscriptions(credentials: Credentials, page: Int): SubscriptionsPage {
        val data = responseData(
            http.call(
                Endpoints.SubscriptionList, credentials, timestamp = clock(),
                // sign=2 refreshes the first page even when a later page is asked for, as the website does.
                query = mapOf("pageId" to "$page", "sign" to if (page == 1) "2" else "1", "size" to "$PageSize"),
            ),
        )
        val rows = reader.rows(data["albumResults"], PageSize)
        val ids = HashSet<String>()
        val items = rows.map { row ->
            val album = reader.albumOf(row)
            if (album.id.isEmpty() || !ids.add(album.id)) reader.invalid()
            val unread = reader.count(row["unreadNum"]) ?: reader.invalid()
            SubscribedAlbum(album, reader.text(row["trackTitle"]), unread, reader.count(row["lastUpdateAt"]) ?: 0L)
        }
        val more = reader.boolean(data["hasMore"]) ?: reader.invalid()
        if (more && items.isEmpty()) reader.invalid()
        return SubscriptionsPage(items, more)
    }

    /** The update feed, newest first. Pass the previous page's [UpdatesPage.nextBefore] to read further back. */
    suspend fun updates(credentials: Credentials, before: Long? = null): UpdatesPage {
        if (before != null && (before <= 0 || before > MaxTimelineMs)) reader.invalid()
        val data = responseData(
            http.call(
                Endpoints.UpdateFeed, credentials, timestamp = clock(),
                query = buildMap {
                    put("sign", "2")
                    put("size", "$PageSize")
                    if (before != null) put("timeline", "$before")
                },
            ),
        )
        val rows = reader.rows(data["trackResults"], PageSize)
        val ids = HashSet<String>()
        val items = ArrayList<UpdatedTrack>(rows.size)
        var cursor: Long? = null
        for (row in rows) {
            val track = reader.trackOf(row)
            if (!ids.add(track.id)) throw XmlyException("repeated_page")
            val timeline = reader.count(row["timeline"])?.takeIf { it in 1L..MaxTimelineMs } ?: reader.invalid()
            val previous = cursor
            if (previous != null && timeline > previous) reader.invalid()
            cursor = timeline
            items += UpdatedTrack(track, reader.count(row["createdAt"]) ?: timeline)
        }
        val reached = cursor
        // The live feed can ignore the cursor and repeat its newest page; that is not an older page.
        if (before != null && reached != null && reached >= before) throw XmlyException("repeated_page")
        val more = reader.boolean(data["hasMore"]) ?: reader.invalid()
        return UpdatesPage(items, hasMore = more && items.isNotEmpty(), nextBefore = reached)
    }


    /** The rows of one page of 收藏, checked, and whether another page follows. */
    private suspend fun favoriteRows(credentials: Credentials, page: Int): Pair<List<JsonObject>, Boolean> {
        val data = responseData(
            http.call(Endpoints.FavoriteList, credentials, query = mapOf("pageId" to "$page", "pageSize" to "$PageSize")),
        )
        val rows = data["dataList"] as? JsonArray ?: reader.invalid()
        // An empty library comes back with zeroed pagination whatever page was asked for.
        if (page == 1 && rows.isEmpty() && data.integer("pageId") == 0L && data.integer("pageSize") == 0L &&
            data.integer("maxPageId") == 0L && data.integer("totalCount") == 0L
        ) return emptyList<JsonObject>() to false
        val maxPage = data.integer("maxPageId")?.toInt() ?: reader.invalid()
        if (data.integer("pageId") != page.toLong() || data.integer("pageSize") != PageSize.toLong()) reader.invalid()
        val ids = HashSet<String>()
        val accepted = ArrayList<JsonObject>(rows.size)
        for (raw in rows) {
            val row = raw as? JsonObject ?: reader.invalid()
            if (row.integer("resourceType") != 1L) reader.invalid()
            val track = favoriteTrackId(row)
            if (track.isEmpty() || reader.anyText(row["albumId"]).isEmpty()) reader.invalid()
            if (!ids.add(track)) throw XmlyException("repeated_page")
            accepted += row
        }
        val more = page < maxPage
        if ((more && accepted.isEmpty()) || (accepted.isNotEmpty() && page > maxPage)) reader.invalid()
        return accepted to more
    }

    private suspend fun favoritePage(credentials: Credentials, page: Int): LibraryIdsPage {
        val (rows, more) = favoriteRows(credentials, page)
        return LibraryIdsPage(rows.map { favoriteTrackId(it) }, more)
    }

    private fun favoriteTrackId(row: JsonObject): String =
        reader.anyText(row["trackId"]).ifEmpty { reader.anyText(row["id"]) }

    /** The lite row names its paid and authorized flags differently from the shared track parser. */
    private fun favoriteTrack(row: JsonObject): Track {
        val renamed = listOfNotNull(row["paid"]?.let { "isPaid" to it }, row["authorized"]?.let { "isAuthorized" to it })
        return reader.trackOf(JsonObject(row + renamed))
    }


    private fun responseData(response: JsonObject): JsonObject = response["data"] as? JsonObject ?: reader.invalid()

    private fun JsonObject.integer(name: String): Long? = reader.integer(this[name])

    private companion object {
        const val PageSize = 30
        const val MaxTimelineMs = 2_115_354_119_000L
    }
}
