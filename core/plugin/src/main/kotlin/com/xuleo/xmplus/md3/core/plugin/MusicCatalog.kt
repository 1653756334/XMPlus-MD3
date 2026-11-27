package com.xuleo.xmplus.md3.core.plugin

import com.xuleo.xmplus.md3.core.model.MusicAlbum
import com.xuleo.xmplus.md3.core.model.MusicArtist
import com.xuleo.xmplus.md3.core.model.MusicItem
import com.xuleo.xmplus.md3.core.model.MusicSheet
import com.xuleo.xmplus.md3.core.model.PluginMedia
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** One page of a list a plugin returns, and whether it was the last. */
data class PluginPage<out T>(val items: List<T>, val isEnd: Boolean) {
    companion object {
        val End = PluginPage<Nothing>(emptyList(), isEnd = true)
    }
}

/** MusicFree's search types, by the key plugins receive. */
enum class SearchType(val key: String) {
    Music("music"), Album("album"), Artist("artist"), Sheet("sheet"), Lyric("lyric");

    companion object {
        fun fromKey(key: String?): SearchType? = entries.firstOrNull { it.key == key }
    }
}

/** A tag of the sheet square. [raw] goes back to the plugin unchanged. */
data class SheetTag(val id: String, val title: String, val raw: JsonObject)

data class SheetTagGroup(val title: String, val tags: List<SheetTag>)

data class SheetTags(val pinned: List<SheetTag>, val groups: List<SheetTagGroup>) {
    val isEmpty: Boolean get() = pinned.isEmpty() && groups.isEmpty()
}

/** A titled group of sheets, as 榜单 returns them. */
data class SheetGroup(val title: String, val sheets: List<MusicSheet>)

/** A sheet, ranking or album page: the item itself (completed by the plugin on page 1) and its songs. */
data class SongListPage<out T : PluginMedia>(val item: T, val songs: PluginPage<MusicItem>)

/** What 从链接导入 found: the songs, and the sheet's own title when the plugin gave one. */
data class ImportedSheet(val songs: List<MusicItem>, val title: String?) {
    companion object {
        val Empty = ImportedSheet(emptyList(), null)
    }
}

/** Where a song plays from: a link with the headers it needs, or a downloaded [localFile]. */
data class MediaSource(val url: String, val headers: Map<String, String>, val quality: String?, val localFile: java.io.File? = null)

/**
 * What 音乐 asks plugins for, with the same rules as MusicFree's `PluginMethodsWrapper`: a missing method is an empty
 * result, items carry the plugin's platform, `isEnd` is true unless the plugin says `false`, and page 1 of a detail
 * merges what the plugin returns into the item that was opened. A plugin that throws or returns nothing where a
 * result is required fails only that call, with [PluginCallException], so the page can offer 重试.
 */
class MusicCatalog(private val manager: PluginManager) {

    suspend fun sheetTags(platform: String): SheetTags {
        val root = call(platform, "getRecommendSheetTags") as? JsonObject ?: return SheetTags(emptyList(), emptyList())
        val groups = root.array("data").mapNotNull { element ->
            val group = element as? JsonObject ?: return@mapNotNull null
            SheetTagGroup(group.text("title").orEmpty(), group.array("data").mapNotNull(::tag)).takeIf { it.tags.isNotEmpty() }
        }
        return SheetTags(root.array("pinned").mapNotNull(::tag), groups)
    }

    /** Sheets of [tag]; null asks for the plugin's default list, as MusicFree sends `{ id: "" }`. */
    suspend fun sheets(platform: String, tag: SheetTag?, page: Int): PluginPage<MusicSheet> {
        val argument = tag?.raw ?: buildJsonObject { put("title", DefaultTagTitle); put("id", "") }
        val root = required(call(platform, "getRecommendSheetsByTag", argument, JsonPrimitive(page))) ?: return PluginPage.End
        return PluginPage(root.array("data").objects().mapNotNull { MusicSheet.from(it, platform) }, root.isEnd())
    }

