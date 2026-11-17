package com.xuleo.xmplus.md3.core.protocol

/** The API hosts this app talks to. The login host has its own client. */
internal enum class Host(val origin: String) {
    Mobile("https://mobile.ximalaya.com"),
    Welfare("https://m.ximalaya.com"),
}

/**
 * One allowed request shape. Declaring endpoints as values, rather than matching strings at call time, is what keeps
 * the client from sending anything the original app never sent.
 *
 * @property path may contain [TimestampToken], filled with the request time.
 * @property idempotent a repeat has the same effect as one attempt, so a failed attempt may be retried; everything
 * else is sent at most once and reported as uncertain when its outcome is unknown.
 * @property successCode the `ret`/`status` value that means success.
 */
internal class Endpoint(
    val host: Host,
    val method: String,
    val path: String,
    val authenticated: Boolean = true,
    val idempotent: Boolean = false,
    val successCode: String = "0",
) {
    fun resolve(timestamp: Long?): String = path.replace(TimestampToken, timestamp?.toString() ?: "")

    companion object {
        const val TimestampToken = "{ts}"
    }
}

internal object Endpoints {
    val Config = Endpoint(Host.Mobile, "POST", "/lite-mobile/audi/func/v1/config", idempotent = true)
}