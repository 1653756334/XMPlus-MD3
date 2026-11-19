package com.xuleo.xmplus.md3.core.media

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.icu.text.BreakIterator
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.model.dartTrim
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The album and episode a bookmark belongs to. Bookmarks belong to the device, so no account is part of them. */
data class BookmarkEpisode internal constructor(
    internal val albumId: String,
    internal val trackId: String,
)

data class BookmarkDraft internal constructor(
    internal val episode: BookmarkEpisode,
    val positionMs: Long,
    internal val createdAtMs: Long,
    internal val source: BookmarkSource,
)

data class PlaybackBookmark internal constructor(
    internal val episode: BookmarkEpisode,
    val id: Long,
    val positionMs: Long,
    val createdAtMs: Long,
    val note: String,
)

/** The names and cover a bookmark is listed under, kept with it so 书签 needs no network. */
data class BookmarkSource(val albumTitle: String, val trackTitle: String, val anchor: String, val coverUrl: String?)

/**
 * A bookmark with the album and episode it belongs to, as 书签 lists it across every album. [named] is false until the
 * names have been looked up; an episode whose catalogue has no cover is named too, with no cover.
 */
data class BookmarkEntry(
    val albumId: String,
    val trackId: String,
    val positionMs: Long,
    val createdAtMs: Long,
    val note: String,
    val source: BookmarkSource,
    val named: Boolean,
)

const val BookmarkNoteLimit = 1_000

/** Match the original limit on user-perceived characters, not UTF-16 code units. */
fun isBookmarkNoteValid(note: String): Boolean {
    if (note.length <= BookmarkNoteLimit) return true
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(note) }
    repeat(BookmarkNoteLimit + 1) { if (iterator.next() == BreakIterator.DONE) return true }
    return false
}

/** Bookmarks are local, one list for the device whichever account is signed in; no credential, media URL or cloud history state is stored here. */
class PlaybackBookmarkStore(context: Context) {
    private val appContext = context.applicationContext
    private val database by lazy { Database(appContext) }

