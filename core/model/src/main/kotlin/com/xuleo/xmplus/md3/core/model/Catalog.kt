package com.xuleo.xmplus.md3.core.model


/** An album as lists and headers show it. Covers are absolute https URLs, already checked. */
data class Album(
    val id: String,
    val title: String = "",
    val artist: String = "",
    val intro: String = "",
    val coverUrl: String? = null,
    val trackCount: Int = 0,
    val anchorUid: String? = null,
    val anchorAvatarUrl: String? = null,
)

/** The numbers a list row may show beside an album; any of them can be missing. */
data class AlbumStats(
    val score: Double? = null,
    val playCount: Long? = null,
    val subscribeCount: Long? = null,
    val finished: Boolean = false,
)

/** A titled run of albums: a home section, a ranking or one page of a category. */
data class AlbumSection(
    val title: String,
    val albums: List<Album>,
    val stats: Map<String, AlbumStats> = emptyMap(),
    val ranking: Boolean = false,
    val moreCategoryId: Int? = null,
)

data class Track(
    val id: String,
    val title: String = "",
    val artist: String = "",
    val albumTitle: String = "",
    val albumId: String = "",
    val durationSeconds: Double = 0.0,
    val orderNo: Int? = null,
    val episode: Int? = null,
    val paid: Boolean? = null,
    val authorized: Boolean? = null,
    val coverUrl: String? = null,
    val intro: String = "",
    val likes: Int? = null,
    val playCount: Long? = null,
    val publishedAtMillis: Long? = null,
    val commentCount: Int? = null,
    val officialPosition: Int? = null,
    /** Cloud history uses the episode row's direction, not the player's queue order; null means an invalid value. */
    val historyDirection: Int? = 0,
) {
    /** The id as XMPlus stores it in queues, libraries and backups. */
    val key: String get() = "xmly:$id"
}

data class AnchorProfile(
    val uid: String,
    val nickname: String,
    val avatarUrl: String? = null,
    val backgroundUrl: String? = null,
    val intro: String = "",
    val verification: String = "",
    val verified: Boolean = false,
    val level: Int? = null,
    val location: String = "",
    val fans: Int? = null,
    val following: Int? = null,
    val albums: Int? = null,
    val tracks: Int? = null,
)

/** An album on an anchor's page, with its play count when the server gives one. */
data class AnchorAlbum(val album: Album, val playCount: Long? = null)

/** The service can omit the total for albums and limits the track catalogue to its first 5,000 tracks. */
data class AnchorContentPage<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val hasMore: Boolean,
    val total: Int?,
    val reportedTotal: Long? = total?.toLong(),
)

/** One page of a numbered listing. [pageSize] is never 0, so paging controls always have a divisor. */
data class Page<T>(val items: List<T>, val page: Int, val pageSize: Int, val total: Int) {
    val maxPage: Int get() = if (total == 0) 1 else (total + pageSize - 1) / pageSize
    val hasMore: Boolean get() = page < maxPage
}

data class HomeChannel(val pageId: Int, val title: String, val categoryId: Int?)

data class HomeTab(val tabId: Int, val title: String)

data class HomeContent(val sections: List<AlbumSection>, val tabs: List<HomeTab>)

data class HotWord(val word: String, val display: String)

data class BrowseCategory(val id: Int, val title: String)

/** One page of 全部专辑; [hasMore] is false once the last page has been read. */
data class AlbumListing(val albums: List<Album>, val stats: Map<String, AlbumStats>, val page: Int, val total: Int, val hasMore: Boolean)

/** The whole-site or per-category 热播榜. [wholeSite] lets the screen use its own heading for the all-categories chart. */
data class HotRanking(val section: AlbumSection, val wholeSite: Boolean)

enum class SearchKind(val code: String) { All("all"), Track("track"), Album("album"), Anchor("user") }

data class SearchPage(
    val kind: SearchKind,
    val tracks: List<Track>,
    val albums: List<Album>,
    val anchors: List<AnchorProfile>,
    val totals: Map<SearchKind, Int>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
)

data class CategoryChannel(val channelId: Int, val title: String, val relationMetadataValueId: Int, val coverUrl: String?)

data class CategoryGroup(val id: Int, val title: String, val channels: List<CategoryChannel>)

/** A filter row, such as 地区; each [CategoryValue] may open further rows beneath it. */
data class CategoryDimension(val id: Int, val title: String, val values: List<CategoryValue>)

data class CategoryValue(val id: Int, val title: String, val dimensions: List<CategoryDimension>)

