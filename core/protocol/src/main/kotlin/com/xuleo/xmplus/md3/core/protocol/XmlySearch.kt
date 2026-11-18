package com.xuleo.xmplus.md3.core.protocol

import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.AnchorProfile
import com.xuleo.xmplus.md3.core.model.SearchKind
import com.xuleo.xmplus.md3.core.model.SearchPage
import com.xuleo.xmplus.md3.core.model.Track
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.model.dartTrim
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** Site-wide search over albums, tracks and anchors. Public: it never carries an account. */
class XmlySearch(private val http: XmlyHttp) {
    private val read = Reader("invalid_catalogue")

    /** One page of results; [SearchKind.All] returns a page of each kind. */
    suspend fun search(keyword: String, kind: SearchKind = SearchKind.Track, page: Int = 1, pageSize: Int = 20): SearchPage {
        val query = keyword.dartTrim()
        if (query.isEmpty() || keyword.length > 200) throw XmlyException("invalid_search", definitive = true)
        if (page !in 1..10000 || pageSize !in 1..50) throw XmlyException("invalid_page", definitive = true)
        val response = http.call(
            Endpoints.Search, null,
            query = mapOf(
                "kw" to query, "core" to kind.code, "page" to "$page", "rows" to "$pageSize",
                "device" to "iPhone", "condition" to "relation", "spellchecker" to "true",
            ),
        )
        val result = ((response["data"] as? JsonObject)?.get("result") as? JsonObject) ?: JsonObject(emptyMap())
        val tracks = mutableListOf<Track>()
        val albums = mutableListOf<Album>()
        val anchors = mutableListOf<AnchorProfile>()
        val totals = linkedMapOf<SearchKind, Int>()
        for (section in if (kind == SearchKind.All) listOf(SearchKind.Album, SearchKind.Track, SearchKind.Anchor) else listOf(kind)) {
            val values = (result[if (kind == SearchKind.All) section.code else "response"] as? JsonObject) ?: JsonObject(emptyMap())
            val docs = values["docs"] as? JsonArray ?: read.invalid()
            totals[section] = total(values["numFound"] ?: JsonPrimitive(0))
            val known = mutableSetOf<String>()
            for (doc in docs.take(pageSize)) {
                val row = doc as? JsonObject ?: read.invalid()
                when (section) {
                    SearchKind.Album -> read.albumOf(row).let { if (known.add(it.id)) albums += it }
                    SearchKind.Anchor -> read.anchorOf(row).let { if (known.add(it.uid)) anchors += it }
                    else -> read.trackOf(row).let { if (known.add(it.id)) tracks += it }
                }
            }
        }
        return SearchPage(kind, tracks, albums, anchors, totals, page, pageSize, totals.values.sum())
    }

    private fun total(value: JsonElement): Int {
        val number = (value as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
        if (number == null || !number.isFinite() || number < 0 || number != Math.floor(number)) throw XmlyException("invalid_page")
        return number.toInt()
    }
}