package com.xuleo.xmplus.md3.core.media

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.SystemClock
import androidx.annotation.MainThread
import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.ContentKind
import com.xuleo.xmplus.md3.core.model.MusicItem
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

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
    /** 听书 rows are one per album; songs (from M4) one per song, with [albumId] holding the song's key. */
    val kind: ContentKind = ContentKind.Episode,
    /** A song's full plugin object as JSON, passed back to the plugin when it is played again. */
    val item: String? = null,
    /** The list a song was played from, when it was played from one. */
    val source: MusicPlaySource? = null,
    /** The saved queue the song was played in, read back by [ListenHistoryStore.resume]. */
    val queue: String? = null,
) {
    /** The song of a song row; null for 听书 rows and for rows whose object no longer parses. */
    val song: MusicItem? get() = if (kind == ContentKind.Song) songOf(item) else null
}

/**
 * Local listening history: one row per album for 听书, the episode last played from it, and one row per song for
 * music, kept on this device only. Playback reports its position from the tick, and a write happens at most every
 * [SaveIntervalMs] while something plays, then again whenever playback stops or moves on. Songs also keep the queue
 * they were played in, so 继续听 can bring that list back. Nothing here reads or spends listening time, and cloud
 * history upload is a separate setting.
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

    /** Every album and song, most recently played first. Emits again after each change. */
    fun records(): Flow<List<ListenRecord>> = version.map { read() }

    /** Called from the playback tick while audible playback reads a position. */
    @MainThread
    fun observe(album: Album, track: Track, positionMs: Long, durationMs: Long) {
        observe(
            ListenRecord(
                albumId = album.id,
                albumTitle = album.title,
                anchor = album.artist,
                coverUrl = album.coverUrl,
                trackId = track.id,
                trackTitle = track.title.ifBlank { album.title },
                durationMs = durationMs.coerceAtLeast(0),
                positionMs = positionMs.coerceAtLeast(0),
                playedAtMs = clock(),
            ),
        )
    }

    /** Called from the music tick while a song plays. [queue] is the saved list it plays from, if any. */
    @MainThread
    fun observeSong(song: MusicItem, queue: SongQueue?, source: MusicPlaySource?, positionMs: Long, durationMs: Long) {
        observe(
            ListenRecord(
                albumId = song.key,
                albumTitle = song.album,
                anchor = song.artist,
                coverUrl = song.artwork,
                trackId = song.key,
                trackTitle = song.title,
                durationMs = durationMs.coerceAtLeast(0),
                positionMs = positionMs.coerceAtLeast(0),
                playedAtMs = clock(),
                kind = ContentKind.Song,
                item = song.raw.toString(),
                source = queue?.source ?: source,
                queue = queue?.token,
            ),
        )
    }

    @MainThread
    private fun observe(record: ListenRecord) {
        pending = record
        val last = lastWriteAt
        if (lastEpisode != episodeKey(record) || last == null || elapsed() - last >= SaveIntervalMs) write()
    }

    /** Writes the newest observation now; called when playback pauses, stops or moves to another episode or song. */
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

    /** Keeps the list a queue was played from, so songs played in it can bring it back. Saving it again updates it. */
    fun saveQueue(queue: SongQueue) {
        val values = ContentValues().apply {
            put("token", queue.token)
            put("source", queue.source?.toJson())
            put("songs", JsonArray(queue.songs.map { it.raw }).toString())
            put("saved_ms", clock())
        }
        writes.launch {
            lock.withLock {
                val db = database.writableDatabase
                db.insertWithOnConflict(QueueTable, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                db.execSQL(
                    "DELETE FROM $QueueTable WHERE token NOT IN " +
                        "(SELECT token FROM $QueueTable ORDER BY saved_ms DESC LIMIT $MaxQueues)",
                )
            }
        }
    }

    /** A saved queue, or null when it was never saved or has been dropped since. */
    suspend fun queue(token: String): SongQueue? = lock.withLock {
        withContext(Dispatchers.IO) {
            database.readableDatabase.rawQuery("SELECT source, songs FROM $QueueTable WHERE token = ?", arrayOf(token))
                .use { cursor ->
                    if (!cursor.moveToFirst()) return@withContext null
                    val songs = songsOf(cursor.getString(1))
                    if (songs.isEmpty()) null else SongQueue(token, MusicPlaySource.fromJson(cursor.getString(0)), songs)
                }
        }
    }

    /**
     * How to play a song row again: the queue it was played in with the song's place in it, or the song alone when that
     * queue is no longer kept or no longer holds it. Null for 听书 rows.
     */
    suspend fun resume(record: ListenRecord): SongResume? {
        val song = record.song ?: return null
        val queue = record.queue?.let { queue(it) }
        val index = queue?.songs?.indexOfFirst { it.isSame(song) } ?: -1
        return if (queue != null && index != -1) {
            SongResume(queue.songs, index, record.positionMs, queue.source, queue)
        } else {
            SongResume(listOf(song), 0, record.positionMs, record.source, null)
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
            put("kind", record.kind.key)
            put("item", record.item)
            put("source", record.source?.toJson())
            put("queue", record.queue)
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
            "SELECT album, album_title, anchor, cover, track, track_title, duration_ms, position_ms, played_ms, kind, item, " +
                "source, queue FROM $Table ORDER BY played_ms DESC, rowid DESC",
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
                            kind = ContentKind.fromKey(cursor.getString(9)),
                            item = cursor.getString(10),
                            source = MusicPlaySource.fromJson(cursor.getString(11)),
                            queue = cursor.getString(12),
                        ),
                    )
                }
            }
        }
    }

    private fun episodeKey(record: ListenRecord) = "${record.albumId}/${record.trackId}"

    private class Database(context: Context) : SQLiteOpenHelper(
        context, File(context.noBackupFilesDir, "listen-history.db").absolutePath, null, 4,
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
                    kind TEXT NOT NULL DEFAULT 'episode',
                    item TEXT,
                    source TEXT,
                    queue TEXT,
                    PRIMARY KEY (album)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX listens_by_time ON listens(played_ms)")
            createQueues(db)
        }

        // Version 1 kept one row per episode. Its rows cannot be read as one per album, so the table is rebuilt.
        // Version 2 rows are all 听书; version 3 adds the content kind and keeps them; version 4 adds where songs were
        // played from, and existing rows are left as they are.
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("DROP TABLE IF EXISTS $Table")
                db.execSQL("DROP TABLE IF EXISTS $QueueTable")
                onCreate(db)
                return
            }
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE $Table ADD COLUMN kind TEXT NOT NULL DEFAULT 'episode'")
                db.execSQL("ALTER TABLE $Table ADD COLUMN item TEXT")
            }
            if (oldVersion < 4) {
                db.execSQL("ALTER TABLE $Table ADD COLUMN source TEXT")
                db.execSQL("ALTER TABLE $Table ADD COLUMN queue TEXT")
                createQueues(db)
            }
        }

        private fun createQueues(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS song_queues (
                    token TEXT NOT NULL PRIMARY KEY,
                    source TEXT,
                    songs TEXT NOT NULL,
                    saved_ms INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    private companion object {
        const val Table = "listens"
        const val QueueTable = "song_queues"
        const val MaxRecords = 5_000
        /** Queues kept for 继续听; songs from older ones come back alone. */
        const val MaxQueues = 50
        const val SaveIntervalMs = 5_000L
    }
}

private fun songOf(text: String?): MusicItem? {
    if (text.isNullOrEmpty()) return null
    return try {
        val raw = Json.parseToJsonElement(text).jsonObject
        MusicItem.from(raw, (raw["platform"] as? JsonPrimitive)?.contentOrNull ?: return null)
    } catch (_: Exception) {
        null
    }
}

private fun songsOf(text: String?): List<MusicItem> {
    if (text.isNullOrEmpty()) return emptyList()
    return try {
        (Json.parseToJsonElement(text) as? JsonArray).orEmpty().mapNotNull { element ->
            val raw = element as? JsonObject ?: return@mapNotNull null
            MusicItem.from(raw, (raw["platform"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null)
        }
    } catch (_: Exception) {
        emptyList()
    }
}
