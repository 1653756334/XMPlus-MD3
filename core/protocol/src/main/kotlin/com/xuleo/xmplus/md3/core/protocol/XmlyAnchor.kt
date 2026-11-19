package com.xuleo.xmplus.md3.core.protocol

import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.AnchorAlbum
import com.xuleo.xmplus.md3.core.model.AnchorContentPage
import com.xuleo.xmplus.md3.core.model.AnchorProfile
import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.Track
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.model.dartTrim
import com.xuleo.xmplus.md3.core.model.xmlyIdentifier
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Profile and public works. The mobile profile and albums are used only with an account; tracks are always anonymous. */
class XmlyAnchor(private val http: XmlyHttp) {
    private val reader = Reader("invalid_anchor")

    suspend fun albumAnchor(albumId: String): String {
        val id = xmlyIdentifier(albumId)
        val response = data(Endpoints.AnchorAlbumInfo, mapOf("albumId" to id))
        if (reader.anyText(response["albumId"]) != id) reader.invalid()
        return reader.id(optionalObject(response["albumPageMainInfo"])["anchorUid"])
    }

    suspend fun profile(uid: String, credentials: Credentials?): AnchorProfile {
        val id = xmlyIdentifier(uid)
        if (credentials == null) {
            val row = data(Endpoints.AnchorProfile, mapOf("uid" to id))
            if (reader.anyText(row["uid"]) != id) reader.invalid()
            return AnchorProfile(
                uid = id,
                nickname = requiredText(row["nickName"]),
                avatarUrl = image(row["cover"]),
                backgroundUrl = image(row["background"]),
                intro = cleanText(row["description"]?.takeIf(::present)
                    ?: row["personalSignature"]?.takeIf(::present) ?: row["intro"]),
                verification = cleanText(row["personalDescription"]),
                verified = (count(row["logoType"]) ?: 0) > 0,
                level = reader.positiveOrNull(row["mvpGrade"]),
                location = location(row),
                fans = countInt(row["fansCount"]),
                following = countInt(row["followingCount"]),
                albums = countInt(row["albumsCount"]),
                tracks = countInt(row["tracksCount"]),
            )
        }
        val response = data(Endpoints.AnchorProfileAccount, mapOf("toUid" to id), credentials)
        val cards = response["cards"] as? JsonArray ?: reader.invalid()
        val module = cards.asSequence().map(::optionalObject)
            .firstOrNull { reader.text(it["cardType"]) == "ArtistPageAnchorInfoModule" } ?: reader.invalid()
        val row = reader.rows(module["body"], 1).singleOrNull() ?: reader.invalid()
        val ext = optionalObject(row["anchorExt"])
        if (reader.anyText(row["id"]) != id || reader.anyText(ext["anchorUid"]) != id) reader.invalid()
        val counts = optionalObject(row["count"])
        val verify = optionalObject(ext["verify"])
        return AnchorProfile(
            uid = id,
            nickname = requiredText(row["title"]),
            avatarUrl = image(row["cover"], mobile = true),
            intro = cleanText(row["summary"]),
            verification = cleanText(verify["ptitle"]),
            verified = reader.boolean(verify["isVerified"]) == true,
            level = reader.positiveOrNull(ext["anchorGrade"]),
            location = location(ext),
            fans = countInt(counts["anchorFansCount"]),
            following = countInt(counts["anchorFolloweeCount"]),
            albums = countInt(counts["anchorAlbumCount"]),
            tracks = countInt(counts["anchorTrackCount"]),
        )
    }

    suspend fun albums(
        uid: String, credentials: Credentials?, page: Int = 1, pageSize: Int = 10,
    ): AnchorContentPage<AnchorAlbum> {
        checkPage(page, pageSize)
        val id = xmlyIdentifier(uid)
        return if (credentials != null) mobileAlbums(id, page, credentials) else publicAlbums(id, page, pageSize)
    }

    private suspend fun mobileAlbums(id: String, page: Int, credentials: Credentials): AnchorContentPage<AnchorAlbum> {
        val response = data(Endpoints.AnchorAlbumsAccount, mapOf(
            "toUid" to id, "pageId" to "$page", "orderBy" to "0", "finishStatus" to "0", "paidType" to "0",
        ), credentials)
        val stride = 12
        if (countInt(response["offset"]) != page * stride) repeatedPage()
        val card = reader.obj(response["card"])
        if (reader.text(card["cardType"]) != "ArtistPageAllAlbumListModule") reader.invalid()
        val body = card["body"]
        val more = reader.boolean(card["hasMore"])
        val header = optionalObject(card["header"])
        val total = countInt(optionalObject(header["count"])["anchorAlbumCount"])
        val items = if (!present(body)) emptyList() else {
            if (more == null || total == null) reader.invalid()
            reader.rows(body, stride).map { row ->
                val albumId = reader.id(row["id"])
                if (reader.anyText(optionalObject(row["albumExt"])["albumId"]) != albumId) reader.invalid()
                val counts = optionalObject(row["count"])
                AnchorAlbum(
                    album = Album(
                        albumId, requiredText(row["title"]), intro = cleanText(row["summary"]),
                        coverUrl = image(row["cover"], mobile = true), anchorUid = id,
                        trackCount = countInt(counts["albumTrackCount"]) ?: 0,
                    ),
                    playCount = count(counts["albumPlayTimesCount"]),
                )
            }
        }
        // The final partial page may still claim there is more.
        return AnchorContentPage(items, page, stride, more == true && (total == null || page * stride < total), total)
    }

