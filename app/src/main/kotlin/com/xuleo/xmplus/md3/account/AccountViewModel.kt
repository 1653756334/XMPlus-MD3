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
import com.xuleo.xmplus.md3.core.protocol.XmlyLogin

class AccountViewModel(private val repository: AccountRepository, login: XmlyLogin) : ViewModel() {
    val accounts = repository.state
    private val operations = Operations(viewModelScope)

    /** Set when a sign-in finished; the UI consumes it to leave the login screens. */
    var importCompleted by mutableStateOf(false)
        private set
    val phone = PhoneLoginSession(login, repository, viewModelScope) { importCompleted = true }

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
        repository.importCredentials(draft, makePrimary, recoveryIdentity)
        draft = ""
        importCompleted = true
    }

    fun setPrimary(identity: String) = operations.launch { repository.setPrimary(identity) }
    fun remove(identity: String) = operations.launch { repository.remove(identity) }
    fun refreshQuota(identity: String) = operations.launch { repository.refreshQuota(identity) }
    fun claim(identity: String) = operations.launch { repository.claimOnce(identity) }
    fun resumeRewards(identity: String) = operations.launch { repository.resumeRewards(identity) }
    fun setAutoClaim(enabled: Boolean) = operations.launch { repository.setAutoClaim(enabled) }
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
        phone.close()
    }

    companion object {
        const val MaxImportCharacters = 65_536

        fun factory(repository: AccountRepository, login: XmlyLogin): ViewModelProvider.Factory = viewModelFactory {
            initializer { AccountViewModel(repository, login) }
        }
    }
}