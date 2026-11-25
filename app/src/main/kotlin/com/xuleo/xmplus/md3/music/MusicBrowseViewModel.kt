package com.xuleo.xmplus.md3.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xuleo.xmplus.md3.XmApplication
import com.xuleo.xmplus.md3.core.model.MusicSheet
import com.xuleo.xmplus.md3.core.plugin.MusicCatalog
import com.xuleo.xmplus.md3.core.plugin.SheetGroup
import com.xuleo.xmplus.md3.core.plugin.SheetTag
import com.xuleo.xmplus.md3.core.plugin.SheetTags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One plugin's sheet square. [tag] null is the plugin's default list, as MusicFree opens it. */
data class SheetSquare(
    val tags: SheetTags? = null,
    val tagsFailed: Boolean = false,
    val tag: SheetTag? = null,
    val sheets: List<MusicSheet> = emptyList(),
    val page: Int = 0,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val isEnd: Boolean = false,
)

/** One plugin's rankings; [groups] is null until they first arrive. */
data class TopLists(val groups: List<SheetGroup>? = null, val loading: Boolean = false, val failed: Boolean = false)

/** 推荐 and 榜单 of every plugin opened so far. What was loaded stays while the app runs, so switching back is instant. */
class MusicBrowseViewModel(private val catalog: MusicCatalog) : ViewModel() {
    private val mutableSquares = MutableStateFlow<Map<String, SheetSquare>>(emptyMap())
    val squares: StateFlow<Map<String, SheetSquare>> = mutableSquares.asStateFlow()

    private val mutableTopLists = MutableStateFlow<Map<String, TopLists>>(emptyMap())
    val topLists: StateFlow<Map<String, TopLists>> = mutableTopLists.asStateFlow()

    private val sheetJobs = HashMap<String, Job>()

    fun openSquare(platform: String) {
        if (platform in mutableSquares.value) return
        mutableSquares.update { it + (platform to SheetSquare()) }
        loadTags(platform)
        loadSheets(platform)
    }

    fun selectTag(platform: String, tag: SheetTag?) {
        val square = mutableSquares.value[platform] ?: return
        if (square.tag?.id == tag?.id && (square.sheets.isNotEmpty() || square.loading)) return
        sheetJobs.remove(platform)?.cancel()
        editSquare(platform) { SheetSquare(tags = it.tags, tagsFailed = it.tagsFailed, tag = tag) }
        loadSheets(platform)
    }

    fun loadMoreSheets(platform: String) {
        val square = mutableSquares.value[platform] ?: return
        if (square.loading || square.failed || square.isEnd) return
        loadSheets(platform)
    }

    fun retrySheets(platform: String) {
        if (mutableSquares.value[platform]?.loading == true) return
        loadSheets(platform)
    }

    fun retryTags(platform: String) = loadTags(platform)

    fun openTopLists(platform: String) {
        val current = mutableTopLists.value[platform]
        if (current != null && (current.groups != null || current.loading)) return
        loadTopLists(platform)
    }

    fun retryTopLists(platform: String) {
        if (mutableTopLists.value[platform]?.loading == true) return
        loadTopLists(platform)
    }

    private fun loadTags(platform: String) {
        editSquare(platform) { it.copy(tagsFailed = false) }
        viewModelScope.launch {
            try {
                val tags = catalog.sheetTags(platform)
                editSquare(platform) { it.copy(tags = tags) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                editSquare(platform) { it.copy(tagsFailed = true) }
            }
        }
    }

    private fun loadSheets(platform: String) {
        val square = mutableSquares.value[platform] ?: return
        val tag = square.tag
        val page = square.page + 1
        editSquare(platform) { it.copy(loading = true, failed = false) }
        sheetJobs[platform] = viewModelScope.launch {
            try {
                val result = catalog.sheets(platform, tag, page)
                editSquare(platform) {
                    if (it.tag?.id != tag?.id) return@editSquare it
                    // Plugins repeat sheets across pages now and then; each one is shown once.
                    val sheets = (it.sheets + result.items).distinctBy { sheet -> sheet.id }
                    it.copy(sheets = sheets, page = page, loading = false, isEnd = result.isEnd)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                editSquare(platform) { if (it.tag?.id != tag?.id) it else it.copy(loading = false, failed = true) }
            }
        }
    }

    private fun loadTopLists(platform: String) {
        mutableTopLists.update { it + (platform to (it[platform] ?: TopLists()).copy(loading = true, failed = false)) }
        viewModelScope.launch {
            val next = try {
                TopLists(groups = catalog.topLists(platform))
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                TopLists(failed = true)
            }
            mutableTopLists.update { it + (platform to next) }
        }
    }

    private fun editSquare(platform: String, edit: (SheetSquare) -> SheetSquare) =
        mutableSquares.update { map -> map[platform]?.let { map + (platform to edit(it)) } ?: map }

    companion object {
        fun factory(app: XmApplication) = viewModelFactory { initializer { MusicBrowseViewModel(app.musicCatalog) } }
    }
}
