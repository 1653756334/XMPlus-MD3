package com.xuleo.xmplus.md3.core.protocol

import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.AlbumStats
import com.xuleo.xmplus.md3.core.model.AnchorProfile
import com.xuleo.xmplus.md3.core.model.CategoryChannel
import com.xuleo.xmplus.md3.core.model.CategoryChannelInfo
import com.xuleo.xmplus.md3.core.model.CategoryDimension
import com.xuleo.xmplus.md3.core.model.CategoryGroup
import com.xuleo.xmplus.md3.core.model.CategoryValue
import com.xuleo.xmplus.md3.core.model.HotWord
import com.xuleo.xmplus.md3.core.model.Page
import com.xuleo.xmplus.md3.core.model.RankBlock
import com.xuleo.xmplus.md3.core.model.RankCategory
import com.xuleo.xmplus.md3.core.model.RankTab
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.model.xmlyIdentifier
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The desktop site's discovery API: category filters, ranking tabs and charts, and the hot words. All public. */
class XmlyDiscovery(private val http: XmlyHttp) {
    private val read = Reader("invalid_discovery")

    suspend fun categoryGroups(): List<CategoryGroup> {
        val data = data(Endpoints.CategoryGroups, mapOf("includeChannels" to "true"))
        return read.rows(data["groups"]).map { row ->
            CategoryGroup(read.positive(row["id"]), read.text(row["name"]), read.rows(row["channels"]).map(::channel))
        }
    }

    suspend fun categoryChannels(groupId: Int, page: Int = 1): Page<CategoryChannel> {
        positiveInput(groupId)
        pageInput(page)
        val data = data(Endpoints.CategoryGroupChannels, mapOf("groupId" to "$groupId", "pageId" to "$page"))
        return pageOf(data, "channels", "pageId", "totalCount", page, ::channel)
    }

    suspend fun categoryInfo(channelRelationMetadataId: Int): CategoryChannelInfo {
        positiveInput(channelRelationMetadataId)
        val data = data(Endpoints.CategoryChannelInfo, mapOf("channelRelationMetadataId" to "$channelRelationMetadataId"))
        val info = CategoryChannelInfo(
            channel = channel(read.obj(data["topInfo"])),
            dimensions = read.rows(data["metadata"]).map { dimension(it, 0) },
        )
        if (info.channel.relationMetadataValueId != channelRelationMetadataId) read.invalid()
        return info
    }

    /** One page of a channel's albums, 60 at a time. [metadata] maps a filter row's id to the chosen value's id. */
    suspend fun categoryAlbums(
        groupId: Int,
        metadataValueId: Int? = null,
        page: Int = 1,
        sort: Int = 1,
        metadata: Map<Int, Int> = emptyMap(),
    ): Page<Album> {
        positiveInput(groupId)
        pageInput(page)
        if (metadataValueId != null) positiveInput(metadataValueId)
        if (sort !in 1..3) throw XmlyException("invalid_sort", definitive = true)
        for ((row, value) in metadata) {
            if (row != -1) positiveInput(row)
            positiveInput(value)
        }
        val query = buildMap {
            put("groupId", "$groupId")
            put("pageNum", "$page")
            put("pageSize", "60")
            put("sort", "$sort")
            if (metadataValueId != null) put("metadataValueId", "$metadataValueId")
            if (metadata.isNotEmpty()) put("metadata", buildJsonObject { for ((row, value) in metadata) put("$row", value) }.toString())
        }
        return pageOf(data(Endpoints.CategoryChannelAlbums, query), "albums", "pageNum", "total", page, ::categoryAlbum)
    }

    suspend fun rankTabs(): List<RankCategory> {
        val data = data(Endpoints.RankTabs, mapOf("sceneId" to "1"))
        return positioned(data["tabLists"]).map { row ->
            RankCategory(
                read.positive(row["id"]),
                read.text(row["name"]),
                positioned(row["tabWraps"]).map { RankTab(read.positive(it["id"]), read.positive(it["rankingId"]), read.text(it["name"])) },
            )
        }
    }

    suspend fun rankElements(rankingId: Int): List<RankBlock> {
        positiveInput(rankingId)
        return read.rows(data(Endpoints.RankElements, mapOf("rankingId" to "$rankingId"))["rankList"]).map(::rankBlock)
    }

    /** At most 20 words, the same list for everyone. */
    suspend fun hotWords(): List<HotWord> {
        val response = http.call(Endpoints.HotWords, null, query = mapOf("categoryId" to "-1", "size" to "20"))
        return read.rows(response["hotWordResultList"], 20).map { row ->
            val word = read.text(row["word"])
            if (word.isBlank()) read.invalid()
            HotWord(word, read.text(row["display_word"]).ifEmpty { word })
        }
    }

    private suspend fun data(endpoint: Endpoint, query: Map<String, String>): JsonObject =
        read.obj(http.call(endpoint, null, query = query)["data"])

