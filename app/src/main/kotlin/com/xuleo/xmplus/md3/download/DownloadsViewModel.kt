package com.xuleo.xmplus.md3.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xuleo.xmplus.md3.XmApplication
import com.xuleo.xmplus.md3.core.download.DownloadManager
import com.xuleo.xmplus.md3.core.download.DownloadTask
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The 下载 tab: the task list and the buttons that change it. */
class DownloadsViewModel(private val manager: DownloadManager) : ViewModel() {
    val tasks: StateFlow<List<DownloadTask>> = manager.list

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

    fun pauseAll() {
        viewModelScope.launch { manager.pauseAll() }
    }

    fun resumeAll() {
        viewModelScope.launch { manager.resumeAll() }
    }

    companion object {
        fun factory(app: XmApplication) = viewModelFactory { initializer { DownloadsViewModel(app.downloads) } }
    }
}