    suspend fun topLists(platform: String): List<SheetGroup> {
        val reply = call(platform, "getTopLists") ?: return emptyList()
        val groups = reply as? JsonArray ?: throw PluginCallException("empty")
        return groups.objects().map { group ->
            SheetGroup(group.text("title").orEmpty(), group.array("data").objects().mapNotNull { MusicSheet.from(it, platform) })
        }.filter { it.sheets.isNotEmpty() }
    }

    suspend fun topListDetail(sheet: MusicSheet, page: Int): SongListPage<MusicSheet> {
        val root = required(call(sheet.platform, "getTopListDetail", sheet.raw, JsonPrimitive(page)))
            ?: return SongListPage(sheet, PluginPage.End)
        val item = if (page <= 1) merged(sheet.raw, root["topListItem"])?.let { MusicSheet.from(it, sheet.platform) } else null
        return SongListPage(item ?: sheet, songs(root, sheet.platform))
    }

    suspend fun sheetDetail(sheet: MusicSheet, page: Int): SongListPage<MusicSheet> {
        val root = required(call(sheet.platform, "getMusicSheetInfo", sheet.raw, JsonPrimitive(page)))
            ?: return SongListPage(sheet, PluginPage(sheet.raw.array("musicList").songs(sheet.platform), isEnd = true))
        val item = if (page <= 1) merged(sheet.raw, root["sheetItem"])?.let { MusicSheet.from(it, sheet.platform) } else null
        return SongListPage(item ?: sheet, songs(root, sheet.platform))
    }

    /** Songs of an album take the album's title, as MusicFree sets it. */
    suspend fun albumDetail(album: MusicAlbum, page: Int): SongListPage<MusicAlbum> {
        val root = required(call(album.platform, "getAlbumInfo", album.raw, JsonPrimitive(page)))
            ?: return SongListPage(album, PluginPage(album.raw.array("musicList").songs(album.platform, album.title), isEnd = true))
        val item = if (page <= 1) merged(album.raw, root["albumItem"])?.let { MusicAlbum.from(it, album.platform) } else null
        val songs = root.array("musicList").songs(album.platform, album.title)
        return SongListPage(item ?: album, PluginPage(songs, root.isEnd()))
    }

    suspend fun artistSongs(artist: MusicArtist, page: Int): PluginPage<MusicItem> =
        artistWorks(artist, page, SearchType.Music) { MusicItem.from(it, artist.platform) }

    suspend fun artistAlbums(artist: MusicArtist, page: Int): PluginPage<MusicAlbum> =
        artistWorks(artist, page, SearchType.Album) { MusicAlbum.from(it, artist.platform) }

    /** Whether 查看歌手 can work for a song of [platform]: the plugin searches artists and lists their works. */
    fun canFindArtist(platform: String): Boolean =
        enabledPlugin(platform)?.let { GetArtistWorks in it.info.methods && it.searches(SearchType.Artist) } == true

    /** Whether 查看专辑 can work for a song of [platform]: the plugin searches albums and opens them. */
    fun canFindAlbum(platform: String): Boolean =
        enabledPlugin(platform)?.let { GetAlbumInfo in it.info.methods && it.searches(SearchType.Album) } == true

    /**
     * The artist [name] of [song] as the plugin's own search returns them, since a song carries no artist object to
     * open. The song's `artistId` wins when it names this artist; otherwise the same name. Null when neither is listed.
     */
    suspend fun findArtist(song: MusicItem, name: String): MusicArtist? {
        val found = search(song.platform, name, 1, SearchType.Artist).items.filterIsInstance<MusicArtist>()
        val artistId = song.raw.text("artistId")?.takeIf { it.isNotBlank() }
        val single = splitArtists(song.artist).size <= 1
        return found.firstOrNull { it.id == artistId && (single || sameName(it.name, name)) }
            ?: found.firstOrNull { sameName(it.name, name) }
    }

