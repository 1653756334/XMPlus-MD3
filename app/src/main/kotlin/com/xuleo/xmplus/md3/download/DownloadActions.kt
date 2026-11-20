package com.xuleo.xmplus.md3.download

import com.xuleo.xmplus.md3.core.account.AccountRepository
import com.xuleo.xmplus.md3.core.download.DownloadManager
import com.xuleo.xmplus.md3.core.media.PlaybackSelection
import com.xuleo.xmplus.md3.core.model.AlbumDetail
import com.xuleo.xmplus.md3.core.model.Track
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.protocol.XmlyAlbum

/** What a download start reports. A null [errorCode] means the episodes were queued. */
data class DownloadNotice(val errorCode: String?)

/** Starts downloads from the screens. Reading the episodes is done here; queueing, paying and transferring is the manager's job. */
class DownloadActions(
    private val accounts: AccountRepository,
    private val album: XmlyAlbum,
    private val downloads: DownloadManager,
) {
    suspend fun downloadEpisode(selection: PlaybackSelection) {
        val track = selection.track
        val total = maxOf(selection.album.trackCount, track.officialPosition ?: 0)
        downloads.enqueue(selection.album.id, selection.album.title, total, listOf(track))
    }

    /** Every episode of the album, oldest first, one page at a time. An album over [MaxEpisodes] is refused. */
    suspend fun downloadAlbum(detail: AlbumDetail) {
        val credentials = accounts.primaryCredentials()
        val tracks = mutableListOf<Track>()
        var page = 1
        while (true) {
            val result = album.episodePage(
                detail.album.id, page, ascending = true, recordDescending = detail.recordDescending, credentials = credentials,
            )
            tracks += result.items
            if (tracks.size > MaxEpisodes) throw XmlyException("album_too_large", definitive = true)
            if (result.items.isEmpty() || !result.hasMore) break
            page++
        }
        downloads.enqueue(detail.album.id, detail.album.title, maxOf(detail.album.trackCount, tracks.size), tracks)
    }

    private companion object {
        const val MaxEpisodes = 5000
    }
}