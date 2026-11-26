package com.xuleo.xmplus.md3.download

import com.xuleo.xmplus.md3.core.account.AccountRepository
import com.xuleo.xmplus.md3.core.account.UsageLedger
import com.xuleo.xmplus.md3.core.download.DownloadManager
import com.xuleo.xmplus.md3.core.download.DownloadRequest
import com.xuleo.xmplus.md3.core.download.DownloadState
import com.xuleo.xmplus.md3.core.download.DownloadTask
import com.xuleo.xmplus.md3.core.media.MusicQuality
import com.xuleo.xmplus.md3.core.model.Album
import com.xuleo.xmplus.md3.core.model.AlbumDetail
import com.xuleo.xmplus.md3.core.model.MusicItem
import com.xuleo.xmplus.md3.core.model.Track
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.protocol.AlbumEpisodePageSize
import com.xuleo.xmplus.md3.core.protocol.XmlyAlbum
import com.xuleo.xmplus.md3.core.protocol.XmlyPlayback
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** One episode of a [DownloadPlan] and the listening time it would cost; 0 when it is free. */
data class PlannedEpisode(val track: Track, val seconds: Int)

/** What a download would queue and charge, shown to the listener before anything starts. */
data class DownloadPlan(
    val album: Album,
    val albumTotal: Int,
    val episodes: List<PlannedEpisode>,
    /** Episodes left out because they are already downloading or downloaded. */
    val skipped: Int,
    /** The account's balance when the cost was worked out; null when nothing costs listening time. */
    val balanceSeconds: Long?,
) {
    val totalSeconds: Long get() = episodes.sumOf { it.seconds.toLong() }
    val paidCount: Int get() = episodes.count { it.seconds > 0 }
    val affordable: Boolean get() = balanceSeconds == null || totalSeconds <= balanceSeconds
}

/**
 * Starts downloads from the screens. Episodes are priced here so the listener can see the cost first; queueing,
 * paying and transferring is the manager's job.
 */
class DownloadActions(
    private val accounts: AccountRepository,
    private val album: XmlyAlbum,
    private val playback: XmlyPlayback,
    private val ledger: UsageLedger,
    private val downloads: DownloadManager,
) {
    val tasks: StateFlow<List<DownloadTask>> get() = downloads.list

    /** Songs only, in the order they were queued; 已下载 lists them newest first. */
    val songs: StateFlow<List<DownloadTask>> get() = downloads.songs

    /**
     * Queues a song at [quality], or at the download quality setting when it is null, and returns whether it was added.
     * A song already queued or downloaded is left alone. Nothing is charged for a song.
     */
    suspend fun downloadSong(song: MusicItem, quality: MusicQuality? = null): Boolean = downloads.enqueueSong(song, quality?.key)

    /** A song's task, whatever its state; null when it was never queued. */
    fun songTask(song: MusicItem): DownloadTask? = downloads.songTask(song)

    /** The finished file of a downloaded song, for playing it offline. */
    fun songFile(song: MusicItem): java.io.File? = downloads.completedSongFile(song)

    /**
     * Prices [tracks] for the preview. The list's paid badge only decides which episodes are looked up: one marked free
     * is taken as free, and if the server charges for it after all, its download stops and asks to be started again
     * rather than charging what the listener never saw.
     */
    suspend fun plan(
        album: Album,
        albumTotal: Int,
        tracks: List<Track>,
        onProgress: (checked: Int, total: Int) -> Unit = { _, _ -> },
    ): DownloadPlan = withContext(Dispatchers.Default) {
        if (tracks.size > MaxEpisodes) throw XmlyException("album_too_large", definitive = true)
        val known = downloads.list.value
        val listed = known.filter { it.state != DownloadState.Failed }.mapTo(HashSet()) { it.trackId }
        // A failed episode that was already charged keeps its receipt, so trying it again costs nothing.
        val charged = known.filter { it.receiptId != null }.mapTo(HashSet()) { it.trackId }
        val wanted = tracks.distinctBy { it.id }.filter { it.id !in listed }
        val credentials = accounts.primaryCredentials()
        val lookups = wanted.filter { it.paid != false && it.id !in charged }
        val checked = AtomicInteger()
        onProgress(0, lookups.size)
        val gate = Semaphore(LookupConcurrency)
        val costing = lookups.map { track ->
            async {
                gate.withPermit {
                    val paid = playback.requiresListeningTime(track.id, credentials)
                    onProgress(checked.incrementAndGet(), lookups.size)
                    track.id.takeIf { paid }
                }
            }
        }.awaitAll().filterNotNullTo(HashSet())
        val episodes = wanted.map { track ->
            PlannedEpisode(track, if (track.id in costing) ceil(track.durationSeconds.coerceAtLeast(0.0)).toInt() else 0)
        }
        val balance = if (costing.isEmpty()) null else {
            val identity = credentials?.identity ?: throw XmlyException("login_required", definitive = true)
            ledger.queryQuota(identity).remainingSeconds
        }
        val total = maxOf(albumTotal, tracks.maxOfOrNull { it.officialPosition ?: 0 } ?: 0)
        DownloadPlan(album, total, episodes, tracks.size - wanted.size, balance)
    }

    /** Queues a plan the listener agreed to and returns how many episodes were added. */
    suspend fun start(plan: DownloadPlan): Int = downloads.enqueue(
        plan.album.id, plan.album.title, plan.album.coverUrl, plan.albumTotal,
        plan.episodes.map { DownloadRequest(it.track, it.seconds.takeIf { seconds -> seconds > 0 }) },
    )

    /**
     * The album's episodes numbered [numbers], in that order, reading only the pages they are on (DL-04, DL-05). A
     * number the album lacks, or one two episodes share, fails the whole request.
     */
    suspend fun episodesNumbered(detail: AlbumDetail, numbers: List<Int>): List<Track> = withContext(Dispatchers.Default) {
        val credentials = accounts.primaryCredentials()
        val wanted = numbers.toHashSet()
        val found = HashMap<Int, Track>()
        for (page in numbers.map { (it - 1) / AlbumEpisodePageSize + 1 }.distinct()) {
            val result = album.episodePage(detail.album.id, page, ascending = true, recordDescending = detail.recordDescending, credentials = credentials)
            for (track in result.items) {
                val number = track.officialPosition ?: continue
                if (number !in wanted) continue
                val earlier = found.put(number, track)
                if (earlier != null && earlier.id != track.id) throw XmlyException("range_ambiguous", definitive = true)
            }
        }
        numbers.map { found[it] ?: throw XmlyException("range_missing", definitive = true) }
    }

    suspend fun pause(trackId: String) = downloads.pause(trackId)

    suspend fun resume(trackId: String) = downloads.resume(trackId)

    private companion object {
        const val MaxEpisodes = 5000
        /** Lookups in flight at once while pricing, kept low so a large selection does not trip the rate limit. */
        const val LookupConcurrency = 4
    }
}
