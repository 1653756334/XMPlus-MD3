package com.xuleo.xmplus.md3.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Sensitive outgoing/preview entries may coexist; protect the window until the last is gone. */
internal class SensitiveWindow(private val window: Window?) {
    private var users = 0
    private var wasSecure = false

    fun acquire() {
        if (users++ == 0) {
            wasSecure = window?.attributes?.flags?.let { it and WindowManager.LayoutParams.FLAG_SECURE != 0 } ?: false
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    fun release() {
        check(users > 0)
        if (--users == 0 && !wasSecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

@Composable
internal fun rememberSensitiveWindow(): SensitiveWindow {
    val context = LocalContext.current
    return remember(context) {
        var base: Context = context
        while (base is ContextWrapper && base !is Activity) base = base.baseContext
        SensitiveWindow((base as? Activity)?.window)
    }
}

@Composable
internal fun SensitiveEntry(window: SensitiveWindow, content: @Composable () -> Unit) {
    DisposableEffect(window) {
        window.acquire()
        // Entry disposal can be a cancelled predictive preview, so do not clear inputs here.
        onDispose { window.release() }
    }
    content()
}
