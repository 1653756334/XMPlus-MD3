package com.xuleo.xmplus.md3.core.download

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import com.xuleo.xmplus.md3.core.model.ContentKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** The download list on this device, kept as one JSON file. */
class DownloadStore(private val file: File) {
    /**
     * The saved tasks. A file that cannot be read is moved aside as `.corrupt`, so its receipts stay on disk, and the
     * list starts empty.
     */
    fun load(): List<DownloadTask> {
        if (!file.isFile) return emptyList()
        return try {
            val root = Json.parseToJsonElement(file.readText()) as JsonObject
            (root.getValue("tasks") as JsonArray).map { (it as JsonObject).toTask() }
        } catch (_: Exception) {
            file.renameTo(File(file.path + ".corrupt"))
            emptyList()
        }
    }

    /**
     * Writes the whole list through a temporary file. A failed write is not thrown, since it runs in the background:
     * the list stays in memory and the next change writes it again.
     */
    fun save(tasks: Collection<DownloadTask>) {
        val text = buildJsonObject {
            put("version", 1)
            putJsonArray("tasks") { tasks.forEach { add(it.toJson()) } }
        }.toString()
        val temp = File(file.path + ".tmp")
        try {
            file.parentFile?.mkdirs()
            temp.writeText(text)
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (_: IOException) {
            // Kept in memory; the next change writes the list again.
        }
    }

    private fun DownloadTask.toJson() = buildJsonObject {
        put("trackId", trackId)
        put("taskId", taskId)
        put("albumId", albumId)
        put("albumTitle", albumTitle)
        put("title", title)
        put("index", index)
        put("albumTotal", albumTotal)
        put("durationSeconds", durationSeconds)
        put("state", state.key)
        put("bytes", bytes)
        put("totalBytes", totalBytes)
        put("file", file)
        put("accountIdentity", accountIdentity)
        put("receiptId", receiptId)
        put("error", error)
        put("createdAt", createdAt)
        put("coverUrl", coverUrl)
        put("confirmedSeconds", confirmedSeconds)
        put("kind", kind.key)
        put("quality", quality)
        put("item", item)
    }

    private fun JsonObject.toTask(): DownloadTask {
        fun text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
        fun required(key: String): String = text(key) ?: throw IllegalStateException("missing $key")
        return DownloadTask(
            trackId = required("trackId"),
            taskId = required("taskId"),
            albumId = required("albumId"),
            albumTitle = text("albumTitle").orEmpty(),
            title = text("title").orEmpty(),
            index = text("index")?.toIntOrNull(),
            albumTotal = text("albumTotal")?.toIntOrNull() ?: 0,
            durationSeconds = text("durationSeconds")?.toDoubleOrNull() ?: 0.0,
            state = DownloadState.fromKey(text("state")) ?: throw IllegalStateException("missing state"),
            bytes = text("bytes")?.toLongOrNull() ?: 0L,
            totalBytes = text("totalBytes")?.toLongOrNull(),
            file = text("file"),
            accountIdentity = text("accountIdentity"),
            receiptId = text("receiptId"),
            error = text("error"),
            createdAt = text("createdAt")?.toLongOrNull() ?: 0L,
            coverUrl = text("coverUrl"),
            confirmedSeconds = text("confirmedSeconds")?.toIntOrNull(),
            kind = ContentKind.fromKey(text("kind")),
            quality = text("quality"),
            item = text("item"),
        )
    }
}