    private fun <T> pageOf(data: JsonObject, listKey: String, pageKey: String, totalKey: String, expected: Int, parse: (JsonObject) -> T): Page<T> {
        val page = read.positive(data[pageKey])
        val size = read.count(data["pageSize"])
        val total = read.count(data[totalKey])
        if (total == null || size == null || (size == 0L && total != 0L) || page != expected) read.invalid()
        val rows = read.rows(data[listKey])
        if (rows.size > size || rows.size > total) read.invalid()
        // An empty page reports size 0; paging controls need a positive divisor.
        return Page(rows.map(parse), page, if (size == 0L) 1 else size.toInt(), total.toInt())
    }

    private fun channel(row: JsonObject) = CategoryChannel(
        read.positive(row["channelId"]),
        read.text(row["channelName"]),
        read.positive(row["relationMetadataValueId"]),
        read.image(row["coverPath"]),
    )

    private fun categoryAlbum(row: JsonObject) = Album(
        id = read.id(row["albumId"]),
        title = read.text(row["albumTitle"]),
        artist = read.text(row["albumUserNickName"]),
        intro = read.text(row["intro"]),
        coverUrl = read.image(row["albumCoverPath"]),
        trackCount = read.countInt(row["albumTrackCount"]) ?: 0,
    )

    private fun metadataTitle(row: JsonObject) = read.text(row["displayName"]).ifEmpty { read.text(row["name"]) }

    private fun dimension(row: JsonObject, depth: Int): CategoryDimension {
        val id = read.integer(row["id"])
        if (depth > 32 || id == null || (id != -1L && id <= 0) || id > Int.MAX_VALUE) read.invalid()
        val seen = mutableSetOf<Int>()
        val values = read.rows(row["metadataValues"]).map { value ->
            val valueId = read.positive(value["id"])
            if (!seen.add(valueId)) read.invalid()
            val nested = mutableListOf<CategoryDimension>()
            val nestedIds = mutableSetOf<Int>()
            if (value.containsKey("metadataValues")) {
                for (child in read.rows(value["metadataValues"])) {
                    val dimension = dimension(child, depth + 1)
                    if (!nestedIds.add(dimension.id)) read.invalid()
                    nested += dimension
                }
            }
            CategoryValue(valueId, metadataTitle(value), nested)
        }
        return CategoryDimension(id.toInt(), metadataTitle(row), values)
    }

    /** Rows ordered by their `position`, falling back to where they came in the list. */
    private fun positioned(value: JsonElement?): List<JsonObject> =
        read.rows(value).withIndex().sortedWith(compareBy({ it.value["position"].let(read::count) ?: it.index.toLong() }, { it.index }))
            .map { it.value }

    private fun rankBlock(row: JsonObject): RankBlock {
        val type = read.countInt(row["contentType"]) ?: read.invalid()
        val positions = linkedMapOf<String, Int>()
        val albums = mutableListOf<Album>()
        val anchors = mutableListOf<AnchorProfile>()
        val stats = mutableMapOf<String, AlbumStats>()
        if (type == 2 || type == 3) {
            val ids = row["ids"] as? JsonArray ?: read.invalid()
            if (ids.size > 1000) read.invalid()
            ids.forEachIndexed { index, element ->
                val id = read.id(element)
                if (positions.containsKey(id)) read.invalid()
                positions[id] = index + 1
            }
            val seen = mutableSetOf<String>()
            for (item in read.rows(row[if (type == 2) "albums" else "anchors"])) {
                val id = read.id(item["id"])
                if (!positions.containsKey(id) || !seen.add(id)) read.invalid()
                if (type == 2) {
                    albums += Album(
                        id = id,
                        title = read.text(item["albumTitle"]),
                        artist = read.text(item["anchorName"]),
                        intro = read.text(item["description"]),
                        coverUrl = read.image(item["cover"]),
                        trackCount = read.countInt(item["trackCount"]) ?: 0,
                    )
                    stats[id] = AlbumStats(score = read.score(item["albumScore"]), playCount = read.count(item["playCount"]))
                } else {
                    anchors += AnchorProfile(
                        uid = id,
                        nickname = read.text(item["anchorName"]),
                        avatarUrl = read.image(item["cover"]),
                        intro = read.text(item["personalDescription"]),
                        fans = read.countInt(item["fansCount"]),
                        albums = read.countInt(item["albumCount"]),
                        tracks = read.countInt(item["trackCount"]),
                    )
                }
            }
        }
        return RankBlock(read.positive(row["rankId"]), read.text(row["title"]), type, albums, anchors, stats, positions)
    }

    private fun positiveInput(value: Int) {
        xmlyIdentifier("$value")
    }

    private fun pageInput(page: Int) {
        if (page !in 1..10000) throw XmlyException("invalid_page", definitive = true)
    }
}