data class CategoryChannelInfo(val channel: CategoryChannel, val dimensions: List<CategoryDimension>) {
    /** The rows currently reachable: a row nested under a value is shown only while that value is selected. */
    fun reachableDimensions(selections: Map<Int, Int>): List<CategoryDimension> {
        val result = mutableListOf<CategoryDimension>()
        fun visit(nodes: List<CategoryDimension>) {
            for (dimension in nodes) {
                result += dimension
                for (value in dimension.values) if (selections[dimension.id] == value.id) visit(value.dimensions)
            }
        }
        visit(dimensions)
        return result
    }

    fun pruneSelections(selections: Map<Int, Int>): Map<Int, Int> {
        val result = linkedMapOf<Int, Int>()
        for (dimension in reachableDimensions(selections)) {
            val selected = selections[dimension.id]
            for (value in dimension.values) if (value.id == selected) result[dimension.id] = value.id
        }
        return result
    }

    /** Selects [valueId] in a row (null clears it) and drops selections that are no longer reachable. */
    fun selectValue(selections: Map<Int, Int>, dimensionId: Int, valueId: Int?): Map<Int, Int> {
        val target = reachableDimensions(selections).lastOrNull { it.id == dimensionId } ?: throw XmlyException("invalid_discovery")
        if (valueId != null && target.values.none { it.id == valueId }) throw XmlyException("invalid_discovery")
        if (selections[dimensionId] == valueId) return pruneSelections(selections)
        val result = selections.toMutableMap()
        fun clearDescendants(nodes: List<CategoryDimension>) {
            for (dimension in nodes) {
                result.remove(dimension.id)
                for (value in dimension.values) clearDescendants(value.dimensions)
            }
        }
        for (value in target.values) if (value.id == selections[dimensionId]) clearDescendants(value.dimensions)
        if (valueId == null) result.remove(dimensionId) else result[dimensionId] = valueId
        return pruneSelections(result)
    }
}

data class RankTab(val id: Int, val rankingId: Int, val title: String)

data class RankCategory(val id: Int, val title: String, val tabs: List<RankTab>)

/** One chart. Albums or anchors are filled according to [contentType] (2 albums, 3 anchors). */
data class RankBlock(
    val rankingId: Int,
    val title: String,
    val contentType: Int,
    val albums: List<Album>,
    val anchors: List<AnchorProfile>,
    val stats: Map<String, AlbumStats>,
    val positions: Map<String, Int>,
)
/**
 * An album's own page. [recordDescending] is the order the server lists episodes in by default; sorting asks for the
 * other order when it differs.
 */
data class AlbumDetail(
    val album: Album,
    val stats: AlbumStats,
    val paid: Boolean,
    val anchorVerified: Boolean,
    val tags: List<String>,
    val recordDescending: Boolean,
)

/** A reply under a review. The first reply of a review may be previewed there, when the server sends it in full. */
data class AlbumReply(
    val id: String,
    val commentId: String,
    val uid: String,
    val author: String,
    val content: String,
    val createdAtMillis: Long,
    val avatarUrl: String?,
    val parentNickname: String,
    val likes: Long?,
)

/** One comment on an album. [preview] is the first reply, when the server sends it in full. */
data class AlbumReview(
    val id: String,
    val uid: String,
    val author: String,
    val content: String,
    val createdAtMillis: Long,
    val replyCount: Int,
    val likes: Long?,
    val avatarUrl: String?,
    val highQuality: Boolean,
    val preview: AlbumReply?,
)

/** The score summary above an album's reviews. Any part can be missing; the three shares are between 0 and 1. */
data class AlbumScore(
    val albumScore: Double?,
    val ratingCount: Int?,
    val commentCount: Int?,
    val recommendationScore: Double?,
    val recommendation: String,
    val recommendedCount: Int?,
    val recommended: Double?,
    val neutral: Double?,
    val notRecommended: Double?,
)

/** One page of reviews with the album's score. [hasMore] follows the server's last page number. */
data class AlbumReviews(val score: AlbumScore, val items: List<AlbumReview>, val page: Int, val hasMore: Boolean)

data class AlbumReplies(val items: List<AlbumReply>, val page: Int, val hasMore: Boolean)

/** Other albums by the same anchor, and albums listeners also play. [anchorMore] says the anchor has more than [anchorAlbums]. */
data class AlbumSimilar(val anchorAlbums: List<Album>, val anchorMore: Boolean, val recommendations: List<Album>)
