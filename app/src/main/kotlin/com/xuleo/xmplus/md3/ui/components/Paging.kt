package com.xuleo.xmplus.md3.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout

/**
 * Asks for the next page when the last visible row is a few rows from the end. It runs again whenever one of
 * [keys] changes, so a page that lands while the end is still in view asks for the one after it.
 */
@Composable
fun LoadMoreEffect(lastVisible: () -> Int?, total: () -> Int, vararg keys: Any?, onLoadMore: () -> Unit) {
    val current = rememberUpdatedState(onLoadMore)
    LaunchedEffect(*keys) {
        snapshotFlow { lastVisible()?.let { it >= total() - 5 } == true }.collect { if (it) current.value() }
    }
}

/** The last row of a paged list: busy, failed with a retry, or the end. */
@Composable
fun PagingFooter(loading: Boolean, failed: Boolean, hasMore: Boolean, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(XmLayout.PagePadding), contentAlignment = Alignment.Center) {
        when {
            failed -> TextButton(onRetry) { Text(stringResource(R.string.action_retry)) }
            loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            !hasMore -> Text(stringResource(R.string.list_end), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}