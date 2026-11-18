package com.xuleo.xmplus.md3.core.protocol

import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.AlbumListing
import com.xuleo.xmplus.md3.core.model.AlbumSection
import com.xuleo.xmplus.md3.core.model.AlbumStats
import com.xuleo.xmplus.md3.core.model.BrowseCategory
import com.xuleo.xmplus.md3.core.model.HotRanking
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.model.dartTrim
import com.xuleo.xmplus.md3.core.model.xmlyIdentifier
import kotlinx.serialization.json.JsonObject

/** The website's category list, its paged 全部专辑 feed, and the 热播榜 charts. All public. */
class XmlyBrowse(private val http: XmlyHttp) {
    private val browse = Reader("invalid_browse")
    private val ranking = Reader("invalid_ranking")

    /** The top-level categories, in the server's order. */
    suspend fun categories(): List<BrowseCategory> {
        val data = http.call(Endpoints.AllCategories, null)["data"] as? JsonObject
        val seen = mutableSetOf<Int>()
        val result = mutableListOf<BrowseCategory>()
        for (row in browse.rows(data?.get("categories"), 500)) {
            if (browse.boolean(row["isDisplay"]) == false || (browse.count(row["parentId"]) ?: 0) != 0L) continue
            val id = browse.countInt(row["id"])
            val title = browse.text(row["title"]).dartTrim()
            if (id == null || id <= 0 || title.isEmpty() || !seen.add(id)) continue
            result += BrowseCategory(id, title)
        }
        if (result.isEmpty()) browse.invalid()
        return result
    }

    /** [sort] is 1 综合, 2 最近更新 or 3 最多播放; category 0 means all. */
    suspend fun albums(categoryId: Int = 0, sort: Int = 1, page: Int = 1): AlbumListing {
        if (categoryId < 0 || page !in 1..10000 || sort !in 1..3) throw XmlyException("invalid_page", definitive = true)
        val query = buildMap {
            if (categoryId > 0) put("categoryId", "$categoryId")
            put("pageNum", "$page")
            put("pageSize", "$PageSize")
            put("sort", "$sort")
        }
        val data = browse.obj(http.call(Endpoints.CategoryAlbums, null, query = query)["data"])
        val rows = browse.rows(data["albums"], 500)
        val total = browse.count(data["total"])
        val size = browse.count(data["pageSize"])
        if (browse.count(data["pageNum"]) != page.toLong() || size == null || total == null ||
            (size == 0L && total != 0L) || rows.size > size || rows.size > total
        ) browse.invalid()
        val albums = mutableListOf<Album>()
        val stats = mutableMapOf<String, AlbumStats>()
        for (row in rows) {
            if (browse.boolean(row["isDeleted"]) == true) continue
            val id = xmlyIdentifier(browse.anyText(row["albumId"]))
            if (stats.containsKey(id)) continue
            albums += Album(
                id = id,
                title = browse.text(row["albumTitle"]),
                artist = browse.text(row["albumUserNickName"]),
                intro = browse.text(row["intro"]),
                coverUrl = browse.image(row["albumCoverPath"]),
                trackCount = browse.countInt(row["albumTrackCount"]) ?: 0,
            )
            stats[id] = AlbumStats(playCount = browse.count(row["albumPlayCount"]), finished = browse.integer(row["isFinished"]) == 2L)
        }
        val hasMore = total > 0 && page.toLong() * size < total
        if (rows.isEmpty() && hasMore) browse.invalid()
        return AlbumListing(albums, stats, page, total.toInt(), hasMore)
    }

    /** One complete chart, never a pageable feed. [categoryId] 0 is the whole site; an unknown category falls back to it. */
    suspend fun hotRanking(categoryId: Int = 0): HotRanking {
        if (categoryId < 0) ranking.invalid()
        val clusters = ranking.obj(http.call(Endpoints.HotRankClusters, null)["data"])
        val group = ranking.rows(clusters["clusterType"], 500)
            .firstOrNull { it["rankClusterTypeCode"]?.let(ranking::text) == "hot" && ranking.count(it["rankType"]) == 2L } ?: ranking.invalid()
        val typeId = ranking.count(group["rankClusterTypeId"])
        val categories = ranking.rows(group["rankClusterCategories"], 500)
        val selected = categories.firstOrNull { ranking.count(it["categoryId"]) == categoryId.toLong() }
            ?: categories.firstOrNull { ranking.count(it["categoryId"]) == 0L }
        if (selected == null || typeId == null || typeId <= 0) ranking.invalid()
        val clusterId = ranking.count(selected["rankClusterId"])
        val rankId = ranking.count(selected["rankId"])
        if (clusterId == null || clusterId <= 0 || rankId == null || rankId <= 0) ranking.invalid()
        val data = ranking.obj(
            http.call(Endpoints.HotRankElements, null, query = mapOf("typeId" to "$typeId", "clusterId" to "$clusterId"))["data"],
        )
        if (ranking.count(data["typeId"]) != typeId || ranking.count(data["clusterId"]) != clusterId) ranking.invalid()
        val rank = ranking.rows(data["rankList"], 500).firstOrNull { ranking.count(it["rankId"]) == rankId } ?: ranking.invalid()
        val title = ranking.text(rank["title"]).dartTrim()
        val rows = ranking.rows(rank["albums"], 500)
        // A smaller response is not a complete chart: never invent pages or label a truncated preview as the full one.
        if (title.isEmpty() || ranking.count(rank["count"]) != rows.size.toLong()) ranking.invalid()
        val albums = mutableListOf<Album>()
        val stats = mutableMapOf<String, AlbumStats>()
        for (row in rows) {
            val id = xmlyIdentifier(ranking.anyText(row["id"]))
            if (stats.containsKey(id)) ranking.invalid()
            albums += Album(
                id = id,
                title = ranking.text(row["albumTitle"]),
                artist = ranking.text(row["anchorName"]),
                intro = ranking.text(row["description"]),
                coverUrl = ranking.image(row["cover"]),
                trackCount = ranking.countInt(row["trackCount"]) ?: 0,
            )
            stats[id] = AlbumStats(playCount = ranking.count(row["playCount"]))
        }
        return HotRanking(AlbumSection(title, albums, stats, ranking = true), wholeSite = ranking.count(selected["categoryId"]) == 0L)
    }

    private companion object {
        const val PageSize = 20
    }
}
