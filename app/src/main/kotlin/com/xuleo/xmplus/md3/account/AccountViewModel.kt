package com.xuleo.xmplus.md3.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xuleo.xmplus.md3.core.account.AccountRepository
import com.xuleo.xmplus.md3.core.account.ArchiveContents
import com.xuleo.xmplus.md3.core.account.XmplusArchive
import com.xuleo.xmplus.md3.core.media.PlaybackController
import com.xuleo.xmplus.md3.core.protocol.XmlyLogin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AccountViewModel(
    private val repository: AccountRepository,
    login: XmlyLogin,
    private val playback: PlaybackController,
) : ViewModel() {
    val accounts = repository.state
    private val operations = Operations(viewModelScope)

    /** Set when a sign-in finished; the UI consumes it to leave the login screens. */
    var importCompleted by mutableStateOf(false)
        private set
    val phone = PhoneLoginSession(login, viewModelScope, saveAccount = { credentials, primary ->
        playback.changeAccount { repository.importCredentials(credentials, primary) }
    }) { importCompleted = true }

    /** A parsed backup waiting for the user's yes. It holds logins, so it stays in memory and never in saved state. */
    var archive by mutableStateOf<ArchiveContents?>(null)
        private set

    // Kept in memory only: credentials never enter SavedStateHandle or a Bundle.
    var draft by mutableStateOf("")
        private set
    val busy: Boolean get() = operations.busy
    val errorCode: String? get() = operations.errorCode

    init {
        retryLoad()
    }

    fun retryLoad() = operations.launch { repository.load() }

    fun updateDraft(value: String) {
        if (busy) return
        if (value.length > MaxImportCharacters) return operations.fail("input_too_long")
        draft = value
        operations.clearError()
    }

    fun importCredentials(makePrimary: Boolean, recoveryIdentity: String? = null) = operations.launch {
        playback.changeAccount { repository.importCredentials(draft, makePrimary, recoveryIdentity) }
        draft = ""
        importCompleted = true
    }

    fun setPrimary(identity: String) = operations.launch {
        if (repository.primaryCredentials()?.identity != identity) playback.changeAccount { repository.setPrimary(identity) }
    }
    fun remove(identity: String) = operations.launch { playback.changeAccount { repository.remove(identity) } }
    fun refreshQuota(identity: String) = operations.launch { repository.refreshQuota(identity) }
    fun claim(identity: String) = operations.launch { repository.claimOnce(identity) }
    fun resumeRewards(identity: String) = operations.launch { repository.resumeRewards(identity) }
    fun setAutoClaim(enabled: Boolean) = operations.launch { repository.setAutoClaim(enabled) }
    /** Reads and checks a backup. Nothing changes until [confirmArchive]. */
    fun previewArchive(read: suspend () -> ByteArray) = operations.launch {
        archive = withContext(Dispatchers.Default) { XmplusArchive.read(read()) }
    }

    fun confirmArchive() = operations.launch {
        val contents = archive ?: return@launch
        playback.changeAccount(preserveJournal = true) { repository.restoreArchive(contents) }
        archive = null
    }

    fun dismissArchive() {
        if (!busy) archive = null
    }

    fun clearError() = operations.clearError()
    fun consumeImport() {
        importCompleted = false
    }

    fun beginImport() {
        closeImport()
        importCompleted = false
    }

    fun closeImport() {
        operations.cancel()
        draft = ""
        operations.clearError()
    }

    fun closePhone() = phone.close()

    override fun onCleared() {
        draft = ""
        archive = null
        phone.close()
    }

    companion object {
        const val MaxImportCharacters = 65_536

        fun factory(repository: AccountRepository, login: XmlyLogin, playback: PlaybackController): ViewModelProvider.Factory =
            viewModelFactory { initializer { AccountViewModel(repository, login, playback) } }
    }
}