    internal suspend fun list(episode: BookmarkEpisode): List<PlaybackBookmark> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT id, position_ms, created_ms, note FROM bookmarks WHERE $EpisodeFilter ORDER BY created_ms DESC, id DESC",
            episode.args(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(PlaybackBookmark(episode, cursor.getLong(0), cursor.getLong(1), cursor.getLong(2), cursor.getString(3)))
                }
            }
        }
    }

    /**
     * Writes the names and cover of an episode's bookmarks that have not been looked up. A NULL cover means not looked up
     * yet; an empty one means looked up and the catalogue has none, so the episode is never looked up again.
     */
    suspend fun completeSource(albumId: String, trackId: String, source: BookmarkSource) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("album_title", source.albumTitle)
            put("track_title", source.trackTitle)
            put("anchor", source.anchor)
            put("cover", source.coverUrl.orEmpty())
        }
        database.writableDatabase.update("bookmarks", values, "album = ? AND track = ? AND cover IS NULL", arrayOf(albumId, trackId))
        Unit
    }

    /** Every bookmark on this device, newest first. */
    suspend fun all(): List<BookmarkEntry> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT album, track, position_ms, created_ms, note, album_title, track_title, anchor, cover, cover IS NOT NULL " +
                "FROM bookmarks ORDER BY created_ms DESC, id DESC",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        BookmarkEntry(
                            albumId = cursor.getString(0),
                            trackId = cursor.getString(1),
                            positionMs = cursor.getLong(2),
                            createdAtMs = cursor.getLong(3),
                            note = cursor.getString(4),
                            source = BookmarkSource(
                                albumTitle = cursor.getString(5),
                                trackTitle = cursor.getString(6),
                                anchor = cursor.getString(7),
                                coverUrl = if (cursor.isNull(8)) null else cursor.getString(8).ifEmpty { null },
                            ),
                            named = cursor.getInt(9) == 1,
                        ),
                    )
                }
            }
        }
    }

    internal suspend fun add(draft: BookmarkDraft, note: String): PlaybackBookmark = withContext(Dispatchers.IO) {
        val clean = checkedNote(note)
        val values = ContentValues().apply {
            put("album", draft.episode.albumId)
            put("track", draft.episode.trackId)
            put("position_ms", draft.positionMs)
            put("created_ms", draft.createdAtMs)
            put("note", clean)
            put("album_title", draft.source.albumTitle)
            put("track_title", draft.source.trackTitle)
            put("anchor", draft.source.anchor)
            // The names are known when the bookmark is made, so it never needs a lookup.
            put("cover", draft.source.coverUrl.orEmpty())
        }
        val id = database.writableDatabase.insertOrThrow("bookmarks", null, values)
        PlaybackBookmark(draft.episode, id, draft.positionMs, draft.createdAtMs, clean)
    }

    internal suspend fun edit(bookmark: PlaybackBookmark, note: String): PlaybackBookmark = withContext(Dispatchers.IO) {
        val clean = checkedNote(note)
        val values = ContentValues().apply { put("note", clean) }
        if (database.writableDatabase.update("bookmarks", values, RecordFilter, bookmark.args()) != 1) stale()
        bookmark.copy(note = clean)
    }

    internal suspend fun delete(bookmark: PlaybackBookmark) = withContext(Dispatchers.IO) {
        if (database.writableDatabase.delete("bookmarks", RecordFilter, bookmark.args()) != 1) stale()
    }

    private fun checkedNote(note: String): String {
        if (!isBookmarkNoteValid(note)) throw XmlyException("bookmark_note_limit")
        return note.dartTrim()
    }

    private fun BookmarkEpisode.args() = arrayOf(albumId, trackId)
    private fun PlaybackBookmark.args() = episode.args() + arrayOf(id.toString(), note)

    private class Database(context: Context) : SQLiteOpenHelper(
        context, File(context.noBackupFilesDir, "playback-bookmarks.db").absolutePath, null, 3,
    ) {
        override fun onCreate(db: SQLiteDatabase) = createTable(db)

        // Version 2 adds the names a bookmark is listed under; older bookmarks keep their position and note, with no names yet.
        // Version 3 drops the account: bookmarks belong to the device, so one list serves every signed-in account.
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN album_title TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN track_title TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN anchor TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN cover TEXT")
            }
            if (oldVersion < 3) {
                db.execSQL("DROP INDEX IF EXISTS bookmarks_by_episode")
                db.execSQL("ALTER TABLE bookmarks RENAME TO bookmarks_with_account")
                createTable(db)
                db.execSQL(
                    "INSERT INTO bookmarks (id, album, track, position_ms, created_ms, note, album_title, track_title, anchor, cover) " +
                        "SELECT id, album, track, position_ms, created_ms, note, album_title, track_title, anchor, cover FROM bookmarks_with_account",
                )
                db.execSQL("DROP TABLE bookmarks_with_account")
            }
        }

        // A NULL cover means the names have not been looked up yet; see completeSource.
        private fun createTable(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE bookmarks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    album TEXT NOT NULL,
                    track TEXT NOT NULL,
                    position_ms INTEGER NOT NULL,
                    created_ms INTEGER NOT NULL,
                    note TEXT NOT NULL,
                    album_title TEXT NOT NULL DEFAULT '',
                    track_title TEXT NOT NULL DEFAULT '',
                    anchor TEXT NOT NULL DEFAULT '',
                    cover TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX bookmarks_by_episode ON bookmarks(album, track, created_ms DESC, id DESC)")
        }
    }

    private companion object {
        const val EpisodeFilter = "album = ? AND track = ?"
        const val RecordFilter = "$EpisodeFilter AND id = ? AND note = ?"
        fun stale(): Nothing = throw XmlyException("stale_bookmark")
    }
}