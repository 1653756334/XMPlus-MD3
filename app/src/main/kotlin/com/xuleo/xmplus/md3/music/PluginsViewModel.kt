package com.xuleo.xmplus.md3.music

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.XmApplication
import com.xuleo.xmplus.md3.core.plugin.InstallFailure
import com.xuleo.xmplus.md3.core.plugin.InstallProgress
import com.xuleo.xmplus.md3.core.plugin.InstallResult
import com.xuleo.xmplus.md3.core.plugin.InstalledPlugin
import com.xuleo.xmplus.md3.core.plugin.PluginManager
import com.xuleo.xmplus.md3.core.plugin.PluginSubscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the 插件 page reports once an action finishes. */
sealed interface PluginEvent {
    data class Results(val results: List<InstallResult>) : PluginEvent
    data object LinkCopied : PluginEvent
}

/** An install or update: how far it has got while it runs, every result once it has finished. */
data class InstallJob(
    val update: Boolean,
    val done: Int = 0,
    /** 0 while the links are still being read. */
    val total: Int = 0,
    val current: String? = null,
    val results: List<InstallResult>? = null,
) {
    val running: Boolean get() = results == null
}

class PluginsViewModel(private val app: XmApplication, private val manager: PluginManager) : ViewModel() {
    val plugins: StateFlow<List<InstalledPlugin>?> = manager.plugins
    val subscriptions: StateFlow<List<PluginSubscription>> = manager.subscriptions

    /** The running install or update, or the results waiting to be read; a second one waits for this one to end. */
    var job by mutableStateOf<InstallJob?>(null)
        private set

    val busy: Boolean get() = job?.running == true

    /** The user hid the progress sheet; the job goes on and its results arrive as a snackbar. */
    var hidden by mutableStateOf(false)
        private set

    private val mutableEvents = Channel<PluginEvent>(Channel.BUFFERED)
    val events: Flow<PluginEvent> = mutableEvents.receiveAsFlow()

    private val preferences = app.getSharedPreferences("music", Context.MODE_PRIVATE)
    private val mutableSource = MutableStateFlow(preferences.getString(SourceKey, null))

    /** The plugin 推荐 and 榜单 were last shown from. */
    val source: StateFlow<String?> = mutableSource.asStateFlow()

    fun selectSource(platform: String) {
        mutableSource.value = platform
        preferences.edit().putString(SourceKey, platform).apply()
    }

    fun installFiles(uris: List<Uri>) = work(update = false) { progress ->
        val files = withContext(Dispatchers.IO) {
            uris.map { uri ->
                val code = try {
                    app.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                } catch (_: Exception) {
                    null
                }
                displayName(uri) to code
            }
        }
        val unreadable = files.filter { it.second == null }.map { InstallResult.Failed(InstallFailure.CannotParse, it.first) }
        manager.installFiles(files.mapNotNull { (name, code) -> code?.let { name to it } }, progress) + unreadable
    }

    fun installLink(url: String) = work(update = false) { manager.installLink(url, it) }

    fun update(platform: String) = work(update = true) { listOf(manager.update(platform)) }

    fun updateAll() = work(update = true) { manager.updateAll(it) }

    fun updateSubscriptions() = work(update = false) { manager.updateSubscriptions(it) }

    /** Closes the results, or lets a running job go on without the sheet. */
    fun dismissJob() {
        if (busy) hidden = true else job = null
    }

    fun uninstall(platform: String) {
        viewModelScope.launch { manager.uninstall(platform) }
    }

    fun setEnabled(platform: String, enabled: Boolean) {
        viewModelScope.launch { manager.setEnabled(platform, enabled) }
    }

    fun setVariables(platform: String, values: Map<String, String>) {
        viewModelScope.launch { manager.setVariables(platform, values) }
    }

    fun setAlternative(platform: String, alternative: String?) {
        viewModelScope.launch { manager.setAlternative(platform, alternative) }
    }

    fun reorder(platforms: List<String>) {
        viewModelScope.launch { manager.reorder(platforms) }
    }

    fun setSubscriptions(list: List<PluginSubscription>) {
        viewModelScope.launch { manager.setSubscriptions(list) }
    }

    fun linkCopied() {
        mutableEvents.trySend(PluginEvent.LinkCopied)
    }

    private fun work(update: Boolean, block: suspend (InstallProgress) -> List<InstallResult>) {
        if (busy) return
        hidden = false
        job = InstallJob(update)
        val progress = InstallProgress { done, total, current ->
            job = job?.copy(done = done, total = total, current = current)
        }
        viewModelScope.launch {
            var results: List<InstallResult>? = null
            try {
                results = block(progress)
            } finally {
                val list = results.orEmpty()
                // One result fits in a snackbar; so do the results of a job whose sheet the user hid.
                val quiet = hidden || list.size <= 1
                job = if (quiet || results == null) null else job?.copy(done = list.size, total = list.size, current = null, results = list)
                if (quiet && list.isNotEmpty()) mutableEvents.trySend(PluginEvent.Results(list))
            }
        }
    }

    private fun displayName(uri: Uri): String = try {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (_: Exception) {
        null
    } ?: uri.lastPathSegment.orEmpty()

    companion object {
        private const val SourceKey = "source"

        fun factory(app: XmApplication) = viewModelFactory { initializer { PluginsViewModel(app, app.plugins) } }
    }
}

/** One line for the snackbar: the single result, or how many succeeded and failed. */
fun installMessage(res: Resources, results: List<InstallResult>): String? {
    if (results.isEmpty()) return null
    if (results.size == 1) return when (val result = results.single()) {
        is InstallResult.Installed -> res.getString(R.string.plugin_installed, result.platform)
        is InstallResult.AlreadyInstalled -> res.getString(R.string.plugin_already, result.platform)
        is InstallResult.Failed -> res.getString(failureText(result.reason))
    }
    return installSummary(res, results)
}

fun installSummary(res: Resources, results: List<InstallResult>): String {
    val ok = results.count { it !is InstallResult.Failed }
    return res.getString(R.string.plugin_install_summary, ok, results.size - ok)
}

fun failureText(reason: InstallFailure) = when (reason) {
    InstallFailure.CannotParse -> R.string.plugin_fail_parse
    InstallFailure.LowerVersion -> R.string.plugin_fail_lower
    InstallFailure.BadLink -> R.string.plugin_fail_link
    InstallFailure.Network -> R.string.plugin_fail_network
}
