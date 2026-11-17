package com.xuleo.xmplus.md3.core.account

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes everything that changes one account's balance (claims and debits), keyed by account identity so a
 * refreshed login for the same account shares the lock. Callers take their own account or ledger locks first.
 */
class BalanceLocks {
    private class Entry {
        val mutex = Mutex()
        var users = 0
    }

    private val entries = HashMap<String, Entry>()

    suspend fun <T> withLock(identity: String, action: suspend () -> T): T {
        val entry = synchronized(entries) { entries.getOrPut(identity, ::Entry).also { it.users++ } }
        try {
            return entry.mutex.withLock { action() }
        } finally {
            synchronized(entries) { if (--entry.users == 0) entries.remove(identity) }
        }
    }
}