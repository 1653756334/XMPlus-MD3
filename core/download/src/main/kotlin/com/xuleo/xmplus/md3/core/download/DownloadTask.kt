package com.xuleo.xmplus.md3.core.download

/** Where one episode is in its download. */
enum class DownloadState(val key: String) {
    Queued("queued"), Running("running"), Paused("paused"), Completed("completed"), Failed("failed");

    companion object {
        fun fromKey(key: String?): DownloadState? = entries.firstOrNull { it.key == key }
    }
}

/**
 * One episode's download. [trackId] identifies it; [taskId] is the key the listening-time ledger charges under, so it
 * stays the same across retries and an episode is never paid for twice.
 */
data class DownloadTask(
    val trackId: String,
    val taskId: String,
    val albumId: String,
    val albumTitle: String,
    val title: String,
    /** The official episode number, which names the file. Null when the server gave none. */
    val index: Int?,
    val albumTotal: Int,
    val durationSeconds: Double,
    val state: DownloadState,
    /** Bytes on disk so far. While running this is updated in memory only. */
    val bytes: Long = 0,
    val totalBytes: Long? = null,
    /** Relative to the download folder; set when the download completes. */
    val file: String? = null,
    /** The account whose listening time paid for this episode, when it had to be paid for. */
    val accountIdentity: String? = null,
    val receiptId: String? = null,
    /** A stable error code, shown to the user as text. */
    val error: String? = null,
    val createdAt: Long,
)