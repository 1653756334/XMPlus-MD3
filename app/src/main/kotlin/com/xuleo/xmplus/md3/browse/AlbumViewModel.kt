package com.xuleo.xmplus.md3.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xuleo.xmplus.md3.core.account.AccountRepository
import com.xuleo.xmplus.md3.core.model.AlbumDetail
import com.xuleo.xmplus.md3.core.model.AlbumReply
import com.xuleo.xmplus.md3.core.model.AlbumReview
import com.xuleo.xmplus.md3.core.model.AlbumScore
import com.xuleo.xmplus.md3.core.model.AlbumSimilar
import com.xuleo.xmplus.md3.core.model.Track
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.protocol.XmlyAlbum
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything one album page shows. Each part loads and fails on its own. */
data class AlbumState(
    val detail: AlbumDetail? = null,
    val headerLoading: Boolean = true,
    val headerErrorCode: String? = null,
    val introduction: String? = null,
    val introductionLoading: Boolean = false,
    val introductionErrorCode: String? = null,
    val similar: AlbumSimilar? = null,
    val similarLoading: Boolean = false,
    val similarErrorCode: String? = null,
    /** True for episode-number order, oldest first. */
    val ascending: Boolean = true,
    /** The episodes loaded so far, in the order on screen. Each page is added when the list asks for it. */
    val episodes: List<Track> = emptyList(),
    val episodePage: Int = 0,
    val episodesHasMore: Boolean = false,
    val episodesLoading: Boolean = false,
    val episodesErrorCode: String? = null,
    val searchOpen: Boolean = false,
    val query: String = "",
    val hits: List<Track> = emptyList(),
    val hitPage: Int = 0,
    val hitsHasMore: Boolean = false,
    val hitsLoading: Boolean = false,
    val hitsErrorCode: String? = null,
    val newestReviews: Boolean = false,
    val score: AlbumScore? = null,
    val reviews: List<AlbumReview> = emptyList(),
    val reviewPage: Int = 0,
    val reviewsHasMore: Boolean = false,
    val reviewsLoading: Boolean = false,
    val reviewsErrorCode: String? = null,
    /** The review whose replies sheet is open, if any. */
    val replyTo: AlbumReview? = null,
    val replies: List<AlbumReply> = emptyList(),
    val replyPage: Int = 0,
    val repliesHasMore: Boolean = false,
    val repliesLoading: Boolean = false,
    val repliesErrorCode: String? = null,
)

/**
 * One album's page. The header loads first, with the introduction, similar albums and the first page of episodes. Reviews
 * load when their tab opens; search results and replies page on demand.
 */
