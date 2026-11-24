package com.xuleo.xmplus.md3.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.ui.icons.XmIcons
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.designsystem.XmPreview
import com.xuleo.xmplus.md3.core.designsystem.XmPreviews

/** The words for a failed load. The code says what happened; the user only needs to know whether to wait or retry. */
@StringRes
fun browseErrorMessage(code: String): Int = when (code) {
    "network", "network_error", "http_error" -> R.string.error_network
    "rate_limited" -> R.string.error_rate_limited
    "content_removed" -> R.string.album_unavailable
    else -> R.string.error_generic
}

@Composable
fun ErrorState(code: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(XmLayout.FormPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(XmLayout.ItemGap, Alignment.CenterVertically),
    ) {
        Icon(XmIcons.Outlined.CloudOff, contentDescription = null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
        Text(stringResource(browseErrorMessage(code)), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

/** A section that failed inside a page: the words for the failure and a retry, sized for that section. */
@Composable
fun InlineError(code: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(XmLayout.PagePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(browseErrorMessage(code)), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

@XmPreviews
@Composable
private fun ErrorStatePreview() = XmPreview { ErrorState("network", {}) }