package com.xuleo.xmplus.md3.library

/** A 订阅 or 关注 switch as a page shows it. [on] stays null until the account's list has been read. */
data class LibraryToggle(
    val signedIn: Boolean = false,
    val on: Boolean? = null,
    val busy: Boolean = false,
    val failed: Boolean = false,
)
