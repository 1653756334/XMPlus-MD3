package com.xuleo.xmplus.md3.core.model

import kotlin.time.Duration

/**
 * A protocol failure. [code] is a stable machine-readable error code; the UI turns it into text.
 * [definitive] marks failures that retrying cannot fix.
 */
open class XmlyException(
    val code: String,
    val retryAfter: Duration? = null,
    val definitive: Boolean = false,
    val diagnostics: Map<String, Any?>? = null,
    val fingerprint: List<String>? = null,
    cause: Throwable? = null,
) : Exception(code, cause)
