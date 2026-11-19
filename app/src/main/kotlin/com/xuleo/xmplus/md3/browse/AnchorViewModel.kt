package com.xuleo.xmplus.md3.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xuleo.xmplus.md3.core.account.AccountRepository
import com.xuleo.xmplus.md3.core.model.AnchorAlbum
import com.xuleo.xmplus.md3.core.model.AnchorContentPage
import com.xuleo.xmplus.md3.core.model.AnchorProfile
import com.xuleo.xmplus.md3.core.model.ReadPolicy
import com.xuleo.xmplus.md3.core.model.Track
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.protocol.XmlyAnchor
import com.xuleo.xmplus.md3.library.LibraryActions
import com.xuleo.xmplus.md3.library.LibraryToggle
import com.xuleo.xmplus.md3.ui.AppRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class AnchorHeader(
    val uid: String? = null,
    val profile: AnchorProfile? = null,
    val loading: Boolean = true,
    val errorCode: String? = null,
    val follow: LibraryToggle = LibraryToggle(),
)

internal data class AnchorListingState<T>(
    val items: List<T> = emptyList(),
    val next: Int? = null,
    val total: Int? = null,
    val reportedTotal: Long? = null,
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val keyword: String? = null,
    val errorCode: String? = null,
)

/** One list on the anchor page. Pages are read as the list is scrolled; a refresh starts again from page one. */
internal class AnchorListing<T>(
    private val scope: CoroutineScope,
    private val uid: () -> String?,
    private val itemId: (T) -> String,
    private val fetchPage: suspend (String, Int, String?) -> AnchorContentPage<T>,
) {
    private val mutable = MutableStateFlow(AnchorListingState<T>())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var requestedPage = 1

    fun ensureLoaded() {
        if (state.value.let { !it.loaded && !it.loading && it.errorCode == null }) load(1)
    }

    /** Asks for the page after the last one shown, unless the list has ended or a read is running. */
    fun loadMore() {
        val current = state.value
        val next = current.next ?: return
        if (current.loading) return
        load(next)
    }

    fun retry() = load(requestedPage)

    fun refresh() = load(1, refreshing = state.value.loaded)

    /** Shows the works matching [keyword] from page one; a blank keyword shows the whole list again. */
    fun search(keyword: String?) {
        val next = keyword?.trim()?.takeIf(String::isNotEmpty)
        if (next == state.value.keyword) return
        job?.cancel()
        mutable.value = AnchorListingState(keyword = next)
        load(1)
    }

    private fun load(page: Int, refreshing: Boolean = false) {
        val anchor = uid() ?: return
        val keyword = state.value.keyword
        job?.cancel()
        requestedPage = page
        mutable.update { it.copy(loading = true, refreshing = refreshing, errorCode = null) }
        job = scope.launch {
            try {
                val result = withContext(Dispatchers.Default + AnchorReadPolicy) { fetchPage(anchor, page, keyword) }
                ensureActive()
                publish(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                ensureActive()
                mutable.update { it.copy(loading = false, refreshing = false, errorCode = codeOf(failure)) }
            }
        }
    }

    /** A later page can repeat a row the server has moved, so rows already shown are skipped rather than shown twice. */
    private fun publish(result: AnchorContentPage<T>) {
        val ids = result.items.map(itemId)
        if (ids.toSet().size != ids.size) throw XmlyException("repeated_page")
        mutable.update { current ->
            val items = if (result.page == 1) result.items else {
                val known = current.items.mapTo(HashSet(), itemId)
                current.items + result.items.filter { known.add(itemId(it)) }
            }
            current.copy(
                items = items, next = if (result.hasMore) result.page + 1 else null,
                total = result.total, reportedTotal = result.reportedTotal,
                loaded = true, loading = false, refreshing = false,
            )
        }
    }
}

private val AnchorReadPolicy = ReadPolicy(rateLimitRetries = 0)

internal class AnchorViewModel(
    private val route: AppRoute.AnchorPage,
    private val api: XmlyAnchor,
    private val accounts: AccountRepository,
    private val library: LibraryActions,
) : ViewModel() {
    private val header = MutableStateFlow(AnchorHeader(uid = route.id.takeUnless { route.resolveFromAlbum }))
    val state = header.asStateFlow()
    private var started = false
    private var profileJob: Job? = null

    val albums = AnchorListing<AnchorAlbum>(
        viewModelScope, { header.value.uid }, itemId = { it.album.id },
        fetchPage = { uid, page, keyword ->
            if (keyword == null) api.albums(uid, accounts.primaryCredentials(), page) else api.searchAlbums(uid, keyword, page)
        },
    )
    val tracks = AnchorListing<Track>(
        viewModelScope, { header.value.uid }, itemId = Track::id,
        fetchPage = { uid, page, _ -> api.tracks(uid, page) },
    )

    fun start() {
        if (started) return
        started = true
        retryProfile()
    }

    fun retryProfile() {
        profileJob?.cancel()
        header.update { it.copy(loading = true, errorCode = null) }
        profileJob = viewModelScope.launch {
            try {
                val uid = header.value.uid ?: withContext(Dispatchers.Default + AnchorReadPolicy) { api.albumAnchor(route.id) }
                header.update { it.copy(uid = uid) }
                val profile = withContext(Dispatchers.Default + AnchorReadPolicy) { api.profile(uid, accounts.primaryCredentials()) }
                header.update { it.copy(profile = profile, loading = false) }
                loadFollow(profile.uid)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                ensureActive()
                header.update { it.copy(loading = false, errorCode = codeOf(failure)) }
            }
        }
    }

    /** 关注 shows only for a signed-in account, and never on the account's own page. */
    private fun loadFollow(uid: String) {
        viewModelScope.launch {
            val credentials = accounts.primaryCredentials() ?: return@launch
            if (uid == credentials.uid) return@launch
            header.update { it.copy(follow = LibraryToggle(signedIn = true)) }
            try {
                val following = library.isFollowing(uid)
                header.update { it.copy(follow = it.follow.copy(on = following)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                header.update { it.copy(follow = it.follow.copy(failed = true)) }
            }
        }
    }

    fun toggleFollow() {
        val uid = header.value.uid ?: return
        val current = header.value.follow
        val following = current.on ?: return
        if (current.busy) return
        header.update { it.copy(follow = it.follow.copy(busy = true, failed = false)) }
        viewModelScope.launch {
            try {
                library.setFollowing(uid, !following)
                header.update { it.copy(follow = it.follow.copy(on = !following, busy = false)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                header.update { it.copy(follow = it.follow.copy(busy = false, failed = true)) }
            }
        }
    }

    companion object {
        fun factory(route: AppRoute.AnchorPage, api: XmlyAnchor, accounts: AccountRepository, library: LibraryActions): ViewModelProvider.Factory =
            viewModelFactory { initializer { AnchorViewModel(route, api, accounts, library) } }
    }
}

