package com.xuleo.xmplus.md3.library

import com.xuleo.xmplus.md3.core.account.AccountRepository
import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.protocol.LibraryIdsPage
import com.xuleo.xmplus.md3.core.protocol.XmlyLibrary
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 收藏, 订阅 and 关注 for the primary account. A membership list is read one page at a time: a check reads only as far as
 * it needs to show whether the item is there, and the pages it read stay for the next check. A change the server has
 * confirmed is kept on top of the pages, so a page read later cannot undo it.
 */
class LibraryActions(private val accounts: AccountRepository, private val library: XmlyLibrary) {
    private var favorites: Membership? = null
    private var subscriptions: Membership? = null

    suspend fun isFavorite(trackId: String): Boolean {
        val credentials = signedIn()
        return favoritesFor(credentials).has(trackId)
    }

    /** Records the change once the server has confirmed it. */
    suspend fun setFavorite(trackId: String, albumId: String, favorite: Boolean) {
        val credentials = signedIn()
        library.setFavorite(credentials, trackId, albumId, favorite)
        favoritesFor(credentials).record(trackId, favorite)
    }

    suspend fun isSubscribed(albumId: String): Boolean {
        val credentials = signedIn()
        return subscriptionsFor(credentials).has(albumId)
    }

    /** Records the change once the server has confirmed it. */
    suspend fun setSubscribed(albumId: String, subscribed: Boolean) {
        val credentials = signedIn()
        library.setSubscribed(credentials, albumId, subscribed)
        subscriptionsFor(credentials).record(albumId, subscribed)
    }

    suspend fun isFollowing(uid: String): Boolean = library.isFollowing(signedIn(), uid)

    suspend fun setFollowing(uid: String, following: Boolean) {
        library.setFollowing(signedIn(), uid, following)
    }

    private suspend fun signedIn(): Credentials =
        accounts.primaryCredentials() ?: throw XmlyException("login_required", definitive = true)

    private fun favoritesFor(credentials: Credentials): Membership =
        favorites?.takeIf { it.account == credentials.identity }
            ?: Membership(credentials.identity, FavoriteScanLimit) { library.favoriteIdPage(credentials, it) }.also { favorites = it }

    private fun subscriptionsFor(credentials: Credentials): Membership =
        subscriptions?.takeIf { it.account == credentials.identity }
            ?: Membership(credentials.identity) { library.subscriptionIdPage(credentials, it) }.also { subscriptions = it }
}

/** Most favourites a check reads, in the order the server lists them. A favourite past this counts as not favourited. */
private const val FavoriteScanLimit = 200

/**
 * One membership list of one account. [readPage] gives the pages in order, and the reads are serialised so two checks
 * never read the same page twice. [answers] holds the changes the server has confirmed; they win over what the pages say.
 * With a [limit], only that many items are read; a track past the limit counts as not in the list.
 */
private class Membership(val account: String, private val limit: Int? = null, private val readPage: suspend (Int) -> LibraryIdsPage) {
    private val lock = Mutex()
    private val seen = HashSet<String>()
    private val answers = ConcurrentHashMap<String, Boolean>()
    private var nextPage = FirstPage
    private var ended = false
    private var capped = false

    /** Whether [id] is in the list. Pages are read until one answers it, or the list ends, or the limit is reached. */
    suspend fun has(id: String): Boolean = lock.withLock {
        while (answers[id] == null && id !in seen && !ended && !capped) readNext()
        answers[id] ?: (id in seen)
    }

    /** Keeps a change the server has confirmed. */
    fun record(id: String, member: Boolean) {
        answers[id] = member
    }

    private suspend fun readNext() {
        if (nextPage > MaxPages) throw XmlyException("page_limit")
        val result = readPage(nextPage)
        // A page with nothing new, while the list says more follows, means the server is repeating itself.
        if ((result.ids.isNotEmpty() || result.hasMore) && result.ids.all { it in seen }) throw XmlyException("repeated_page")
        val room = limit?.let { (it - seen.size).coerceAtLeast(0) } ?: result.ids.size
        val taken = result.ids.take(room)
        seen += taken
        if (taken.size < result.ids.size || (result.hasMore && limit != null && seen.size >= limit)) capped = true
        else ended = !result.hasMore
        nextPage++
    }

    private companion object {
        const val FirstPage = 1
        const val MaxPages = 10_000
    }
}
