package com.xuleo.xmplus.md3.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/** Navigation events, not entry disposal, own the end of sensitive login sessions. */
internal class AccountNavigator(
    private val stack: NavBackStack<NavKey>,
    private val onRemoved: (AppRoute) -> Unit,
) {
    fun push(route: AppRoute) {
        val existing = stack.indexOf(route)
        if (existing >= 0) {
            while (stack.lastIndex > existing) pop()
        } else {
            stack.add(route)
        }
    }

    fun pop() {
        if (stack.size > 1) (stack.removeAt(stack.lastIndex) as? AppRoute)?.let(onRemoved)
    }

    fun authenticated() {
        val removed = stack.filterIsInstance<AppRoute>().filter {
            it is AppRoute.PhoneLogin || it is AppRoute.ImportLogin
        }
        Snapshot.withMutableSnapshot {
            stack.removeAll(removed.toSet())
            if (stack.lastOrNull() != AppRoute.Accounts) stack.add(AppRoute.Accounts)
        }
        removed.forEach(onRemoved)
    }
}
