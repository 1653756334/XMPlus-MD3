package com.xuleo.xmplus.md3

import android.app.Application
import com.xuleo.xmplus.md3.account.RewardScheduler
import com.xuleo.xmplus.md3.account.XmlyUsageService
import com.xuleo.xmplus.md3.core.account.AccountRepository
import com.xuleo.xmplus.md3.core.account.BalanceLocks
import com.xuleo.xmplus.md3.core.account.UsageLedger
import com.xuleo.xmplus.md3.core.data.KeystoreAccountStore
import com.xuleo.xmplus.md3.core.protocol.XmlyAlbum
import com.xuleo.xmplus.md3.core.protocol.XmlyAnchor
import com.xuleo.xmplus.md3.core.protocol.XmlyBrowse
import com.xuleo.xmplus.md3.core.protocol.XmlyDiscovery
import com.xuleo.xmplus.md3.core.protocol.XmlyHome
import com.xuleo.xmplus.md3.core.protocol.XmlyHttp
import com.xuleo.xmplus.md3.core.protocol.XmlyLogin
import com.xuleo.xmplus.md3.core.protocol.XmlyQuotaClient
import com.xuleo.xmplus.md3.core.protocol.XmlyRewards
import com.xuleo.xmplus.md3.core.protocol.XmlySearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class XmApplication : Application() {
    /** Work that belongs to the process rather than to a screen. */
    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The one door to the XMPlus API hosts, shared by every account. */
    val http = XmlyHttp()

    private val quotaClient by lazy { XmlyQuotaClient(http) }

    val home by lazy { XmlyHome(http) }
    val browse by lazy { XmlyBrowse(http) }
    val discovery by lazy { XmlyDiscovery(http) }
    val search by lazy { XmlySearch(http) }
    val album by lazy { XmlyAlbum(http) }
    val anchor by lazy { XmlyAnchor(http) }

    /** Claims and debits change the same balance, so they share one lock per account. */
    private val balanceLocks = BalanceLocks()

    val accounts: AccountRepository by lazy {
        AccountRepository(KeystoreAccountStore(this), quotaClient::queryQuota, XmlyRewards(http)::claim, balanceLocks)
    }

    /** Every charge for listening time goes through this ledger. */
    val usage: UsageLedger by lazy { UsageLedger(accounts, XmlyUsageService(quotaClient), balanceLocks) }

    /** One login session per process: it owns the SMS cooldowns and the pending challenge. */
    val login: XmlyLogin by lazy { XmlyLogin() }

    override fun onCreate() {
        super.onCreate()
        RewardScheduler(accounts, processScope)
        // A debit caught mid-send by the last exit becomes "uncertain" before anything can be charged again.
        processScope.launch {
            accounts.load()
            usage.load()
        }
    }
}