    /**
     * The album of [song] as the plugin's own search returns it: the song's `albumId`, otherwise the same title by one
     * of the song's artists (or by no one). The title alone is tried first, then with the first artist.
     */
    suspend fun findAlbum(song: MusicItem): MusicAlbum? {
        val title = song.album.takeIf { it.isNotBlank() } ?: return null
        val albumId = song.raw.text("albumId")?.takeIf { it.isNotBlank() }
        val artists = splitArtists(song.artist)
        fun pick(found: List<MusicAlbum>): MusicAlbum? =
            found.firstOrNull { albumId != null && it.id == albumId }
                ?: found.firstOrNull { album ->
                    sameName(album.title, title) &&
                        (album.artist.isBlank() || splitArtists(album.artist).any { by -> artists.any { sameName(it, by) } })
                }
        fun albums(query: String) = suspend { search(song.platform, query, 1, SearchType.Album).items.filterIsInstance<MusicAlbum>() }
        pick(albums(title)())?.let { return it }
        val artist = artists.firstOrNull() ?: return null
        return pick(albums("$title $artist")())
    }

    /** Songs for [SearchType.Music] and [SearchType.Lyric], otherwise albums, artists or sheets. */
    suspend fun search(platform: String, query: String, page: Int, type: SearchType): PluginPage<PluginMedia> {
        val reply = call(platform, "search", JsonPrimitive(query), JsonPrimitive(page), JsonPrimitive(type.key))
        val root = reply as? JsonObject ?: return PluginPage.End
        val data = root["data"] as? JsonArray ?: return PluginPage.End
        val items = data.objects().mapNotNull<JsonObject, PluginMedia> {
            when (type) {
                SearchType.Music, SearchType.Lyric -> MusicItem.from(it, platform)
                SearchType.Album -> MusicAlbum.from(it, platform)
                SearchType.Artist -> MusicArtist.from(it, platform)
                SearchType.Sheet -> MusicSheet.from(it, platform)
            }
        }
        return PluginPage(items, root.isEnd())
    }

