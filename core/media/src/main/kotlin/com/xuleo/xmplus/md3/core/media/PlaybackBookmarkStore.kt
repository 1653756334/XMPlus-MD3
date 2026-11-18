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

/** The authorizing account and episode are fixed before any bookmark action can suspend. */
data class BookmarkEpisode internal constructor(
    internal val account: String,
    internal val albumId: String,
    internal val trackId: String,
)

data class BookmarkDraft internal constructor(
    internal val episode: BookmarkEpisode,
    val positionMs: Long,
    internal val createdAtMs: Long,
)

data class PlaybackBookmark internal constructor(
    internal val episode: BookmarkEpisode,
    val id: Long,
    val positionMs: Long,
    val createdAtMs: Long,
    val note: String,
)

const val BookmarkNoteLimit = 1_000

/** Match the original limit on user-perceived characters, not UTF-16 code units. */
fun isBookmarkNoteValid(note: String): Boolean {
    if (note.length <= BookmarkNoteLimit) return true
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(note) }
    repeat(BookmarkNoteLimit + 1) { if (iterator.next() == BreakIterator.DONE) return true }
    return false
}

/** Bookmarks are local; no credential, media URL or cloud history state is stored here. */
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

    internal suspend fun add(draft: BookmarkDraft, note: String): PlaybackBookmark = withContext(Dispatchers.IO) {
        val clean = checkedNote(note)
        val values = ContentValues().apply {
            put("account", draft.episode.account)
            put("album", draft.episode.albumId)
            put("track", draft.episode.trackId)
            put("position_ms", draft.positionMs)
            put("created_ms", draft.createdAtMs)
            put("note", clean)
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

    private fun BookmarkEpisode.args() = arrayOf(account, albumId, trackId)
    private fun PlaybackBookmark.args() = episode.args() + arrayOf(id.toString(), note)

    private class Database(context: Context) : SQLiteOpenHelper(
        context, File(context.noBackupFilesDir, "playback-bookmarks.db").absolutePath, null, 1,
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE bookmarks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    account TEXT NOT NULL,
                    album TEXT NOT NULL,
                    track TEXT NOT NULL,
                    position_ms INTEGER NOT NULL,
                    created_ms INTEGER NOT NULL,
                    note TEXT NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX bookmarks_by_episode ON bookmarks(account, album, track, created_ms DESC, id DESC)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        const val EpisodeFilter = "account = ? AND album = ? AND track = ?"
        const val RecordFilter = "$EpisodeFilter AND id = ? AND note = ?"
        fun stale(): Nothing = throw XmlyException("stale_bookmark")
    }
}
