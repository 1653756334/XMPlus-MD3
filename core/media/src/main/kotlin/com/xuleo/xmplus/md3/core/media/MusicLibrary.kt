package com.xuleo.xmplus.md3.core.media

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.xuleo.xmplus.md3.core.model.MusicItem
import com.xuleo.xmplus.md3.core.model.MusicSheet
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** A sheet the user made. [cover] is one the user chose; without it the first song's cover shows, as in MusicFree. */
data class UserSheet(
    val id: String,
    val name: String,
    val cover: String?,
    val createdAtMs: Long,
    val songs: List<MusicItem>,
) {
    val coverUrl: String? get() = cover ?: songs.firstOrNull()?.artwork
}

private data class Library(
    val liked: List<MusicItem> = emptyList(),
    val sheets: List<UserSheet> = emptyList(),
    val starred: List<MusicSheet> = emptyList(),
)

/**
 * The music library, MusicFree's `MusicSheet`: 我喜欢 (a built-in sheet, id [LikesId]), the user's own sheets and the
 * plugin sheets the user starred. Songs are the plugins' full objects; a sheet holds a song (`platform` + `id`) once.
 * Songs added go to the top in the order given, new sheets go first and starred sheets go first, as in MusicFree.
 *
 * Every change applies at once and is written in the background; changes made before the file was read are applied
 * on top of it. All functions are safe on any thread.
 */
class MusicLibrary internal constructor(private val directory: File, private val clock: () -> Long) {
    constructor(context: Context) : this(context.applicationContext.filesDir, System::currentTimeMillis)

    private val file = AtomicFile(File(directory, FileName))
    private val writes = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val access = Mutex()
    private val lock = Any()
    private var data = Library()
    private var ready = false
    private val early = mutableListOf<(Library) -> Library>()
    private var version = 0L
    private var written = 0L
    @Volatile private var migrated = false

    private val mutableLiked = MutableStateFlow<List<MusicItem>>(emptyList())
    private val mutableSheets = MutableStateFlow<List<UserSheet>>(emptyList())
    private val mutableStarred = MutableStateFlow<List<MusicSheet>>(emptyList())
    private val mutableLoaded = MutableStateFlow(false)

    /** 我喜欢, the last liked first. */
    val liked: StateFlow<List<MusicItem>> = mutableLiked.asStateFlow()

    /** The user's own sheets, in the user's order; 我喜欢 is not among them. */
    val sheets: StateFlow<List<UserSheet>> = mutableSheets.asStateFlow()

    /** Starred plugin sheets, as the plugin returned them, the last starred first. */
    val starred: StateFlow<List<MusicSheet>> = mutableStarred.asStateFlow()

    /** True once the saved library has been read. */
    val loaded: StateFlow<Boolean> = mutableLoaded.asStateFlow()

    init {
        writes.launch {
            val saved = read()
            synchronized(lock) {
                val restored = early.fold(saved) { library, change -> change(library) }
                val changed = early.isNotEmpty() || migrated
                early.clear()
                ready = true
                publish(restored)
                if (changed) save()
            }
            mutableLoaded.value = true
        }
    }

    // 我喜欢

    fun isLiked(song: MusicItem): Boolean = liked.value.any { it.isSame(song) }

    /** Likes [song], or unlikes it when it is liked. Returns whether it is liked now. */
    fun toggleLike(song: MusicItem): Boolean {
        val liked = !isLiked(song)
        if (liked) like(song) else unlike(song)
        return liked
    }

    fun like(song: MusicItem) {
        addSongs(LikesId, listOf(song))
    }

    fun unlike(song: MusicItem) = removeSongs(LikesId, listOf(song))

    // Songs of a sheet. [sheetId] may be [LikesId].

    fun songs(sheetId: String): List<MusicItem> =
        if (sheetId == LikesId) liked.value else sheets.value.firstOrNull { it.id == sheetId }?.songs.orEmpty()

    /** Adds the songs not yet in the sheet to its top, in the order given. Returns how many were added. */
    fun addSongs(sheetId: String, songs: List<MusicItem>): Int {
        var added = 0
        mutate { library ->
            library.withSongs(sheetId) { list ->
                val fresh = newSongs(list, songs)
                added = fresh.size
                if (fresh.isEmpty()) list else fresh + list
            }
        }
        return added
    }

    fun removeSongs(sheetId: String, songs: Collection<MusicItem>) {
        if (songs.isEmpty()) return
        val keys = songs.mapTo(HashSet()) { it.key }
        mutate { library ->
            library.withSongs(sheetId) { list -> if (list.none { it.key in keys }) list else list.filterNot { it.key in keys } }
        }
    }

