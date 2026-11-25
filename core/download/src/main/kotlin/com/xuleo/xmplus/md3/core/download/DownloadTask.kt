package com.xuleo.xmplus.md3.core.download

import com.xuleo.xmplus.md3.core.model.ContentKind
import com.xuleo.xmplus.md3.core.model.Track

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
    /** The album's cover, for the download list. */
    val coverUrl: String? = null,
    /**
     * The listening time the listener agreed to pay for this episode before it was queued. Null means it was shown as
     * free: if the server asks for listening time after all, the download stops instead of charging.
     */
    val confirmedSeconds: Int? = null,
    /** Songs (from M5) never use listening time: [accountIdentity], [receiptId] and [confirmedSeconds] stay null. */
    val kind: ContentKind = ContentKind.Episode,
)

/** One episode to queue, with the listening time agreed for it; null when it was shown as free. */
data class DownloadRequest(val track: Track, val confirmedSeconds: Int?)