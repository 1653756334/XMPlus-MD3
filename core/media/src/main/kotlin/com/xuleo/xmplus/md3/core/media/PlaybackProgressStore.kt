package com.xuleo.xmplus.md3.core.media

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** [finished] marks an episode that played to its end; its position is then the end and opening it starts over. */
internal data class PlaybackProgress(val positionMs: Long, val observedAtMs: Long, val finished: Boolean = false)

/** Device-local progress, isolated from credentials, quota and the usage journal. */
class PlaybackProgressStore(context: Context) {
    private val appContext = context.applicationContext
    private val access = Mutex()
    private val helper by lazy { Database(appContext) }

    internal suspend fun latest(account: String, track: String): PlaybackProgress? = onDisk(null) {
        helper.readableDatabase.rawQuery(
            "SELECT position_ms, observed_at_ms, finished FROM progress WHERE account = ? AND track = ?",
            arrayOf(account, track),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val position = cursor.getLong(0)
            val observedAt = cursor.getLong(1)
            if (position in 0..MaxPositionMs && observedAt >= 0) PlaybackProgress(position, observedAt, cursor.getInt(2) != 0) else null
        }
    }

    internal suspend fun save(account: String, track: String, progress: PlaybackProgress): Boolean {
        if (progress.positionMs !in 0..MaxPositionMs || progress.observedAtMs < 0) return false
        return onDisk(false) {
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                val values = ContentValues().apply {
                    put("account", account)
                    put("track", track)
                    put("position_ms", progress.positionMs)
                    put("observed_at_ms", progress.observedAtMs)
                    put("finished", if (progress.finished) 1 else 0)
                }
                // REPLACE moves the entry to the end of the per-account insertion order.
                db.insertWithOnConflict("progress", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                    .also { if (it == -1L) throw SQLiteException("Playback progress insert failed") }
                db.execSQL(
                    """
                    DELETE FROM progress WHERE account = ? AND rowid <= (
                        SELECT rowid FROM progress WHERE account = ? ORDER BY rowid DESC LIMIT 1 OFFSET $MaxEntries
                    )
                    """.trimIndent(),
                    arrayOf(account, account),
                )
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            true
        }
    }

    // Acquire on the caller's dispatcher so an immediate reopen waits for the preceding checkpoint.
    private suspend fun <T> onDisk(fallback: T, readOrWrite: () -> T): T = access.withLock {
        withContext(Dispatchers.IO) {
            try {
                readOrWrite()
            } catch (_: SQLiteException) {
                Log.w("PlaybackProgress", "Local playback progress is temporarily unavailable")
                fallback
            }
        }
    }

    private class Database(context: Context) : SQLiteOpenHelper(
        context, File(context.noBackupFilesDir, "playback-progress.db").absolutePath, null, 2,
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE progress (
                    account TEXT NOT NULL,
                    track TEXT NOT NULL,
                    position_ms INTEGER NOT NULL,
                    observed_at_ms INTEGER NOT NULL,
                    finished INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (account, track)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX progress_by_account ON progress(account)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) db.execSQL("ALTER TABLE progress ADD COLUMN finished INTEGER NOT NULL DEFAULT 0")
        }
    }

    private companion object {
        const val MaxEntries = 5_000
        const val MaxPositionMs = Int.MAX_VALUE * 1_000L
    }
}
