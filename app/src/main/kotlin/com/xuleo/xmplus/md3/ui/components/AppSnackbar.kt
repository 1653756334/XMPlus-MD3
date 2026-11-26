package com.xuleo.xmplus.md3.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The app's one snackbar, shown above the mini player and the bottom bar. The players, which cover everything, provide
 * their own so a message is not hidden behind them. Null in previews.
 */
val LocalAppSnackbar = staticCompositionLocalOf<SnackbarHostState?> { null }

/** Where messages are shown from: it outlives the sheet or menu that asked, so closing it does not cut the message. */
val LocalAppSnackbarScope = staticCompositionLocalOf<CoroutineScope?> { null }

/** Shows a short message in [LocalAppSnackbar], replacing the one already shown. */
@Composable
fun rememberShowMessage(): (String) -> Unit {
    val host = LocalAppSnackbar.current
    val scope = LocalAppSnackbarScope.current
    return remember(host, scope) {
        { message ->
            if (host != null && scope != null) {
                scope.launch {
                    host.currentSnackbarData?.dismiss()
                    host.showSnackbar(message)
                }
            }
        }
    }
}