class AlbumViewModel(
    private val albumId: String,
    private val album: XmlyAlbum,
    private val accounts: AccountRepository,
) : ViewModel() {
    private val mutable = MutableStateFlow(AlbumState())
    val state: StateFlow<AlbumState> = mutable.asStateFlow()
    private var started = false
    private var headerJob: Job? = null
    private var introductionJob: Job? = null
    private var similarJob: Job? = null
    private var episodesJob: Job? = null
    private var hitsJob: Job? = null
    private var reviewsJob: Job? = null
    private var repliesJob: Job? = null

    /** Opens the album once; the screen may call it again when it comes back. */
    fun start() {
        if (started) return
        started = true
        loadHeader()
    }

    fun retryHeader() = loadHeader()

    private fun loadHeader() {
        headerJob?.cancel()
        mutable.update { it.copy(headerLoading = true, headerErrorCode = null) }
        headerJob = viewModelScope.launch {
            try {
                val detail = withContext(Dispatchers.Default) { album.detail(albumId) }
                mutable.update { it.copy(detail = detail, headerLoading = false) }
                loadIntroduction()
                loadSimilar()
                loadEpisodes(1)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutable.update { it.copy(headerLoading = false, headerErrorCode = codeOf(failure)) }
            }
        }
    }

    fun retryIntroduction() = loadIntroduction()

    private fun loadIntroduction() {
        introductionJob?.cancel()
        mutable.update { it.copy(introductionLoading = true, introductionErrorCode = null) }
        introductionJob = viewModelScope.launch {
            try {
                val html = withContext(Dispatchers.Default) { album.introduction(albumId, accounts.primaryCredentials()) }
                mutable.update { it.copy(introduction = html, introductionLoading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutable.update { it.copy(introductionLoading = false, introductionErrorCode = codeOf(failure)) }
            }
        }
    }

    fun retrySimilar() = loadSimilar()

    private fun loadSimilar() {
        similarJob?.cancel()
        mutable.update { it.copy(similarLoading = true, similarErrorCode = null) }
        similarJob = viewModelScope.launch {
            try {
                val similar = withContext(Dispatchers.Default) { album.similar(albumId) }
                mutable.update { it.copy(similar = similar, similarLoading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutable.update { it.copy(similarLoading = false, similarErrorCode = codeOf(failure)) }
            }
        }
    }

    /** Episodes are shown oldest first or newest first. Either way the whole album is listed again from its first page. */
    fun selectOrder(ascending: Boolean) {
        if (mutable.value.ascending == ascending) return
        mutable.update { it.copy(ascending = ascending, episodes = emptyList(), episodePage = 0, episodesHasMore = false) }
        loadEpisodes(1)
    }

    fun loadMoreEpisodes() {
        val state = mutable.value
        if (state.episodePage > 0 && state.episodesHasMore && !state.episodesLoading && state.episodesErrorCode == null) {
            loadEpisodes(state.episodePage + 1)
        }
    }

    fun retryEpisodes() {
        val state = mutable.value
        if (!state.episodesLoading) loadEpisodes(state.episodePage + 1)
    }

    /** Reads one page in the order the state asks for. Page 1 starts the list; later pages are added to its end. */
    private fun loadEpisodes(page: Int) {
        val state = mutable.value
        val detail = state.detail ?: return
        val ascending = state.ascending
        episodesJob?.cancel()
        mutable.update { it.copy(episodesLoading = true, episodesErrorCode = null) }
        episodesJob = viewModelScope.launch {
            try {
                val read = withContext(Dispatchers.Default) {
                    album.episodePage(albumId, page, ascending, detail.recordDescending, accounts.primaryCredentials())
                }
                // A page whose total has moved means the album changed under us.
                if (read.total != detail.album.trackCount) throw XmlyException("catalogue_changed")
                val tracks = read.items.sortedWith(
                    if (ascending) compareBy { it.officialPosition } else compareByDescending { it.officialPosition },
                )
                mutable.update { now ->
                    now.copy(
                        episodes = (if (page == 1) emptyList() else now.episodes) + tracks,
                        episodePage = page,
                        episodesHasMore = page * read.pageSize < read.total,
                        episodesLoading = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutable.update { it.copy(episodesLoading = false, episodesErrorCode = codeOf(failure)) }
            }
        }
    }

    fun openSearch() = mutable.update { it.copy(searchOpen = true) }

    fun closeSearch() {
        hitsJob?.cancel()
        mutable.update {
            it.copy(
                searchOpen = false, query = "", hits = emptyList(), hitPage = 0, hitsHasMore = false,
                hitsLoading = false, hitsErrorCode = null,
            )
        }
    }

    fun setQuery(query: String) = mutable.update { it.copy(query = query) }

    /** Searches the album for the query now in the field. A blank query only clears the results. */
    fun submitSearch() {
        hitsJob?.cancel()
        mutable.update { it.copy(hits = emptyList(), hitPage = 0, hitsHasMore = false, hitsLoading = false, hitsErrorCode = null) }
        if (mutable.value.query.isNotBlank()) loadHits(1)
    }

    fun loadMoreHits() {
        val state = mutable.value
        if (state.hitPage > 0 && state.hitsHasMore && !state.hitsLoading && state.hitsErrorCode == null) loadHits(state.hitPage + 1)
    }

    fun retryHits() {
        val state = mutable.value
        if (!state.hitsLoading) loadHits(state.hitPage + 1)
    }

    private fun loadHits(page: Int) {
        val query = mutable.value.query.trim()
        hitsJob?.cancel()
        mutable.update { it.copy(hitsLoading = true, hitsErrorCode = null) }
        hitsJob = viewModelScope.launch {
            try {
                val read = withContext(Dispatchers.Default) { album.searchEpisodes(albumId, query, page) }
                mutable.update { now ->
                    val known = if (page == 1) HashSet() else now.hits.mapTo(HashSet()) { it.id }
                    now.copy(
                        hits = (if (page == 1) emptyList() else now.hits) + read.items.filter { known.add(it.id) },
                        hitPage = page,
                        hitsHasMore = page * read.pageSize < read.total,
                        hitsLoading = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutable.update { it.copy(hitsLoading = false, hitsErrorCode = codeOf(failure)) }
            }
        }
    }

    /** Loads the reviews the first time their tab is shown; later calls do nothing. */
    fun ensureReviews() {
        val state = mutable.value
        if (state.reviewPage == 0 && !state.reviewsLoading) loadReviews(1)
    }

    fun setNewestReviews(newest: Boolean) {
        if (mutable.value.newestReviews == newest) return
        mutable.update {
            it.copy(newestReviews = newest, reviews = emptyList(), reviewPage = 0, reviewsHasMore = false, reviewsErrorCode = null)
        }
        loadReviews(1)
    }

    fun loadMoreReviews() {
        val state = mutable.value
        if (state.reviewPage > 0 && state.reviewsHasMore && !state.reviewsLoading && state.reviewsErrorCode == null) loadReviews(state.reviewPage + 1)
    }

    fun retryReviews() {
        val state = mutable.value
        if (!state.reviewsLoading) loadReviews(state.reviewPage + 1)
    }

    private fun loadReviews(page: Int) {
        val newest = mutable.value.newestReviews
        reviewsJob?.cancel()
        mutable.update { it.copy(reviewsLoading = true, reviewsErrorCode = null) }
        reviewsJob = viewModelScope.launch {
            try {
                val read = withContext(Dispatchers.Default) { album.reviews(albumId, page, newest) }
                mutable.update { now ->
                    val known = if (page == 1) HashSet() else now.reviews.mapTo(HashSet()) { it.id }
                    now.copy(
                        score = if (page == 1) read.score else now.score,
                        reviews = (if (page == 1) emptyList() else now.reviews) + read.items.filter { known.add(it.id) },
                        reviewPage = page,
                        reviewsHasMore = read.hasMore,
                        reviewsLoading = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutable.update { it.copy(reviewsLoading = false, reviewsErrorCode = codeOf(failure)) }
            }
        }
    }

    fun openReplies(review: AlbumReview) {
        repliesJob?.cancel()
        mutable.update {
            it.copy(replyTo = review, replies = emptyList(), replyPage = 0, repliesHasMore = false, repliesLoading = false, repliesErrorCode = null)
        }
        loadReplies(1)
    }

    fun closeReplies() {
        repliesJob?.cancel()
        mutable.update {
            it.copy(replyTo = null, replies = emptyList(), replyPage = 0, repliesHasMore = false, repliesLoading = false, repliesErrorCode = null)
        }
    }

    fun loadMoreReplies() {
        val state = mutable.value
        if (state.replyPage > 0 && state.repliesHasMore && !state.repliesLoading && state.repliesErrorCode == null) loadReplies(state.replyPage + 1)
    }

    fun retryReplies() {
        val state = mutable.value
        if (!state.repliesLoading) loadReplies(state.replyPage + 1)
    }

    private fun loadReplies(page: Int) {
        val root = mutable.value.replyTo?.id ?: return
        repliesJob?.cancel()
        mutable.update { it.copy(repliesLoading = true, repliesErrorCode = null) }
        repliesJob = viewModelScope.launch {
            try {
                val read = withContext(Dispatchers.Default) { album.replies(albumId, root, page) }
                mutable.update { now ->
                    val known = if (page == 1) HashSet() else now.replies.mapTo(HashSet()) { it.id }
                    now.copy(
                        replies = (if (page == 1) emptyList() else now.replies) + read.items.filter { known.add(it.id) },
                        replyPage = page,
                        repliesHasMore = read.hasMore,
                        repliesLoading = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutable.update { it.copy(repliesLoading = false, repliesErrorCode = codeOf(failure)) }
            }
        }
    }

    companion object {
        fun factory(albumId: String, album: XmlyAlbum, accounts: AccountRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { AlbumViewModel(albumId, album, accounts) } }
    }
}