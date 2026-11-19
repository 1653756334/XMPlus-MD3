package com.xuleo.xmplus.md3.core.protocol

import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.model.dartTrim
import com.xuleo.xmplus.md3.core.model.xmlyIdentifier
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 文稿: the official manuscript of one episode, its lyric or else its draft. The promotional introduction is never
 * used. The read is public, so it never carries an account.
 */
class XmlyTranscript(private val http: XmlyHttp) {
    private val reader = Reader("invalid_transcript")

    /** Null when the episode has no manuscript text. */
    suspend fun manuscript(trackId: String): String? {
        val number = xmlyIdentifier(trackId)
        val response = http.call(Endpoints.TrackSimple, null, query = mapOf("trackId" to number))
        val data = response["data"] as? JsonObject ?: reader.invalid()
        val info = data["trackInfo"] as? JsonObject ?: reader.invalid()
        if (info.containsKey("trackId") || info.containsKey("id")) {
            val id = reader.anyText(info["trackId"]).ifEmpty { reader.anyText(info["id"]) }
            if (id != number) throw XmlyException("id_mismatch", definitive = true)
        }
        val lyric = text(info["lyric"])
        val draft = text(info["draft"])
        return lyric?.takeIf { it.dartTrim().isNotEmpty() } ?: draft?.takeIf { it.dartTrim().isNotEmpty() }
    }

    private fun text(value: JsonElement?): String? = when (value) {
        null, JsonNull -> null
        is JsonPrimitive -> if (value.isString) value.content else reader.invalid()
        else -> reader.invalid()
    }
}
