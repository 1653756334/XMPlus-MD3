package com.xuleo.xmplus.md3.core.media

import com.xuleo.xmplus.md3.core.model.AlbumDetail
import com.xuleo.xmplus.md3.core.model.Page
import com.xuleo.xmplus.md3.core.model.Track
import com.xuleo.xmplus.md3.core.model.XmlyException

/** What the player can display; page cursors and catalogue validation stay in the controller. */
data class PlaybackQueueState(
    val items: List<Track> = emptyList(),
    val currentTrackId: String? = null,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = true,
    val shuffle: Boolean = false,
    val isLoading: Boolean = false,
    val errorCode: String? = null,
)

internal const val PlaybackAlbumPageSize = 200
private const val MaxAlbumEpisodes = 5_000

/** A contiguous window of officially numbered pages, not necessarily contiguous in playable episodes. */
internal class AlbumPlaybackQueue private constructor(
    val detail: AlbumDetail,
    val items: List<Track>,
    val firstPage: Int,
    val lastPage: Int,
    private val total: Int,
) {
    val hasEarlierPage: Boolean get() = firstPage > 1
    val hasLaterPage: Boolean get() = lastPage * PlaybackAlbumPageSize < total
    val refreshTailFirst: Boolean get() = !hasLaterPage || items.last().officialPosition != lastPage * PlaybackAlbumPageSize

    /** Unavailable edge slots may become playable between two checks. Revisit them from the actual anchor. */
    fun recheckEdge(forward: Boolean): AlbumPlaybackQueue = AlbumPlaybackQueue(
        detail,
        items,
        if (forward) firstPage else pageFor(items.first()),
        if (forward) pageFor(items.last()) else lastPage,
        total,
    )

    fun neighbor(trackId: String, forward: Boolean): Track? {
        val index = items.indexOfFirst { it.id == trackId }
        return if (index == -1) null else items.getOrNull(index + if (forward) 1 else -1)
    }

    /** Next stays enabled while an episode follows in this window or a later page may hold one. */
    fun hasNext(trackId: String): Boolean = neighbor(trackId, forward = true) != null || hasLaterPage

    fun refreshHead(result: Page<Track>): AlbumPlaybackQueue {
        val headPage = pageFor(items.first())
        val rows = checkedRows(detail.album.id, headPage, result)
        val index = rows.indexOfFirst { it.id == items.first().id }
        val oldHead = items.takeWhile { it.officialPosition!! <= headPage * PlaybackAlbumPageSize }
        if (result.total < items.last().officialPosition!! || index < 0 || rows.size - index < oldHead.size || oldHead.indices.any { offset ->
                !sameSlot(oldHead[offset], rows[index + offset])
            }
        ) changed()
        val prepended = rows.take(index) + items
        checkUnique(prepended)
        return AlbumPlaybackQueue(detail, prepended, firstPage, lastPage, result.total)
    }

    fun merge(expectedPage: Int, result: Page<Track>): AlbumPlaybackQueue {
        val rows = checkedRows(detail.album.id, expectedPage, result)
        val next = when (expectedPage) {
            firstPage - 1 -> {
                if (result.total != total) changed()
                rows + items
            }
            lastPage -> {
                val offset = (lastPage - 1) * PlaybackAlbumPageSize
                val prefix = items.takeWhile { it.officialPosition!! <= offset }
                val oldTail = items.drop(prefix.size)
                // A growing tail must still contain every retained row at exactly the same position.
                if (result.total < items.last().officialPosition!! || rows.size < oldTail.size || oldTail.indices.any { index ->
                        !sameSlot(oldTail[index], rows[index])
                    }
                ) changed()
                prefix + rows
            }
            lastPage + 1 -> {
                if (result.total < items.last().officialPosition!!) changed()
                items + rows
            }
            else -> changed()
        }
        checkUnique(next)
        return AlbumPlaybackQueue(detail, next, minOf(firstPage, expectedPage), maxOf(lastPage, expectedPage), result.total)
    }

    companion object {
        fun pageFor(track: Track): Int {
            val position = track.officialPosition ?: track.orderNo ?: 1
            if (position !in 1..MaxAlbumEpisodes) throw XmlyException("catalogue_limit")
            return (position - 1) / PlaybackAlbumPageSize + 1
        }

        fun start(detail: AlbumDetail, track: Track, page: Int, result: Page<Track>): AlbumPlaybackQueue {
            val rows = checkedRows(detail.album.id, page, result)
            val selected = rows.firstOrNull { it.id == track.id } ?: throw XmlyException("missing_track")
            if ((track.albumId.isNotBlank() && track.albumId != detail.album.id) ||
                (track.officialPosition != null && track.officialPosition != selected.officialPosition)
            ) changed()
            return AlbumPlaybackQueue(detail, rows, page, page, result.total)
        }

        private fun checkedRows(albumId: String, page: Int, result: Page<Track>): List<Track> {
            if (result.total !in 0..MaxAlbumEpisodes || page !in 1..(MaxAlbumEpisodes / PlaybackAlbumPageSize)) {
                throw XmlyException("catalogue_limit")
            }
            if (result.page != page || result.pageSize != PlaybackAlbumPageSize) changed()
            var previous = (page - 1) * PlaybackAlbumPageSize
            val end = minOf(page * PlaybackAlbumPageSize, result.total)
            val ids = HashSet<String>()
            return result.items.map { track ->
                val position = track.officialPosition ?: changed()
                if (position <= previous || position > end || track.orderNo != position ||
                    (track.albumId.isNotEmpty() && track.albumId != albumId) || !ids.add(track.id)
                ) changed()
                previous = position
                if (track.albumId.isEmpty()) track.copy(albumId = albumId) else track
            }
        }

        private fun changed(): Nothing = throw XmlyException("catalogue_changed")

        private fun sameSlot(a: Track, b: Track) = a.id == b.id && a.officialPosition == b.officialPosition

        private fun checkUnique(tracks: List<Track>) {
            val ids = HashSet<String>()
            if (tracks.any { !ids.add(it.id) }) throw XmlyException("duplicate_page")
        }
    }
}
