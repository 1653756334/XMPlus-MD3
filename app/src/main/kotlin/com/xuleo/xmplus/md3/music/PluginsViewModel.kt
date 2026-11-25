package com.xuleo.xmplus.md3.music

import android.content.res.Resources
import android.net.Uri
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
import com.xuleo.xmplus.md3.core.plugin.InstallResult
import com.xuleo.xmplus.md3.core.plugin.InstalledPlugin
import com.xuleo.xmplus.md3.core.plugin.PluginManager
import com.xuleo.xmplus.md3.core.plugin.PluginSubscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the 插件 page reports once an action finishes. */
sealed interface PluginEvent {
    data class Results(val results: List<InstallResult>) : PluginEvent
    data object LinkCopied : PluginEvent
}

class PluginsViewModel(private val app: XmApplication, private val manager: PluginManager) : ViewModel() {
    val plugins: StateFlow<List<InstalledPlugin>?> = manager.plugins
    val subscriptions: StateFlow<List<PluginSubscription>> = manager.subscriptions

    /** An install or update is running; a second one waits for the user to try again. */
    var busy by mutableStateOf(false)
        private set

    private val mutableEvents = Channel<PluginEvent>(Channel.BUFFERED)
    val events: Flow<PluginEvent> = mutableEvents.receiveAsFlow()

    fun installFiles(uris: List<Uri>) = work {
        val files = withContext(Dispatchers.IO) {
            uris.mapNotNull { uri ->
                val code = try {
                    app.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                } catch (_: Exception) {
                    null
                }
                code?.let { uri.toString() to it }
            }
        }
        val unreadable = List(uris.size - files.size) { InstallResult.Failed(InstallFailure.CannotParse, "") }
        manager.installFiles(files) + unreadable
    }

    fun installLink(url: String) = work { manager.installLink(url) }

    fun update(platform: String) = work { listOf(manager.update(platform)) }

    fun updateAll() = work { manager.updateAll() }

    fun updateSubscriptions() = work { manager.updateSubscriptions() }

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

    private fun work(block: suspend () -> List<InstallResult>) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val results = block()
                if (results.isNotEmpty()) mutableEvents.send(PluginEvent.Results(results))
            } finally {
                busy = false
            }
        }
    }

    companion object {
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
    val ok = results.count { it !is InstallResult.Failed }
    return res.getString(R.string.plugin_install_summary, ok, results.size - ok)
}

private fun failureText(reason: InstallFailure) = when (reason) {
    InstallFailure.CannotParse -> R.string.plugin_fail_parse
    InstallFailure.LowerVersion -> R.string.plugin_fail_lower
    InstallFailure.BadLink -> R.string.plugin_fail_link
    InstallFailure.Network -> R.string.plugin_fail_network
}
