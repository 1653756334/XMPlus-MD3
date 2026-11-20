package com.xuleo.xmplus.md3.download

import androidx.annotation.StringRes
import com.xuleo.xmplus.md3.R

/** The text for a download failure code. Unknown codes read as a generic failure. */
@StringRes
fun downloadErrorMessage(code: String?): Int = when (code) {
    "insufficient_quota" -> R.string.download_error_quota
    "login_required", "login_expired" -> R.string.download_error_login
    "disk_space" -> R.string.download_error_space
    "album_too_large" -> R.string.download_error_album_size
    "missing_index" -> R.string.download_error_index
    "rate_limited" -> R.string.error_rate_limited
    "network", "http_error" -> R.string.error_network
    else -> R.string.error_generic
}