    private suspend fun publicAlbums(id: String, page: Int, requestedSize: Int): AnchorContentPage<AnchorAlbum> {
        val desktop = requestedSize == 10
        val endpoint = if (desktop) Endpoints.AnchorAlbumsDesktop else Endpoints.AnchorAlbumsWeb
        val row = data(endpoint, mapOf("uid" to id, "page" to "$page", "pageSize" to "$requestedSize", "orderType" to "2"))
        if (present(row["uid"]) && reader.anyText(row["uid"]) != id) reader.invalid()
        if ((desktop || present(row["page"])) && countInt(row["page"]) != page) repeatedPage()
        val albums = reader.rows(row["albumList"], 100)
        val totalCount = countInt(row["filterTotalCount"]) ?: countInt(row["totalCount"])
        val maximum = countInt(row["maxCount"])
        val total = if (maximum == null) totalCount else minOf(maximum, totalCount ?: maximum)
        val size = countInt(row["pageSize"])
        if ((desktop && (total == null || size == null)) ||
            (present(row["pageSize"]) && size == null) ||
            (size == 0 && (albums.isNotEmpty() || desktop && total != 0)) ||
            (size != null && (size > 100 || albums.size > size)) ||
            (total != null && albums.size > total)
        ) reader.invalid()
        // The desktop returns the true stride; the web endpoint shrinks pageSize without changing the offsets.
        val stride = if (desktop && size != null && size > 0) size else requestedSize
        val items = albums.map { album ->
            val albumId = reader.id(album["id"]?.takeIf(::present) ?: album["albumId"])
            val fallback = reader.albumOf(JsonObject(album + ("albumId" to JsonPrimitive(albumId))))
            AnchorAlbum(
                album = fallback.copy(
                    artist = cleanText(album["anchorNickName"]),
                    intro = cleanText(album["description"]),
                    coverUrl = fallback.coverUrl ?: image(album["coverPath"]),
                    trackCount = countInt(album["trackCount"]) ?: 0,
                ),
                playCount = count(album["playCount"]),
            )
        }
        return AnchorContentPage(items, page, stride, total?.let { page * stride < it } ?: (items.size >= stride), total)
    }

    suspend fun tracks(uid: String, page: Int = 1, pageSize: Int = 20): AnchorContentPage<Track> {
        checkPage(page, pageSize)
        val id = xmlyIdentifier(uid)
        val response = data(Endpoints.AnchorTracks, mapOf(
            "anchorId" to id, "page" to "$page", "pageSize" to "$pageSize", "asc" to "false",
        ))
        val returnedSize = countInt(response["pageSize"])
        if (countInt(response["page"]) != page || returnedSize == null || returnedSize > pageSize) repeatedPage()
        val rows = reader.rows(response["trackDetailInfos"], pageSize)
        val reported = count(response["totalCount"]) ?: reader.invalid()
        if (returnedSize != pageSize && returnedSize != rows.size) reader.invalid()
        val total = minOf(reported, TrackLimit.toLong()).toInt()
        val items = rows.map { row ->
            val info = optionalObject(row["trackInfo"])
            val trackId = reader.id(info["id"])
            val date = count(info["createdTime"])?.takeIf { it in 1..MaxDartDate } ?: reader.invalid()
            if (reader.anyText(row["id"]) != trackId || reader.anyText(info["anchorId"]) != id ||
                cleanText(info["title"]).isEmpty()
            ) reader.invalid()
            reader.trackOf(JsonObject(info + ("trackId" to JsonPrimitive(trackId)))).let { track ->
                track.copy(
                    coverUrl = track.coverUrl ?: image(info["cover"], mobile = true),
                    playCount = count(optionalObject(row["statCountInfo"])["playCount"]) ?: 0,
                    publishedAtMillis = date,
                )
            }
        }
        return AnchorContentPage(items, page, pageSize, page * pageSize < total, total, reported)
    }

