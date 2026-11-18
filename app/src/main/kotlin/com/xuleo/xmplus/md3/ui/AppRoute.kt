package com.xuleo.xmplus.md3.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Only public routing metadata is saved. Login inputs and challenges never belong in keys. */
@Serializable
internal sealed interface AppRoute : NavKey {
    @Serializable data object Home : AppRoute
    @Serializable data object Search : AppRoute
    @Serializable data class AllAlbums(val categoryId: Int = 0) : AppRoute
    @Serializable data class CategoryAlbums(val groupId: Int, val groupTitle: String, val channelId: Int? = null, val channelTitle: String? = null) : AppRoute
    @Serializable data class HotRanking(val pageId: Int) : AppRoute
    @Serializable data object Accounts : AppRoute
    @Serializable data object PhoneLogin : AppRoute
    @Serializable data class ImportLogin(val recoveryIdentity: String? = null) : AppRoute
}
