package com.xuleo.xmplus.md3.library

import com.xuleo.xmplus.md3.core.media.BookmarkEntry
import com.xuleo.xmplus.md3.core.media.BookmarkSource
import com.xuleo.xmplus.md3.core.media.PlaybackBookmarkStore
import com.xuleo.xmplus.md3.core.protocol.XmlyAlbum
import com.xuleo.xmplus.md3.core.protocol.XmlyPlayback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Bookmarks made before their names and cover were kept have none. Each episode is looked up in the public catalogue at
 * most once per launch, and a found name is written back, so the list needs no request for it after that. A lookup that
 * fails is not repeated until the next launch.
 */
class BookmarkNames(
    private val store: PlaybackBookmarkStore,
    private val catalogue: XmlyAlbum,
    private val playbackApi: XmlyPlayback,
) {
    private val lock = Mutex()
    private val looked = HashSet<String>()

    /** Writes the names that are missing and returns whether any were written. */
    suspend fun complete(entries: List<BookmarkEntry>): Boolean = lock.withLock {
        // Each episode is marked as looked up the moment it is queued, so a failed lookup is not repeated in this launch.
        val missing = entries.filter { !it.named }.filter { looked.add("${it.albumId}/${it.trackId}") }
        var written = false
        for ((albumId, rows) in missing.groupBy { it.albumId }) {
            val detail = try {
                catalogue.detail(albumId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                continue
            }
            for (trackId in rows.map { it.trackId }.distinct()) {
                val track = try {
                    playbackApi.trackMetadata(trackId, null)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    continue
                }
                store.completeSource(
                    albumId, trackId,
                    BookmarkSource(
                        albumTitle = detail.album.title,
                        trackTitle = track.title.ifBlank { detail.album.title },
                        anchor = detail.album.artist,
                        coverUrl = detail.album.coverUrl ?: track.coverUrl,
                    ),
                )
                written = true
            }
        }
        written
    }
}