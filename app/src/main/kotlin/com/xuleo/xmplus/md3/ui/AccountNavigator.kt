package com.xuleo.xmplus.md3.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigationevent.NavigationEvent

/** Navigation events, not entry disposal, own the end of sensitive login sessions. */
internal class AccountNavigator(
    private val stack: NavBackStack<NavKey>,
    private val onRemoved: (AppRoute) -> Unit,
) {
    /** The edge of the committed pop, not the last gesture seen anywhere in the app. */
    var popSwipeEdge by mutableIntStateOf(NavigationEvent.EDGE_NONE)
        private set

    fun push(route: AppRoute) {
        val existing = stack.indexOf(route)
        if (existing >= 0) {
            while (stack.lastIndex > existing) pop()
        } else {
            popSwipeEdge = NavigationEvent.EDGE_NONE
            stack.add(route)
        }
    }

    fun pop(swipeEdge: Int = NavigationEvent.EDGE_NONE) {
        if (stack.size <= 1) return
        popSwipeEdge = swipeEdge
        (stack.removeAt(stack.lastIndex) as? AppRoute)?.let(onRemoved)
    }

    fun authenticated() {
        val removed = stack.filterIsInstance<AppRoute>().filter {
            it is AppRoute.PhoneLogin || it is AppRoute.ImportLogin
        }
        Snapshot.withMutableSnapshot {
            popSwipeEdge = NavigationEvent.EDGE_NONE
            stack.removeAll(removed.toSet())
            if (stack.lastOrNull() != AppRoute.Accounts) stack.add(AppRoute.Accounts)
        }
        removed.forEach(onRemoved)
    }
}
