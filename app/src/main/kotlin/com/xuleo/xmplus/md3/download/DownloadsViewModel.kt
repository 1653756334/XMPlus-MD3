package com.xuleo.xmplus.md3.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xuleo.xmplus.md3.XmApplication
import com.xuleo.xmplus.md3.core.download.DownloadManager
import com.xuleo.xmplus.md3.core.download.DownloadState
import com.xuleo.xmplus.md3.core.download.DownloadTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The 下载 tab: the task list, the free space, and the buttons that change them. */
class DownloadsViewModel(private val manager: DownloadManager) : ViewModel() {
    val tasks: StateFlow<List<DownloadTask>> = manager.list

    /** Free space in the download folder, read again whenever an episode finishes or is deleted. */
    val freeBytes: StateFlow<Long?> = manager.list
        .map { list -> list.count { it.state == DownloadState.Completed } }
        .distinctUntilChanged()
        .map<Int, Long?> { manager.freeBytes() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun pause(trackId: String) {
        viewModelScope.launch { manager.pause(trackId) }
    }

    fun resume(trackId: String) {
        viewModelScope.launch { manager.resume(trackId) }
    }

    fun cancel(trackId: String) {
        viewModelScope.launch { manager.cancel(trackId) }
    }

    fun delete(trackId: String) {
        viewModelScope.launch { manager.delete(trackId) }
    }

    fun deleteAlbum(albumId: String) {
        viewModelScope.launch { manager.deleteAlbum(albumId) }
    }

    fun pauseAll() {
        viewModelScope.launch { manager.pauseAll() }
    }

    fun resumeAll() {
        viewModelScope.launch { manager.resumeAll() }
    }

    fun cancelAll() {
        viewModelScope.launch { manager.cancelAll() }
    }

    companion object {
        fun factory(app: XmApplication) = viewModelFactory { initializer { DownloadsViewModel(app.downloads) } }
    }
}
