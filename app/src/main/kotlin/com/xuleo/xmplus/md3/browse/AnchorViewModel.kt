package com.xuleo.xmplus.md3.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
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

internal enum class AnchorOrder { Default, Newest, Oldest, MostPlayed }

internal data class AnchorHeader(
    val uid: String? = null,
    val profile: AnchorProfile? = null,
    val loading: Boolean = true,
    val errorCode: String? = null,
)

internal data class AnchorListingState<T>(
    val order: AnchorOrder = AnchorOrder.Default,
    val content: AnchorContentPage<T>? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val errorCode: String? = null,
)

/** Holds one list's position and sort independently of the other tab. */
internal class AnchorListing<T>(
    private val scope: CoroutineScope,
    private val uid: () -> String?,
    initialPageSize: Int,
    private val itemId: (T) -> String,
    private val plays: (T) -> Long?,
    private val date: (T) -> Long?,
    private val fetchPage: suspend (String, Int) -> AnchorContentPage<T>,
    private val fetchCatalogue: suspend (String) -> AnchorContentPage<T>,
    private val fetchDates: suspend (String, List<T>) -> List<T> = { _, items -> items },
) {
    private val mutable = MutableStateFlow(AnchorListingState<T>())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var requestedPage = 1
    private var pageSize = initialPageSize
    private var catalogue: AnchorContentPage<T>? = null
    private var orderedCatalogue: Pair<AnchorOrder, List<T>>? = null

    fun ensureLoaded() {
        if (state.value.let { it.content == null && !it.loading && it.errorCode == null }) load(1)
    }

    fun select(order: AnchorOrder) {
        if (state.value.order == order) return
        mutable.value = AnchorListingState(order = order)
        load(1)
    }

    fun goTo(page: Int) {
        val current = state.value
        val shown = current.content ?: return
        if (current.loading || page < 1 || page == shown.page) return
        if (shown.total != null && page > maxPage(shown)) return
        if (shown.total == null && (page > shown.page + 1 || (page > shown.page && !shown.hasMore))) return
        load(page)
    }

    fun retry() = load(requestedPage)

    fun refresh() {
        catalogue = null
        orderedCatalogue = null
        load(1, refreshing = state.value.content != null)
    }

    private fun load(page: Int, refreshing: Boolean = false) {
        val anchor = uid() ?: return
        val order = state.value.order
        val cached = catalogue
        val cachedOrder = orderedCatalogue
        val size = pageSize
        job?.cancel()
        requestedPage = page
        mutable.update { it.copy(loading = true, refreshing = refreshing, errorCode = null) }
        job = scope.launch {
            try {
                if (order == AnchorOrder.Default) {
                    val result = withContext(Dispatchers.Default + AnchorReadPolicy) { fetchPage(anchor, page) }
                    ensureActive()
                    if (result.items.map(itemId).toSet().size != result.items.size) throw XmlyException("repeated_page")
                    if (catalogue?.reportedTotal != result.reportedTotal) {
                        catalogue = null
                        orderedCatalogue = null
                    }
                    pageSize = result.pageSize
                    publish(result)
                } else {
                    val (source, items) = withContext(Dispatchers.Default + AnchorReadPolicy) {
                        var source = cached ?: fetchCatalogue(anchor)
                        if ((order == AnchorOrder.Newest || order == AnchorOrder.Oldest) && source.items.any { date(it) == null }) {
                            source = source.copy(items = fetchDates(anchor, source.items))
                        }
                        val items = cachedOrder?.takeIf { it.first == order }?.second ?: sortAnchorItems(source.items, order, plays, date)
                        source to items
                    }
                    ensureActive()
                    catalogue = source
                    orderedCatalogue = order to items
                    val start = (page.toLong() - 1) * size
                    val first = start.coerceAtMost(items.size.toLong()).toInt()
                    val end = (start + size).coerceAtMost(items.size.toLong()).toInt()
                    publish(AnchorContentPage(items.subList(first, end), page, size, end < items.size, items.size, source.reportedTotal))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                ensureActive()
                mutable.update { it.copy(loading = false, refreshing = false, errorCode = codeOf(failure)) }
            }
        }
    }

    private fun publish(result: AnchorContentPage<T>) {
        mutable.update { it.copy(content = result, loading = false, refreshing = false) }
    }

    companion object {
        fun maxPage(content: AnchorContentPage<*>): Int =
            ((content.total.orZero().toLong() + content.pageSize - 1) / content.pageSize).coerceAtLeast(1).toInt()

        private fun Int?.orZero() = this ?: 0
    }
}

/** Kotlin's stable sort keeps the provider's order for ties. Undated works come last in either time order. */
internal fun <T> sortAnchorItems(
    items: List<T>, order: AnchorOrder, plays: (T) -> Long?, date: (T) -> Long?,
): List<T> = when (order) {
    AnchorOrder.Default -> items
    AnchorOrder.Newest -> items.sortedByDescending { date(it) ?: Long.MIN_VALUE }
    AnchorOrder.Oldest -> items.sortedBy { date(it) ?: Long.MAX_VALUE }
    AnchorOrder.MostPlayed -> items.sortedByDescending { plays(it) ?: 0L }
}

private val AnchorReadPolicy = ReadPolicy(rateLimitRetries = 0)

internal class AnchorViewModel(
    private val route: AppRoute.AnchorPage,
    private val api: XmlyAnchor,
    private val accounts: AccountRepository,
) : ViewModel() {
    private val header = MutableStateFlow(AnchorHeader(uid = route.id.takeUnless { route.resolveFromAlbum }))
    val state = header.asStateFlow()
    private var started = false
    private var profileJob: Job? = null

    val albums = AnchorListing(
        viewModelScope, { header.value.uid }, 10,
        itemId = { it.album.id },
        plays = AnchorAlbum::playCount,
        date = { it.createdOn?.toEpochDay() },
        fetchPage = { uid, page -> api.albums(uid, accounts.primaryCredentials(), page) },
        fetchCatalogue = { uid -> api.allAlbums(uid, accounts.primaryCredentials()) },
        fetchDates = api::withAlbumDates,
    )
    val tracks = AnchorListing(
        viewModelScope, { header.value.uid }, 20,
        itemId = Track::id,
        plays = Track::playCount,
        date = Track::publishedAtMillis,
        fetchPage = { uid, page -> api.tracks(uid, page) },
        fetchCatalogue = api::allTracks,
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                ensureActive()
                header.update { it.copy(loading = false, errorCode = codeOf(failure)) }
            }
        }
    }

    companion object {
        fun factory(route: AppRoute.AnchorPage, api: XmlyAnchor, accounts: AccountRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { AnchorViewModel(route, api, accounts) } }
    }
}

/** Keep a catalogue while its route is on the stack, not for the entire life of the activity. */
internal class AnchorRouteStores : ViewModel() {
    private val stores = mutableMapOf<Any, ViewModelStoreOwner>()
    fun owner(contentKey: String): ViewModelStoreOwner = stores.getOrPut(contentKey) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }

    fun remove(contentKey: Any) {
        stores.remove(contentKey)?.viewModelStore?.clear()
    }

    override fun onCleared() {
        stores.values.forEach { it.viewModelStore.clear() }
        stores.clear()
    }
}
