package com.xuleo.xmplus.md3.core.model

import kotlin.time.Instant

/** The listening time (听时) left on an account, as last reported by the server. */
class Quota(
    val remainingSeconds: Long,
    val newDevice: Boolean,
    val checkedAt: Instant,
) {
    init {
        if (remainingSeconds < 0) throw XmlyException("invalid_quota")
    }
}