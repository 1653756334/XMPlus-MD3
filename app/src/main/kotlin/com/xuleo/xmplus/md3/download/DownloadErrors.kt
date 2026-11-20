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
    "confirmation_required", "confirmation_changed" -> R.string.download_error_confirm
    "range_invalid" -> R.string.download_error_range
    "range_missing" -> R.string.download_error_range_missing
    "range_ambiguous" -> R.string.download_error_range_ambiguous
    "file_missing" -> R.string.download_error_file_missing
    "rate_limited" -> R.string.error_rate_limited
    "network", "http_error" -> R.string.error_network
    else -> R.string.error_generic
}
