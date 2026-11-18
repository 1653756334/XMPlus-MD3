package com.xuleo.xmplus.md3.core.media

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.xuleo.xmplus.md3.core.model.XmlyException
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class HistoryObservation(
    val account: String,
    val albumId: String,
    val trackId: String,
    val positionMs: Long,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val direction: Int,
)

internal data class PendingHistoryUpload(val id: Long, val observation: HistoryObservation, val attempted: Boolean)

/** Local-only consent and outbox. The only account field on disk is its existing opaque identity. */
internal class HistoryUploadStore(context: Context) {
    private val helper by lazy { Database(context.applicationContext) }
    private val access = Mutex()

    suspend fun readConsent(): Boolean = onDisk {
        helper.readableDatabase.rawQuery("SELECT enabled FROM consent WHERE id = 1", null).use {
            it.moveToFirst() && it.getInt(0) == 1
        }
    }

    suspend fun saveConsent(enabled: Boolean) = onDisk {
        helper.writableDatabase.execSQL("INSERT OR REPLACE INTO consent (id, enabled) VALUES (1, ?)", arrayOf(if (enabled) 1 else 0))
    }

    suspend fun record(observation: HistoryObservation) = onDisk {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val previous = db.rawQuery(
                "SELECT ended_ms FROM pending WHERE account = ? AND album = ?", arrayOf(observation.account, observation.albumId),
            ).use { if (it.moveToFirst()) it.getLong(0) else null }
            // A late write or another callback for the same observation must not replace an attempted row.
            if (previous == null || observation.endedAtMs > previous) {
                if (previous == null) {
                    val count = db.rawQuery("SELECT COUNT(*) FROM pending WHERE account = ?", arrayOf(observation.account)).use {
                        it.moveToFirst()
                        it.getInt(0)
                    }
                    if (count >= MaxPendingPerAccount) throw XmlyException("history_queue_full")
                }
                val values = ContentValues().apply {
                    put("account", observation.account)
                    put("album", observation.albumId)
                    put("track", observation.trackId)
                    put("position_ms", observation.positionMs)
                    put("started_ms", observation.startedAtMs)
                    put("ended_ms", observation.endedAtMs)
                    put("direction", observation.direction)
                }
                if (db.insertWithOnConflict("pending", null, values, SQLiteDatabase.CONFLICT_REPLACE) == -1L) {
                    throw XmlyException("history_record_failed")
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun pending(account: String): List<PendingHistoryUpload> = onDisk {
        helper.readableDatabase.rawQuery(
            """
            SELECT id, account, album, track, position_ms, started_ms, ended_ms, direction, attempted
            FROM pending WHERE account = ? ORDER BY ended_ms, id
            """.trimIndent(), arrayOf(account),
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.pendingUpload()) } }
    }

    suspend fun markAttempted(id: Long, manual: Boolean): Boolean = onDisk {
        val values = ContentValues().apply { put("attempted", 1) }
        val condition = if (manual) "id = ?" else "id = ? AND attempted = 0"
        helper.writableDatabase.update("pending", values, condition, arrayOf(id.toString())) == 1
    }

    suspend fun acknowledge(id: Long) = onDisk {
        helper.writableDatabase.delete("pending", "id = ?", arrayOf(id.toString()))
    }

    private suspend fun <T> onDisk(operation: () -> T): T = access.withLock { withContext(Dispatchers.IO) { operation() } }

    private fun Cursor.pendingUpload() = PendingHistoryUpload(
        getLong(0),
        HistoryObservation(getString(1), getString(2), getString(3), getLong(4), getLong(5), getLong(6), getInt(7)),
        attempted = getInt(8) != 0,
    )

    private class Database(context: Context) : SQLiteOpenHelper(
        context, File(context.noBackupFilesDir, "history-upload.db").absolutePath, null, 1,
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE consent (id INTEGER PRIMARY KEY, enabled INTEGER NOT NULL)")
            db.execSQL(
                """
                CREATE TABLE pending (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    account TEXT NOT NULL,
                    album TEXT NOT NULL,
                    track TEXT NOT NULL,
                    position_ms INTEGER NOT NULL,
                    started_ms INTEGER NOT NULL,
                    ended_ms INTEGER NOT NULL,
                    direction INTEGER NOT NULL,
                    attempted INTEGER NOT NULL DEFAULT 0,
                    UNIQUE(account, album)
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        const val MaxPendingPerAccount = 256
    }
}
