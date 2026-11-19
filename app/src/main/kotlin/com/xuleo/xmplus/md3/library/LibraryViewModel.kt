package com.xuleo.xmplus.md3.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xuleo.xmplus.md3.XmApplication
import com.xuleo.xmplus.md3.core.account.AccountRepository
import com.xuleo.xmplus.md3.core.media.BookmarkEntry
import com.xuleo.xmplus.md3.core.media.ListenHistoryStore
import com.xuleo.xmplus.md3.core.media.ListenRecord
import com.xuleo.xmplus.md3.core.media.PlaybackBookmarkStore
import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.Track
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.protocol.SubscribedAlbum
import com.xuleo.xmplus.md3.core.protocol.XmlyAnchor
import com.xuleo.xmplus.md3.core.protocol.XmlyHistory
import com.xuleo.xmplus.md3.core.protocol.XmlyLibrary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 听过. Local history and bookmarks are read from this device; the rest come from the primary account and are read
 * one page at a time, as each section is first shown and scrolled.
 */
class LibraryViewModel(
    private val listens: ListenHistoryStore,
    private val bookmarkStore: PlaybackBookmarkStore,
    private val accounts: AccountRepository,
    cloud: XmlyHistory,
    private val library: XmlyLibrary,
    private val anchors: XmlyAnchor,
    private val actions: LibraryActions,
    private val names: BookmarkNames,
) : ViewModel() {
    /** Local history: one row per album, the episode last played from it. Null until the store has been read. */
    val listenHistory: StateFlow<List<ListenRecord>?> = listens.records()
        .stateIn<List<ListenRecord>?>(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMs), null)

    private val bookmarkState = MutableStateFlow<List<BookmarkEntry>?>(null)

    /** Every bookmark on this device, newest first. Null until [loadBookmarks] has read them. */
    val bookmarks: StateFlow<List<BookmarkEntry>?> = bookmarkState

    private val signedIn: suspend () -> Boolean = { accounts.primaryCredentials() != null }

    private val cloudListing = cloud.listing()

    val cloudHistory = ListSection(viewModelScope, signedIn) { cursor ->
        val page = cloudListing.page(credentials(), (cursor ?: FirstPage).toInt())
        ListPage(page.items, page.nextPage?.toLong())
    }

    val updates = ListSection(viewModelScope, signedIn) { cursor ->
        val page = library.updates(credentials(), cursor)
        ListPage(page.items, if (page.hasMore) page.nextBefore else null)
    }

    val subscriptions = ListSection(viewModelScope, signedIn) { cursor ->
        // A page number rides in the cursor, starting from the first page.
        val number = cursor ?: FirstPage
        val page = library.subscriptions(credentials(), number.toInt())
        ListPage(page.items, if (page.hasMore) number + 1 else null)
    }

    val favorites = ListSection(viewModelScope, signedIn) { cursor ->
        val number = cursor ?: FirstPage
        val page = library.favorites(credentials(), number.toInt())
        ListPage(page.tracks, if (page.hasMore) number + 1 else null)
    }

    val followings = ListSection(viewModelScope, signedIn) { cursor ->
        val number = cursor ?: FirstPage
        val page = anchors.followings(credentials().uid, number.toInt())
        ListPage(page.items, if (page.hasMore) number + 1 else null)
    }

    fun loadBookmarks() {
        viewModelScope.launch {
            val entries = bookmarkStore.all()
            bookmarkState.value = entries
            // Bookmarks that still lack names are completed, then the list is read again.
            if (names.complete(entries)) bookmarkState.value = bookmarkStore.all()
        }
    }

    fun removeHistory(record: ListenRecord) {
        viewModelScope.launch { listens.remove(record) }
    }

    /** Puts back a row removed by mistake; the store keeps a newer row for the album if there is one. */
    fun restoreHistory(record: ListenRecord) {
        viewModelScope.launch { listens.restore(record) }
    }

    /** Unsubscribes; the row leaves the list once the server has confirmed it. */
    suspend fun unsubscribe(album: SubscribedAlbum) {
        actions.setSubscribed(album.album.id, subscribed = false)
        subscriptions.remove { it.album.id == album.album.id }
    }

    /** Removes a track from 收藏 once the server has confirmed it. */
    suspend fun unfavorite(track: Track) {
        actions.setFavorite(track.id, track.albumId, favorite = false)
        favorites.remove { it.id == track.id }
    }

    private suspend fun credentials(): Credentials =
        accounts.primaryCredentials() ?: throw XmlyException("login_required", definitive = true)

    companion object {
        private const val StopTimeoutMs = 5_000L
        private const val FirstPage = 1L

        fun factory(app: XmApplication) = viewModelFactory {
            initializer {
                LibraryViewModel(
                    app.listens, app.bookmarks, app.accounts, app.history, app.library, app.anchor, app.libraryActions,
                    app.bookmarkNames,
                )
            }
        }
    }
}