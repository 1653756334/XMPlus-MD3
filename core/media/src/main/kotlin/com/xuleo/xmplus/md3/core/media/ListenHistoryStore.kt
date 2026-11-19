package com.xuleo.xmplus.md3.core.media

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.SystemClock
import androidx.annotation.MainThread
import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.Track
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** An album's latest episode and where playback stopped in it. The fields are those of a cloud history row. */
data class ListenRecord(
    val albumId: String,
    val albumTitle: String,
    val anchor: String,
    val coverUrl: String?,
    val trackId: String,
    val trackTitle: String,
    val durationMs: Long,
    val positionMs: Long,
    val playedAtMs: Long,
)

/**
 * Local listening history, one row per album: the episode last played from it, kept on this device only. Playback
 * reports its position from the tick, and a write happens at most every [SaveIntervalMs] while an episode plays, then
 * again whenever playback stops or moves on. Nothing here reads or spends listening time, and cloud history upload is
 * a separate setting.
 */
class ListenHistoryStore internal constructor(
    context: Context,
    private val clock: () -> Long,
    private val elapsed: () -> Long,
) {
    constructor(context: Context) : this(context, System::currentTimeMillis, SystemClock::elapsedRealtime)

    private val database by lazy { Database(context.applicationContext) }
    // Writes and user edits share one lock, so a queued write cannot re-add an episode that was removed after it.
    private val lock = Mutex()
    private val writes = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val version = MutableStateFlow(0)
    private var pending: ListenRecord? = null
    private var lastEpisode: String? = null
    private var lastWriteAt: Long? = null

    /** Every album, most recently played first. Emits again after each change. */
    fun records(): Flow<List<ListenRecord>> = version.map { read() }

    /** Called from the playback tick while audible playback reads a position. */
    @MainThread
    fun observe(album: Album, track: Track, positionMs: Long, durationMs: Long) {
        val record = ListenRecord(
            albumId = album.id,
            albumTitle = album.title,
            anchor = album.artist,
            coverUrl = album.coverUrl,
            trackId = track.id,
            trackTitle = track.title.ifBlank { album.title },
            durationMs = durationMs.coerceAtLeast(0),
            positionMs = positionMs.coerceAtLeast(0),
            playedAtMs = clock(),
        )
        pending = record
        val last = lastWriteAt
        if (lastEpisode != episodeKey(record) || last == null || elapsed() - last >= SaveIntervalMs) write()
    }

    /** Writes the newest observation now; called when playback pauses, stops or moves to another episode. */
    @MainThread
    fun flush() {
        if (pending != null) write()
    }

    @MainThread
    private fun write() {
        val record = pending ?: return
        pending = null
        lastEpisode = episodeKey(record)
        lastWriteAt = elapsed()
        writes.launch {
            lock.withLock { store(record) }
            version.update { it + 1 }
        }
    }

    suspend fun remove(record: ListenRecord) = change {
        database.writableDatabase.delete(Table, "album = ?", arrayOf(record.albumId))
    }

    /** Puts back an album removed by mistake, unless it has been played again since; then the newer row stays. */
    suspend fun restore(record: ListenRecord) = change { store(record, SQLiteDatabase.CONFLICT_IGNORE) }

    private suspend fun change(action: () -> Unit) {
        lock.withLock { withContext(Dispatchers.IO) { action() } }
        version.update { it + 1 }
    }

    private fun store(record: ListenRecord, conflict: Int = SQLiteDatabase.CONFLICT_REPLACE) {
        val values = ContentValues().apply {
            put("album", record.albumId)
            put("album_title", record.albumTitle)
            put("anchor", record.anchor)
            put("cover", record.coverUrl)
            put("track", record.trackId)
            put("track_title", record.trackTitle)
            put("duration_ms", record.durationMs)
            put("position_ms", record.positionMs)
            put("played_ms", record.playedAtMs)
        }
        val db = database.writableDatabase
        db.insertWithOnConflict(Table, null, values, conflict)
        db.execSQL(
            "DELETE FROM $Table WHERE rowid IN (SELECT rowid FROM $Table ORDER BY played_ms ASC " +
                "LIMIT MAX(0, (SELECT COUNT(*) FROM $Table) - $MaxRecords))",
        )
    }

    private suspend fun read(): List<ListenRecord> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT album, album_title, anchor, cover, track, track_title, duration_ms, position_ms, played_ms " +
                "FROM $Table ORDER BY played_ms DESC, rowid DESC",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ListenRecord(
                            albumId = cursor.getString(0),
                            albumTitle = cursor.getString(1),
                            anchor = cursor.getString(2),
                            coverUrl = cursor.getString(3),
                            trackId = cursor.getString(4),
                            trackTitle = cursor.getString(5),
                            durationMs = cursor.getLong(6),
                            positionMs = cursor.getLong(7),
                            playedAtMs = cursor.getLong(8),
                        ),
                    )
                }
            }
        }
    }

    private fun episodeKey(record: ListenRecord) = "${record.albumId}/${record.trackId}"

    private class Database(context: Context) : SQLiteOpenHelper(
        context, File(context.noBackupFilesDir, "listen-history.db").absolutePath, null, 2,
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE listens (
                    album TEXT NOT NULL,
                    album_title TEXT NOT NULL,
                    anchor TEXT NOT NULL,
                    cover TEXT,
                    track TEXT NOT NULL,
                    track_title TEXT NOT NULL,
                    duration_ms INTEGER NOT NULL,
                    position_ms INTEGER NOT NULL,
                    played_ms INTEGER NOT NULL,
                    PRIMARY KEY (album)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX listens_by_time ON listens(played_ms)")
        }

        // Version 1 kept one row per episode. Its rows cannot be read as one per album, so the table is rebuilt.
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $Table")
            onCreate(db)
        }
    }

    private companion object {
        const val Table = "listens"
        const val MaxRecords = 5_000
        const val SaveIntervalMs = 5_000L
    }
}
