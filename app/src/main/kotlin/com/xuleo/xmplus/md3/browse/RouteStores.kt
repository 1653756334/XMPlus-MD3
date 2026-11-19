package com.xuleo.xmplus.md3.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * Keeps a page's view models while its route is on the stack, not for the entire life of the activity. Album and
 * anchor pages hold whole catalogues, so each is cleared when its route leaves the stack.
 */
internal class RouteStores : ViewModel() {
    private val stores = mutableMapOf<Any, ViewModelStoreOwner>()
    fun owner(contentKey: String): ViewModelStoreOwner = stores.getOrPut(contentKey) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }

    fun remove(contentKey: Any) {
        stores.remove(contentKey)?.viewModelStore?.clear()
    }

    override fun onCleared() {
        stores.values.forEach { it.viewModelStore.clear() }
        stores.clear()
    }
}
