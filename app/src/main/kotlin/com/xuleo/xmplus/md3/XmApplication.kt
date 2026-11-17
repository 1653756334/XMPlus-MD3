package com.xuleo.xmplus.md3

import android.app.Application
import com.xuleo.xmplus.md3.account.RewardScheduler
import com.xuleo.xmplus.md3.core.account.AccountRepository
import com.xuleo.xmplus.md3.core.data.KeystoreAccountStore
import com.xuleo.xmplus.md3.core.protocol.XmlyHttp
import com.xuleo.xmplus.md3.core.protocol.XmlyLogin
import com.xuleo.xmplus.md3.core.protocol.XmlyQuotaClient
import com.xuleo.xmplus.md3.core.protocol.XmlyRewards
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class XmApplication : Application() {
    /** Work that belongs to the process rather than to a screen. */
    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The one door to the XMPlus API hosts, shared by every account. */
    val http = XmlyHttp()

    val accounts: AccountRepository by lazy {
        val quota = XmlyQuotaClient(http)
        AccountRepository(KeystoreAccountStore(this), quota::queryQuota, XmlyRewards(http)::claim)
    }

    /** One login session per process: it owns the SMS cooldowns and the pending challenge. */
    val login: XmlyLogin by lazy { XmlyLogin() }

    override fun onCreate() {
        super.onCreate()
        RewardScheduler(accounts, processScope)
    }
}