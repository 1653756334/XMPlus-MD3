package com.xuleo.xmplus.md3.core.protocol

import com.xuleo.xmplus.md3.core.model.AlbumSkip
import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.MaxSkipSeconds
import com.xuleo.xmplus.md3.core.model.xmlyIdentifier
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** The intro and outro lengths Ximalaya suggests for an album. Reading them never spends listening time. */
class XmlySkip(private val http: XmlyHttp) {
    private val reader = Reader("invalid_skip_recommendation")

    /** The suggestion for [albumId], or null when the server has none. */
    suspend fun recommendation(credentials: Credentials, albumId: String): AlbumSkip? {
        val response = http.call(
            Endpoints.AlbumSkipRecommendation, credentials, System.currentTimeMillis(),
            query = mapOf("albumId" to xmlyIdentifier(albumId), "isShowRecommend" to "true"),
        )
        val data = reader.obj(response["data"])
        if (!data.containsKey("recommendSkip")) reader.invalid()
        val row = data["recommendSkip"]
        if (row == null || row is JsonNull) return null
        val suggestion = reader.obj(row)
        return AlbumSkip(seconds(suggestion["recommendHeadSkip"]), seconds(suggestion["recommendHeadTail"]))
    }

    /** Milliseconds as sent, rounded to whole seconds. */
    private fun seconds(value: JsonElement?): Int {
        val milliseconds = (value as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
        if (milliseconds == null || !milliseconds.isFinite() || milliseconds < 0) reader.invalid()
        val rounded = Math.round(milliseconds / 1000.0)
        if (rounded > MaxSkipSeconds) reader.invalid()
        return rounded.toInt()
    }
}