package com.xuleo.xmplus.md3

import android.app.Application
import com.xuleo.xmplus.md3.core.account.AccountRepository
import com.xuleo.xmplus.md3.core.data.KeystoreAccountStore
import com.xuleo.xmplus.md3.core.protocol.XmlyLogin
import com.xuleo.xmplus.md3.core.protocol.XmlyQuotaClient

class XmApplication : Application() {
    val accounts: AccountRepository by lazy {
        val quota = XmlyQuotaClient()
        AccountRepository(KeystoreAccountStore(this), quota::queryQuota)
    }

    /** One login session per process: it owns the SMS cooldowns and the pending challenge. */
    val login: XmlyLogin by lazy { XmlyLogin() }
}