    /**
     * MusicFree's `importMusicSheet`: the plugin reads a share link or text and returns the songs it holds. Most
     * return a bare list; one that returns an object may carry them under `musicList`/`data` and name the sheet it
     * came from as `targetSheetItem`/`sheetItem`. Nothing found is an empty result; a plugin that throws fails the call.
     */
    suspend fun importSheet(platform: String, url: String): ImportedSheet {
        val reply = call(platform, "importMusicSheet", JsonPrimitive(url)) ?: return ImportedSheet.Empty
        val list = when (reply) {
            is JsonArray -> reply
            is JsonObject -> (reply["musicList"] ?: reply["data"]) as? JsonArray ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        val named = ((reply as? JsonObject)?.let { it["targetSheetItem"] ?: it["sheetItem"] }) as? JsonObject
        return ImportedSheet(list.songs(platform), named?.text("title")?.takeIf { it.isNotBlank() })
    }

    /** MusicFree's `importMusicItem`: one song from a link. Null when the plugin returns nothing. */
    suspend fun importItem(platform: String, url: String): MusicItem? {
        val reply = call(platform, "importMusicItem", JsonPrimitive(url)) as? JsonObject ?: return null
        return MusicItem.from(reply, platform)
    }

    private suspend fun <T> artistWorks(artist: MusicArtist, page: Int, type: SearchType, item: (JsonObject) -> T?): PluginPage<T> {
        val reply = call(artist.platform, "getArtistWorks", artist.raw, JsonPrimitive(page), JsonPrimitive(type.key))
        val root = reply as? JsonObject ?: return PluginPage.End
        val data = root["data"] as? JsonArray ?: return PluginPage.End
        return PluginPage(data.objects().mapNotNull(item), root.isEnd())
    }

    /**
     * MusicFree's `getMusicInfo` merge: the plugin's answer over the song, keeping its id and platform; a blank or
     * non-text `url` is dropped. Null when the plugin has no such method or returns nothing.
     */
    suspend fun musicInfo(song: MusicItem): MusicItem? {
        val info = call(song.platform, "getMusicInfo", song.raw) as? JsonObject ?: return null
        val url = info["url"]
        val usable = if (url is JsonPrimitive && url.isString && url.content.isNotBlank()) info else JsonObject(info - "url")
        val id = song.raw["id"] ?: return null
        return MusicItem.from(JsonObject(song.raw + usable + ("id" to id)), song.platform)
    }

    private fun enabledPlugin(platform: String): InstalledPlugin? =
        manager.plugins.value.orEmpty().firstOrNull { it.platform == platform && it.enabled }

    /** The plugin has `search` and lists [type] among its search types, or declares none, as MusicFree reads it. */
    private fun InstalledPlugin.searches(type: SearchType): Boolean {
        if (Search !in info.methods) return false
        val declared = (info.declared["supportedSearchType"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        return declared.isNullOrEmpty() || type.key in declared
    }

    private suspend fun call(platform: String, method: String, vararg args: JsonElement): JsonElement? {
        val host = manager.host(platform) ?: throw PluginCallException("unavailable")
        return host.call(method, args.toList())
    }

    /** A method that exists but returned nothing fails the call; a missing method (null) is an empty result. */
    private fun required(reply: JsonElement?): JsonObject? = when (reply) {
        null -> null
        is JsonObject -> reply
        else -> throw PluginCallException("empty")
    }

    private fun songs(root: JsonObject, platform: String) = PluginPage(root.array("musicList").songs(platform), root.isEnd())

    private fun JsonArray.songs(platform: String, album: String? = null): List<MusicItem> = objects().mapNotNull {
        MusicItem.from(if (album == null) it else JsonObject(it + ("album" to JsonPrimitive(album))), platform)
    }

    private fun merged(base: JsonObject, overlay: JsonElement?): JsonObject? =
        if (overlay is JsonObject) JsonObject(base + overlay) else base

    private fun tag(element: JsonElement): SheetTag? {
        val raw = element as? JsonObject ?: return null
        val id = raw.text("id") ?: return null
        return SheetTag(id, raw.text("title").orEmpty(), raw)
    }

    private companion object {
        /** Sent to the plugin as the tag's title; the plugin's own text, not a label of ours. */
        const val DefaultTagTitle = "默认" // ui-text: data
        const val Search = "search"
        const val GetArtistWorks = "getArtistWorks"
        const val GetAlbumInfo = "getAlbumInfo"
    }
}

/** How plugins join several artists in one line: `&`, `、`, `/`, commas, ` x `, `feat.` and `ft.`. */
private val ArtistSeparators = Regex("""&|\u3001|/|,|\uFF0C|\s+x\s+|\b(?:feat|ft)\.""", RegexOption.IGNORE_CASE)

/** The artists of a song's artist line, in order, each once. */
fun splitArtists(artist: String): List<String> =
    artist.split(ArtistSeparators)
        .map { part -> part.trim { it.isWhitespace() || it in "()[]\uFF08\uFF09" } }
        .filter { it.isNotEmpty() }
        .distinctBy(::nameKey)

/** Names match ignoring case, spaces and full-width forms. */
private fun sameName(a: String, b: String): Boolean = nameKey(a) == nameKey(b)

private fun nameKey(name: String): String =
    java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFKC).lowercase().filterNot { it.isWhitespace() }

private fun JsonObject.isEnd(): Boolean = (this["isEnd"] as? JsonPrimitive)?.booleanOrNull != false

private fun JsonObject.array(name: String): JsonArray = this[name] as? JsonArray ?: JsonArray(emptyList())

private fun JsonArray.objects(): List<JsonObject> = filterIsInstance<JsonObject>()

private fun JsonObject.text(name: String): String? {
    val value = this[name] as? JsonPrimitive ?: return null
    if (value is JsonNull) return null
    return value.contentOrNull
}
