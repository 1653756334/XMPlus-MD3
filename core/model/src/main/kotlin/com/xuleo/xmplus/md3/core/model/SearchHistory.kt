package com.xuleo.xmplus.md3.core.model

/** The keywords searched on this device, newest first: at most [Limit], each kept once. */
object SearchHistory {
    const val Limit = 10
    const val MaxKeyword = 200

    /** The keyword as it is searched, or null when it may be neither searched nor kept. */
    fun keyword(raw: String): String? = raw.dartTrim().takeIf { it.isNotEmpty() && raw.length <= MaxKeyword }

    /** [raw] moved to the front; an invalid keyword leaves the history as it was. */
    fun record(history: List<String>, raw: String): List<String> {
        val keyword = keyword(raw) ?: return history
        return (listOf(keyword) + history.filter { it != keyword }).take(Limit)
    }

    /** A stored list with anything invalid or repeated dropped. */
    fun sanitize(stored: List<String>): List<String> = stored.mapNotNull(::keyword).distinct().take(Limit)
}
