package com.xuleo.xmplus.md3.account

import com.xuleo.xmplus.md3.core.account.AccountRepository
import com.xuleo.xmplus.md3.core.account.AccountsState
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Drives the opt-in automatic top-up: a round every five minutes while auto-claim is on, and one right after it is
 * switched on. Each account is topped up on its own, so one failure never affects another account.
 */
class RewardScheduler(private val accounts: AccountRepository, scope: CoroutineScope) {
    private val nudge = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            accounts.state.map { (it as? AccountsState.Ready)?.autoClaim == true }
                .distinctUntilChanged().filter { it }.collect { nudge.send(Unit) }
        }
        scope.launch {
            accounts.load()
            while (true) {
                withTimeoutOrNull(Interval) { nudge.receive() }
                round()
            }
        }
    }

    private suspend fun round() {
        val state = accounts.state.value as? AccountsState.Ready ?: return
        if (!state.autoClaim) return
        for (account in state.accounts) {
            if (account.rewardPaused || account.expired) continue
            try {
                accounts.topUp(account.identity)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A failed account is never replayed or rotated; the next round tries it again.
            }
        }
    }

    private companion object {
        val Interval = 5.minutes
    }
}