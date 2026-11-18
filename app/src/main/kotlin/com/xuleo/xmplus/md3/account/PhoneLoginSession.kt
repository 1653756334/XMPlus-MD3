package com.xuleo.xmplus.md3.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.protocol.GeetestProof
import com.xuleo.xmplus.md3.core.protocol.SmsChallenge
import com.xuleo.xmplus.md3.core.protocol.XmlyLogin
import kotlinx.coroutines.CoroutineScope

/**
 * Phone login: security check, then the SMS the user confirms, then the code, then an ordinary
 * credential import. Everything sensitive stays in memory; leaving the screen calls [close].
 */
class PhoneLoginSession(
    private val login: XmlyLogin,
    scope: CoroutineScope,
    private val saveAccount: suspend (Credentials, Boolean) -> Unit,
    private val onSignedIn: () -> Unit,
) {
    private val operations = Operations(scope)

    var mobile by mutableStateOf("")
        private set
    var code by mutableStateOf("")
        private set
    var makePrimary by mutableStateOf(true)

    /** Set once an SMS was sent; the code can then be entered. */
    private var challenge by mutableStateOf<SmsChallenge?>(null)

    /** A verified login whose import failed. Retrying it does not need another SMS. */
    private var candidate by mutableStateOf<Credentials?>(null)

    /** A finished security check waiting for the user to confirm sending the SMS. */
    var pendingProof by mutableStateOf<GeetestProof?>(null)
        private set
    var checking by mutableStateOf(false)
        private set

    val busy: Boolean get() = operations.busy
    val errorCode: String? get() = operations.errorCode
    val awaitingCode: Boolean get() = challenge != null
    val signInPending: Boolean get() = candidate != null
    val cooldownSeconds: Int get() = login.smsCooldownSeconds(mobile)

    fun updateMobile(value: String) {
        if (busy || awaitingCode || signInPending) return
        mobile = value.filterIndexed { index, char -> char in '0'..'9' || (char == '+' && index == 0) }.take(MaxMobileLength)
        operations.clearError()
    }

    fun updateCode(value: String) {
        if (busy) return
        code = value.filter { it in '0'..'9' }.take(MaxCodeLength)
    }

    fun startCheck() {
        if (busy || checking || signInPending || cooldownSeconds > 0) return
        if (!XmlyLogin.validPhone(mobile)) return operations.fail("invalid_mobile")
        operations.clearError()
        checking = true
    }

    fun onProof(proof: GeetestProof) {
        checking = false
        pendingProof = proof
    }

    fun onCheckClosed() {
        checking = false
    }

    fun cancelSend() {
        pendingProof = null
    }

    fun confirmSend() {
        val proof = pendingProof ?: return
        pendingProof = null
        operations.launch {
            // The previous code stays usable until the replacement SMS is confirmed.
            login.clear()
            challenge = null
            code = ""
            challenge = login.sendSms(mobile, proof)
        }
    }

    fun verify() {
        val pending = challenge
        if (pending == null && candidate == null) return
        operations.launch {
            try {
                val credentials = candidate ?: verified(requireNotNull(pending))
                saveAccount(credentials, makePrimary)
                candidate = null
                onSignedIn()
            } catch (failure: XmlyException) {
                if (failure.code == "challenge_invalid" || failure.code == "challenge_expired") discardChallenge()
                throw failure
            }
        }
    }

    private suspend fun verified(pending: SmsChallenge): Credentials =
        login.verifySms(pending.id, code).also {
            candidate = it
            challenge = null
            code = ""
            mobile = ""
        }

    /** Called every second while the screen is visible. */
    fun expireStaleChallenge() {
        val pending = challenge ?: return
        if (!busy && login.challengeRemainingSeconds(pending.id) == 0) {
            login.clear()
            discardChallenge()
            operations.fail("challenge_expired")
        }
    }

    private fun discardChallenge() {
        challenge = null
        code = ""
    }

    fun close() {
        operations.cancel()
        login.clear()
        mobile = ""
        code = ""
        challenge = null
        candidate = null
        pendingProof = null
        checking = false
        operations.clearError()
    }

    private companion object {
        const val MaxMobileLength = 19
        const val MaxCodeLength = 8
    }
}