    suspend fun followings(uid: String, page: Int): AnchorContentPage<AnchorProfile> {
        checkPage(page, FollowingPageSize)
        val id = xmlyIdentifier(uid)
        val body = data(Endpoints.AnchorFollowings, mapOf("keyWord" to "", "uid" to id, "page" to "$page", "pageSize" to "$FollowingPageSize"))
        if (body["uid"] != null && reader.anyText(body["uid"]) != id) reader.invalid()
        if (count(body["page"]) != page.toLong()) repeatedPage()
        val items = reader.rows(body["followingsPageInfo"], FollowingPageSize).map { row ->
            AnchorProfile(
                uid = reader.id(row["uid"]),
                nickname = cleanText(row["anchorNickName"]).ifEmpty { reader.invalid() },
                avatarUrl = reader.image(row["coverPath"]),
                intro = cleanText(row["description"]),
                verification = cleanText(row["ptitle"]),
                verified = (count(row["logoType"]) ?: 0L) > 0L,
                fans = count(row["followerCount"])?.toInt(),
                following = count(row["followingCount"])?.toInt(),
                albums = count(row["albumCount"])?.toInt(),
                tracks = count(row["trackCount"])?.toInt(),
            )
        }
        val total = count(body["filterTotalCount"]) ?: count(body["totalCount"])
        val hasMore = total?.let { page.toLong() * FollowingPageSize < it } ?: (items.size >= FollowingPageSize)
        return AnchorContentPage(
            items = items, page = page, pageSize = FollowingPageSize, hasMore = hasMore,
            total = total?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(), reportedTotal = total,
        )
    }

    /**
     * One page of this anchor's albums that match [keyword], from the site search with the anchor as its filter. Rows that
     * belong to another anchor are dropped, and paging stops, when the site ignores the filter.
     */
    suspend fun searchAlbums(uid: String, keyword: String, page: Int, pageSize: Int = 20): AnchorContentPage<AnchorAlbum> {
        val id = xmlyIdentifier(uid)
        val query = keyword.dartTrim()
        if (query.isEmpty() || keyword.length > 200) throw XmlyException("invalid_search", definitive = true)
        checkPage(page, pageSize)
        val response = http.call(
            Endpoints.Search, null,
            query = mapOf(
                "kw" to query, "core" to "album", "page" to "$page", "rows" to "$pageSize",
                "device" to "iPhone", "condition" to "relation", "spellchecker" to "true", "fq" to "uid:$id",
            ),
        )
        val values = optionalObject(optionalObject(optionalObject(response["data"])["result"])["response"])
        val docs = values["docs"] as? JsonArray ?: reader.invalid()
        val total = countInt(values["numFound"]) ?: reader.invalid()
        val rows = docs.map { it as? JsonObject ?: reader.invalid() }
        val items = rows.map { row -> AnchorAlbum(album = reader.albumOf(row), playCount = count(row["play"])) }
        val mine = items.filter { it.album.anchorUid == id }
        val trusted = mine.size == items.size
        return AnchorContentPage(mine, page, pageSize, trusted && page.toLong() * pageSize < total, total)
    }
    private suspend fun data(endpoint: Endpoint, query: Map<String, String>, credentials: Credentials? = null): JsonObject =
        reader.obj(http.call(endpoint, credentials, credentials?.let { System.currentTimeMillis() }, query = query)["data"])

    private fun checkPage(page: Int, size: Int) {
        if (page !in 1..MaxPage || size !in 1..100) throw XmlyException("invalid_page", definitive = true)
    }

    private fun repeatedPage(): Nothing = throw XmlyException("repeated_page")
    private fun present(value: JsonElement?): Boolean = value != null && value != JsonNull
    private fun optionalObject(value: JsonElement?): JsonObject = value as? JsonObject ?: EmptyObject
    private fun cleanText(value: JsonElement?): String = reader.text(value).dartTrim()
    private fun requiredText(value: JsonElement?): String = cleanText(value).ifEmpty { reader.invalid() }
    private fun location(row: JsonObject): String =
        listOf(cleanText(row["province"]), cleanText(row["city"])).filter(String::isNotEmpty).distinct().joinToString(" · ")

    private fun count(value: JsonElement?): Long? {
        if (value is JsonPrimitive && value.isString && value.content.any { it !in '0'..'9' }) return null
        return reader.count(value)
    }

    private fun countInt(value: JsonElement?): Int? = count(value)?.takeIf { it <= Int.MAX_VALUE }?.toInt()

    private fun image(value: JsonElement?, mobile: Boolean = false): String? {
        val url = cleanText(value).ifEmpty { return null }
        val absolute = url.startsWith("https://") || url.startsWith("http://") || url.startsWith("//")
        if (mobile && !absolute && (!ImagePath.containsMatchIn(url) ||
                url.any { it == '\\' || it == '?' || it == '#' } || ".." in url.split('/'))
        ) return null
        return reader.image(JsonPrimitive(url))
    }

    private companion object {
        const val FollowingPageSize = 10
        const val MaxPage = 10_000
        const val TrackLimit = 5_000
        val EmptyObject = JsonObject(emptyMap())
        val ImagePath = Regex("^(?:group[0-9]+|storages)/")
    }
}