    /** Moves the song at [from] to [to] within the sheet. */
    fun moveSong(sheetId: String, from: Int, to: Int) {
        mutate { library -> library.withSongs(sheetId) { list -> list.moved(from, to) } }
    }

    /** Copies [songs] to the top of [toSheetId]; those already there stay where they are. Returns how many were added. */
    fun copySongs(songs: List<MusicItem>, toSheetId: String): Int = addSongs(toSheetId, songs)

    /** Adds [songs] to [toSheetId] as [copySongs] does, and removes them from [fromSheetId], in one change. */
    fun moveSongs(songs: List<MusicItem>, fromSheetId: String, toSheetId: String) {
        if (songs.isEmpty() || fromSheetId == toSheetId) return
        val keys = songs.mapTo(HashSet()) { it.key }
        mutate { library ->
            library
                .withSongs(toSheetId) { list -> newSongs(list, songs).let { fresh -> if (fresh.isEmpty()) list else fresh + list } }
                .withSongs(fromSheetId) { list -> if (list.none { it.key in keys }) list else list.filterNot { it.key in keys } }
        }
    }

    // The user's sheets

    fun sheet(id: String): UserSheet? = sheets.value.firstOrNull { it.id == id }

    fun sheetFlow(id: String): Flow<UserSheet?> = sheets.map { list -> list.firstOrNull { it.id == id } }.distinctUntilChanged()

    /** Creates an empty sheet at the top of the list and returns its id. */
    fun createSheet(name: String): String {
        val sheet = UserSheet(UUID.randomUUID().toString(), name.trim(), null, clock(), emptyList())
        mutate { library -> library.copy(sheets = listOf(sheet) + library.sheets) }
        return sheet.id
    }

    fun renameSheet(id: String, name: String) {
        val trimmed = name.trim()
        mutate { library -> library.withSheet(id) { if (it.name == trimmed) it else it.copy(name = trimmed) } }
    }

    /** A cover the user chose; null goes back to the first song's. */
    fun setSheetCover(id: String, cover: String?) {
        mutate { library -> library.withSheet(id) { if (it.cover == cover) it else it.copy(cover = cover) } }
    }

    fun deleteSheets(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val set = ids.toHashSet()
        mutate { library ->
            if (library.sheets.none { it.id in set }) library else library.copy(sheets = library.sheets.filterNot { it.id in set })
        }
    }

    fun moveSheet(from: Int, to: Int) {
        mutate { library -> library.sheets.moved(from, to).let { if (it === library.sheets) library else library.copy(sheets = it) } }
    }

    // Starred plugin sheets

    fun isStarred(sheet: MusicSheet): Boolean = starred.value.any { it.isSame(sheet) }

    fun star(sheet: MusicSheet) {
        mutate { library ->
            if (library.starred.any { it.isSame(sheet) }) library else library.copy(starred = listOf(sheet) + library.starred)
        }
    }

    fun unstar(sheet: MusicSheet) {
        mutate { library ->
            if (library.starred.none { it.isSame(sheet) }) library
            else library.copy(starred = library.starred.filterNot { it.isSame(sheet) })
        }
    }

    fun moveStarred(from: Int, to: Int) {
        mutate { library -> library.starred.moved(from, to).let { if (it === library.starred) library else library.copy(starred = it) } }
    }

    // State and storage

    /** Applies [change]; it returns its argument unchanged when there is nothing to do. */
    private fun mutate(change: (Library) -> Library) = synchronized(lock) {
        val next = change(data)
        if (next === data) return@synchronized
        if (!ready) early += change
        publish(next)
        if (ready) save()
    }

    private fun publish(library: Library) {
        data = library
        mutableLiked.value = library.liked
        mutableSheets.value = library.sheets
        mutableStarred.value = library.starred
    }

    /** Called holding [lock]; a write that lost the race to a newer one is dropped. */
    private fun save() {
        val request = ++version
        val snapshot = data
        writes.launch {
            access.withLock {
                if (request < written) return@withLock
                written = request
                write(snapshot)
            }
        }
    }

