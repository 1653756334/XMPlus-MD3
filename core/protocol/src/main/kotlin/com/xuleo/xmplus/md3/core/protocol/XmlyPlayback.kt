package com.xuleo.xmplus.md3.core.protocol

import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.Quota
import com.xuleo.xmplus.md3.core.model.Track
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.model.xmlyIdentifier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Account-scoped authorization, never put into a Bundle or stored on disk. */
class PlaybackAuthorization internal constructor(
    val track: Track,
    val url: HttpUrl,
    val accountIdentity: String,
    val credentialsVersion: String?,
    val requiresListeningTime: Boolean,
)

/** Resolves full audio using the provider's current authorization. The list's paid badge is not a billing input. */
class XmlyPlayback(
    private val http: XmlyHttp,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val reader = Reader("invalid_authorization")

    suspend fun resolve(
        trackId: String,
        credentials: Credentials?,
        quota: suspend (String) -> Quota,
    ): PlaybackAuthorization {
        val id = xmlyIdentifier(trackId)
        var signedIn = credentials
        var info = if (credentials == null) publicInfo(id) else try {
            accountInfo(id, credentials)
        } catch (failure: XmlyException) {
            if (failure.code != "login_expired") throw failure
            signedIn = null
            publicInfo(id)
        }
        checkId(info, id)
        val needsTime = !free(info) && info.flag("isAuthorized") != true
        if (needsTime) {
            val account = signedIn ?: fail(if (credentials == null) "login_required" else "login_expired")
            val balance = quota(account.identity)
            if (!balance.newDevice && balance.remainingSeconds <= 0) fail("insufficient_quota")
            info = accountInfo(id, account, balance)
            checkId(info, id)
        }
        rejectPreview(info)
        val track = reader.trackOf(info)
        val url = audioCandidates(info, signedIn != null).let { candidates ->
            Qualities.firstNotNullOfOrNull(candidates::get)
        } ?: if (signedIn != null && !free(info)) paidUrl(track, signedIn) else fail("permission_denied")
        return PlaybackAuthorization(track, url, credentials?.identity ?: "anonymous", credentials?.cacheIdentity, needsTime)
    }

    private suspend fun publicInfo(id: String) =
        http.call(Endpoints.TrackPublicInfo, null, query = AndroidDevice, trackId = id)

    private suspend fun accountInfo(id: String, account: Credentials, balance: Quota? = null): JsonObject {
        val params = buildJsonObject {
            put("device", "android")
            put("trackId", id)
            if (balance != null) {
                put("a1", if (balance.newDevice) "1" else balance.remainingSeconds.toString())
                put("a2", clock().toString())
                put("a3", "0")
            }
        }
        val body = buildJsonObject { put("data", XmlyCrypto.encrypt(params, XmlyCrypto.PlayKey)) }
        val reply = http.call(Endpoints.TrackAccountInfo, account, body = body)
        val payload = (reply["data"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: fail("invalid_authorization")
        val plaintext = try {
            XmlyCrypto.decrypt(payload, XmlyCrypto.PlayKey)
        } catch (failure: XmlyException) {
            throw XmlyException("invalid_authorization", cause = failure)
        }
        val wrapper = parseJson(plaintext, "invalid_authorization") as? JsonObject ?: fail("invalid_authorization")
        return parseEmbedded(wrapper["trackInfo"], "invalid_authorization") as? JsonObject
            ?: fail("invalid_authorization")
    }

    private fun audioCandidates(info: JsonObject, signedIn: Boolean): Map<String, HttpUrl> = buildMap {
        fun add(quality: String, field: JsonElement?, encrypted: Boolean = false) {
            val raw = (field as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf(String::isNotEmpty) ?: return
            if (quality in this) return
            val url = if (encrypted && !raw.startsWith("https://") && !raw.startsWith("http://")) {
                XmlyCrypto.decrypt(raw, XmlyCrypto.PlayKey)
            } else raw
            put(quality, validatePlaybackUrl(url))
        }
        add("mp3_64", info["playUrl64"])
        add("mp3_32", info["playUrl32"])
        if (signedIn) {
            val raw = parseEmbedded(info["playUrlList"], "invalid_media")
            if (raw != null && raw != JsonNull) {
                val rows = raw as? JsonArray ?: fail("invalid_media")
                for (row in rows.filterIsInstance<JsonObject>()) {
                    val quality = reader.text(row["type"]).lowercase()
                    if (quality in Qualities) add(quality, row["url"], encrypted = true)
                }
            }
        }
    }

    private suspend fun paidUrl(track: Track, account: Credentials): HttpUrl {
        val metadata = http.call(Endpoints.TrackPaidMedia, account, clock(), query = AndroidDevice, trackId = track.id)
        val ret = metadata["ret"] as? JsonPrimitive
        if (ret == null || ret.isString || reader.integer(ret) != 0L) fail("permission_denied")
        checkId(metadata, track.id)
        if (track.albumId.isEmpty() || reader.anyText(metadata["albumId"]) != track.albumId) fail("id_mismatch")
        if (metadata.flag("isAuthorized") != true) fail("permission_denied")
        rejectPreview(metadata)
        return validatePlaybackUrl(XmlyCrypto.paidMediaUrl(metadata, account.uid))
    }

    private fun checkId(info: JsonObject, id: String) {
        if (reader.anyText(info["trackId"]?.takeUnless { it == JsonNull } ?: info["id"]) != id) fail("id_mismatch")
    }

    private fun rejectPreview(info: JsonObject) {
        if (info.flag("isPreview") == true || info.flag("isTrial") == true) fail("preview_only")
    }

    private fun free(info: JsonObject) =
        info.flag(if (info["isPaid"]?.takeUnless { it == JsonNull } != null) "isPaid" else "is_paid") == false

    private fun JsonObject.flag(key: String) =
        (get(key) as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull

    private fun parseEmbedded(value: JsonElement?, code: String): JsonElement? =
        if (value is JsonPrimitive && value.isString) parseJson(value.content, code) else value

    private fun parseJson(value: String, code: String) =
        try {
            Json.parseToJsonElement(value)
        } catch (failure: IllegalArgumentException) {
            throw XmlyException(code, cause = failure)
        }

    private companion object {
        val AndroidDevice = mapOf("device" to "android")
        val Qualities = listOf("m4a_128", "m4a_64", "mp3_64", "mp3_32", "m4a_24")
    }
}

/** Used for every Media3 request, including redirect hops; never add API headers to a media request. */
fun validatePlaybackUrl(raw: String): HttpUrl {
    if (raw.length > 16384 || raw.any { it.code < 32 }) fail("invalid_media")
    val url = raw.toHttpUrlOrNull() ?: fail("invalid_media")
    if (url.encodedUsername.isNotEmpty() || url.encodedPassword.isNotEmpty() ||
        url.fragment != null || (url.port != 80 && url.port != 443)
    ) fail("invalid_media")
    if ("_preview_" in url.encodedPath.lowercase()) fail("preview_only")
    if (url.host == "xmcdn.com" || url.host.endsWith(".xmcdn.com")) return url
    if (url.host == "mobile.ximalaya.com" && url.encodedQuery == null && RedirectPath.matches(url.encodedPath)) {
        return url.newBuilder().scheme("https").port(443).build()
    }
    fail("invalid_media")
}

private val RedirectPath = Regex("^/mobile/redirect/free/play/[1-9][0-9]{0,18}(?:/[01])?$")
private fun fail(code: String): Nothing = throw XmlyException(code, definitive = true)
