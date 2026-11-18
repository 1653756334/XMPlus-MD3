package com.xuleo.xmplus.md3.core.protocol

/** The API hosts this app talks to. The login host has its own client. */
internal enum class Host(val origin: String) {
    Mobile("https://mobile.ximalaya.com"),
    Welfare("https://m.ximalaya.com"),
    /** The website's JSON API: categories, rankings, search, public profiles. */
    Web("https://www.ximalaya.com"),
    /** The desktop site's API: category filters, ranking tabs, locating a track in its album. */
    Pc("https://pc.ximalaya.com"),
    Search("https://search.ximalaya.com"),
}

/** Whether a request carries the account's cookie. */
internal enum class Login {
    /** Sent only with an account; without one the call fails before any request is made. */
    Required,

    /** Sent with the account's cookie when one is given, anonymously otherwise. */
    Optional,

    /** Always anonymous, even if an account is at hand. */
    Never,
}

/**
 * One allowed request shape. Declaring endpoints as values, rather than matching strings at call time, is what keeps
 * the client from sending anything the original app never sent.
 *
 * @property path may contain [TimestampToken], filled with the request time.
 * @property idempotent a repeat has the same effect as one attempt, so a failed attempt may be retried; everything
 * else is sent at most once and reported as uncertain when its outcome is unknown.
 * @property successCode the `ret`/`status` value that means success.
 */
internal class Endpoint(
    val host: Host,
    val method: String,
    val path: String,
    val login: Login = Login.Required,
    val idempotent: Boolean = false,
    val successCode: String = "0",
) {
    fun resolve(timestamp: Long?): String = path.replace(TimestampToken, timestamp?.toString() ?: "")

    companion object {
        const val TimestampToken = "{ts}"
    }
}

internal object Endpoints {
    val Config = Endpoint(Host.Mobile, "POST", "/lite-mobile/audi/func/v1/config", idempotent = true)
    val Report = Endpoint(Host.Mobile, "POST", "/lite-mobile/audi/func/v1/report")
    val RewardClaim = Endpoint(Host.Welfare, "POST", "/lite-earn/ting/welfare/rewardDuration/ts-{ts}")

    private fun read(host: Host, path: String, login: Login = Login.Never, success: String = "0") =
        Endpoint(host, "GET", path, login, idempotent = true, successCode = success)

    private fun web(path: String) = read(Host.Web, path, success = "200")
    private fun pc(path: String) = read(Host.Pc, path, success = "200")

    val HomeChannels = read(Host.Mobile, "/lite-mobile/index/v1/subPages/{ts}", Login.Optional)
    val HomeModules = read(Host.Mobile, "/lite-mobile/index/v1/modules/{ts}", Login.Optional)
    val HotWords = read(Host.Search, "/hotWordBillboard/list/2.0")

    val Search = web("/revision/search")
    val AllCategories = web("/revision/category/v2/allCategory")
    val CategoryAlbums = web("/revision/category/v2/albums")
    val HotRankClusters = web("/revision/rank/v3/cluster")
    val HotRankElements = web("/revision/rank/v3/element")

    val CategoryGroups = pc("/simple-revision-for-pc/metadata/v2/group/all")
    val CategoryGroupChannels = pc("/simple-revision-for-pc/metadata/v2/group/channels")
    val CategoryChannelInfo = pc("/simple-revision-for-pc/metadata/v2/channel/info")
    val CategoryChannelAlbums = pc("/simple-revision-for-pc/metadata/v2/channel/albums")
    val RankTabs = pc("/simple-revision-for-pc/rank/v4/rankTabs")
    val RankElements = pc("/simple-revision-for-pc/rank/v4/element")
}