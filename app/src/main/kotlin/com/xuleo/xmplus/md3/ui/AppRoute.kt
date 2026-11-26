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
    @Serializable data class AlbumPage(val albumId: String) : AppRoute {
        val contentKey: String get() = "album:$albumId"
    }
    @Serializable data class AnchorPage(val id: String, val resolveFromAlbum: Boolean = false) : AppRoute {
        val contentKey: String get() = "anchor:${if (resolveFromAlbum) "album" else "uid"}:$id"
    }
    /** A plugin's sheet, ranking, album or artist. [raw] is the plugin's object, which every later call gets back. */
    @Serializable data class MusicDetail(val kind: String, val platform: String, val id: String, val raw: String) : AppRoute {
        val contentKey: String get() = "music:$kind:$platform:$id"
    }
    @Serializable data object MusicSearch : AppRoute
    /** 最近播放, opened from 继续听 in 听过. */
    @Serializable data object Recent : AppRoute
    /** 我喜欢 (`MusicLibrary.LikesId`) or one of the user's sheets. */
    @Serializable data class LocalSheet(val id: String) : AppRoute
    /** One 听书 collection of 听过; [section] is a `LibrarySection` name. */
    @Serializable data class LibraryPage(val section: String) : AppRoute
    @Serializable data object Player : AppRoute
    @Serializable data object Accounts : AppRoute
    @Serializable data object Plugins : AppRoute
    @Serializable data object PhoneLogin : AppRoute
    @Serializable data class ImportLogin(val recoveryIdentity: String? = null) : AppRoute
}
