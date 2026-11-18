package com.xuleo.xmplus.md3.core.protocol

import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.AlbumSection
import com.xuleo.xmplus.md3.core.model.AlbumStats
import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.HomeChannel
import com.xuleo.xmplus.md3.core.model.HomeContent
import com.xuleo.xmplus.md3.core.model.HomeTab
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.model.dartTrim
import com.xuleo.xmplus.md3.core.model.xmlyIdentifier
import kotlinx.serialization.json.JsonObject

/**
 * The 发现 landing content: channels, their tabs and recommended sections. It is personalised when an account is
 * given and public otherwise.
 */
class XmlyHome(private val http: XmlyHttp, private val clock: () -> Long = System::currentTimeMillis) {
    private val read = Reader("invalid_experience")

    suspend fun channels(credentials: Credentials?): List<HomeChannel> {
        val data = get(Endpoints.HomeChannels, credentials, mapOf("latestTheaterViewTimestamp" to "0", "theaterDefaultMark" to "0"))
        val seen = mutableSetOf<Int>()
        return read.rows(data["subPages"], 100).map { row ->
            val id = read.countInt(row["pageId"])
            val title = read.text(row["title"]).dartTrim()
            if (id == null || id <= 0 || title.isEmpty() || !seen.add(id)) read.invalid()
            HomeChannel(id, title, read.countInt(row["categoryId"]))
        }
    }

    suspend fun home(credentials: Credentials?, pageId: Int, tabId: Int? = null): HomeContent {
        if (pageId <= 0 || (tabId != null && tabId <= 0)) throw XmlyException("invalid_page", definitive = true)
        val data = get(Endpoints.HomeModules, credentials, buildMap {
            put("pageId", "$pageId")
            if (tabId != null) put("tabId", "$tabId")
        })
        val sections = mutableListOf<AlbumSection>()
        val tabs = mutableListOf<HomeTab>()
        val tabIds = mutableSetOf<Int>()
        val modules = (data["modules"] as? JsonObject)?.get("list")
        for (module in read.rows(modules, 100)) {
            val title = read.text(module["title"])
            when (read.text(module["moduleCode"])) {
                "pageTab" -> for (row in read.rows(module["list"], 100)) {
                    val id = read.countInt(row["tabId"])
                    val tabTitle = read.text(row["title"]).dartTrim()
                    if (id == null || id <= 0 || tabTitle.isEmpty() || !tabIds.add(id)) read.invalid()
                    tabs += HomeTab(id, tabTitle)
                }
                "rankingLists" -> for (ranking in read.rows(module["list"], 100)) {
                    // Captured non-default tabs hold no albums and have no supported endpoint; never expose them.
                    if (read.boolean(ranking["isDefault"]) != true) continue
                    val rows = read.rows(ranking["list"], 500)
                    if (rows.isNotEmpty()) sections += section(read.text(ranking["rankingListName"]), rows, ranking = true)
                }
                "feedStream" -> {
                    val rows = read.rows(module["list"], 500).filter { read.text(it["itemType"]) == "ALBUM" }
                        .map { read.obj(it["item"]) }
                    val category = (data["page"] as? JsonObject)?.let { read.countInt(it["categoryId"]) } ?: 0
                    if (rows.isNotEmpty()) sections += section(title, rows, moreCategoryId = category)
                }
                "contentPool" -> {
                    val rows = read.rows(module["list"], 500)
                    if (rows.isNotEmpty()) sections += section(title, rows)
                }
            }
        }
        return HomeContent(sections, tabs)
    }

    private suspend fun get(endpoint: Endpoint, credentials: Credentials?, query: Map<String, String>): JsonObject {
        val response = http.call(endpoint, credentials, clock(), query = query)
        return response["data"] as? JsonObject ?: read.invalid()
    }

    private fun section(title: String, rows: List<JsonObject>, ranking: Boolean = false, moreCategoryId: Int? = null): AlbumSection {
        val albums = mutableListOf<Album>()
        val stats = mutableMapOf<String, AlbumStats>()
        for (row in rows) {
            val album = Album(
                id = xmlyIdentifier(read.anyText(row["albumId"])),
                title = read.anyText(row["albumTitle"]),
                artist = read.anyText(row["nickname"]),
                intro = read.anyText(row["albumIntro"]),
                coverUrl = read.cover(row["coverLarge"], row["coverMiddle"], row["coverSmall"], row["albumCover"]),
                trackCount = read.countInt(row["trackCount"]) ?: 0,
            )
            if (stats.containsKey(album.id)) continue
            albums += album
            stats[album.id] = AlbumStats(
                score = read.score(row["albumScore"])?.takeIf { it in 0.0..10.0 },
                playCount = read.count(row["playCount"]),
                subscribeCount = read.count(row["subscribeCount"]),
                finished = read.boolean(row["isFinished"]) == true,
            )
        }
        // An empty title is the caller's cue to show its own default heading; it is not text to translate here.
        return AlbumSection(title, albums, stats, ranking, moreCategoryId)
    }
}