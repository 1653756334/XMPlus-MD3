package com.xuleo.xmplus.md3.library

import com.xuleo.xmplus.md3.core.model.XmlyException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One page of rows, and the cursor of the page after it; a null cursor means the list has ended. */
data class ListPage<T>(val items: List<T>, val next: Long?)

/**
 * What a list shows: its rows so far, and whether the account is signed in, the list has been read, a page is in flight,
 * or the last request failed with [failure]. [next] is the cursor [ListSection.loadMore] will ask for.
 */
data class ListState<T>(
    val items: List<T> = emptyList(),
    val signedIn: Boolean = true,
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val failure: String? = null,
    val next: Long? = null,
)

/**
 * A list read one page at a time. The first page is read when the list is first shown, and [loadMore] appends the page
 * after it. [load] receives the cursor of the page wanted (null for the first) and returns its rows with the next cursor.
 * A new read cancels one still in flight, so the last request decides what is shown.
 */
class ListSection<T>(
    private val scope: CoroutineScope,
    private val signedIn: suspend () -> Boolean,
    private val load: suspend (cursor: Long?) -> ListPage<T>,
) {
    private val state = MutableStateFlow(ListState<T>())
    val flow: StateFlow<ListState<T>> = state.asStateFlow()
    private var job: Job? = null

    /** Reads the first page unless it has been read or is being read now. */
    fun ensureLoaded() {
        if (!state.value.loaded && job?.isActive != true) refresh()
    }

    /** Starts again from the first page; the rows already shown stay until the new page arrives. */
    fun refresh() {
        job?.cancel()
        job = scope.launch {
            if (!signedIn()) {
                state.value = ListState(signedIn = false, loaded = true)
                return@launch
            }
            fetch(cursor = null, replace = true)
        }
    }

    /** Appends the next page; does nothing when the list has ended or a read is already running. */
    fun loadMore() {
        val cursor = state.value.next ?: return
        if (job?.isActive == true) return
        job = scope.launch { fetch(cursor, replace = false) }
    }

    /** Drops rows that no longer belong, such as an album that has just been unsubscribed. */
    fun remove(predicate: (T) -> Boolean) {
        state.update { it.copy(items = it.items.filterNot(predicate)) }
    }

    /** Changes rows in place, such as a comment whose like the server has confirmed. */
    fun replace(transform: (T) -> T) {
        state.update { it.copy(items = it.items.map(transform)) }
    }

    private suspend fun fetch(cursor: Long?, replace: Boolean) {
        state.update { it.copy(loading = true, failure = null) }
        try {
            val page = load(cursor)
            state.update {
                it.copy(
                    items = if (replace) page.items else it.items + page.items,
                    signedIn = true,
                    loaded = true,
                    loading = false,
                    next = page.next,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: XmlyException) {
            state.update { it.copy(loading = false, failure = failure.code) }
        } catch (_: Exception) {
            state.update { it.copy(loading = false, failure = "unknown") }
        }
    }
}