    private fun read(): Library {
        val text = try {
            file.readFully().decodeToString()
        } catch (_: FileNotFoundException) {
            return migrate()
        } catch (_: Exception) {
            Log.w(Tag, "The music library could not be read")
            return Library()
        }
        return try {
            val root = Json.parseToJsonElement(text).jsonObject
            Library(
                liked = songsOf(root["liked"]),
                sheets = (root["sheets"] as? JsonArray).orEmpty().mapNotNull { element ->
                    val sheet = element as? JsonObject ?: return@mapNotNull null
                    UserSheet(
                        id = sheet.string("id") ?: return@mapNotNull null,
                        name = sheet.string("name").orEmpty(),
                        cover = sheet.string("cover"),
                        createdAtMs = (sheet["createdAt"] as? JsonPrimitive)?.longOrNull ?: 0L,
                        songs = songsOf(sheet["songs"]),
                    )
                }.distinctBy { it.id },
                starred = (root["starred"] as? JsonArray).orEmpty().mapNotNull { element ->
                    val raw = element as? JsonObject ?: return@mapNotNull null
                    MusicSheet.from(raw, raw.string("platform") ?: return@mapNotNull null)
                }.distinctBy { it.platform to it.id },
            )
        } catch (_: Exception) {
            // A damaged file is kept aside rather than overwritten, so nothing is lost for good.
            Log.w(Tag, "The music library is damaged and was set aside")
            file.baseFile.renameTo(File(directory, BrokenName))
            migrate()
        }
    }

    /** The first launch with this store takes 我喜欢 from the file the earlier version kept it in. */
    private fun migrate(): Library {
        val old = AtomicFile(File(directory, OldFavoritesName))
        val text = try {
            old.readFully().decodeToString()
        } catch (_: Exception) {
            return Library()
        }
        // An old file that does not parse is left in place, so a later version can still read it.
        val songs = try {
            songsOf(Json.parseToJsonElement(text).jsonObject["songs"])
        } catch (_: Exception) {
            return Library()
        }
        migrated = true
        return Library(liked = songs)
    }

    private fun write(library: Library) {
        val text = buildJsonObject {
            put("version", 1)
            put("liked", JsonArray(library.liked.map { it.raw }))
            put("sheets", JsonArray(library.sheets.map { sheet ->
                buildJsonObject {
                    put("id", sheet.id)
                    put("name", sheet.name)
                    sheet.cover?.let { put("cover", it) }
                    put("createdAt", sheet.createdAtMs)
                    put("songs", JsonArray(sheet.songs.map { it.raw }))
                }
            }))
            put("starred", JsonArray(library.starred.map { it.raw }))
        }.toString()
        val stream = try {
            file.startWrite()
        } catch (_: Exception) {
            Log.w(Tag, "The music library could not be saved")
            return
        }
        try {
            stream.write(text.encodeToByteArray())
            file.finishWrite(stream)
        } catch (_: Exception) {
            file.failWrite(stream)
            Log.w(Tag, "The music library could not be saved")
            return
        }
        if (migrated) {
            AtomicFile(File(directory, OldFavoritesName)).delete()
            migrated = false
        }
    }

    companion object {
        /** 我喜欢's id, MusicFree's default sheet id. */
        const val LikesId = "favorite"
        private const val FileName = "music-library.json"
        private const val BrokenName = "music-library.damaged.json"
        private const val OldFavoritesName = "music-favorites.json"
        private const val Tag = "MusicLibrary"
    }
}

/** [songs] not in [list], each once, in the order given. */
private fun newSongs(list: List<MusicItem>, songs: List<MusicItem>): List<MusicItem> {
    val present = list.mapTo(HashSet()) { it.key }
    return songs.filter { present.add(it.key) }
}

private fun Library.withSongs(sheetId: String, transform: (List<MusicItem>) -> List<MusicItem>): Library {
    if (sheetId == MusicLibrary.LikesId) {
        val next = transform(liked)
        return if (next === liked) this else copy(liked = next)
    }
    return withSheet(sheetId) { sheet ->
        val next = transform(sheet.songs)
        if (next === sheet.songs) sheet else sheet.copy(songs = next)
    }
}

private fun Library.withSheet(id: String, transform: (UserSheet) -> UserSheet): Library {
    val index = sheets.indexOfFirst { it.id == id }
    if (index == -1) return this
    val sheet = sheets[index]
    val next = transform(sheet)
    if (next === sheet) return this
    return copy(sheets = sheets.toMutableList().also { it[index] = next })
}

/** The list with the item at [from] moved to [to]; the same list when either is out of range or they are equal. */
private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    return toMutableList().also { it.add(to, it.removeAt(from)) }
}

private fun MusicSheet.isSame(other: MusicSheet) = platform == other.platform && id == other.id

private fun songsOf(element: kotlinx.serialization.json.JsonElement?): List<MusicItem> =
    (element as? JsonArray).orEmpty().mapNotNull { item ->
        val raw = item as? JsonObject ?: return@mapNotNull null
        MusicItem.from(raw, raw.string("platform") ?: return@mapNotNull null)
    }.distinctBy { it.key }

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
