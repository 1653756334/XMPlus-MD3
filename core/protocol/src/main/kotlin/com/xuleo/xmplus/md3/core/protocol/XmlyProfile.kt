package com.xuleo.xmplus.md3.core.protocol

import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.XmlyException

/** The signed-in account's own public profile, as XMPlus xmly_profile.dart reads it. Only the avatar is used. */
class XmlyProfile(private val http: XmlyHttp, private val clock: () -> Long = System::currentTimeMillis) {
    private val reader = Reader("invalid_profile")

    /** The account's avatar, or null when the profile carries no usable one. */
    suspend fun avatar(credentials: Credentials): String? {
        val uid = credentials.uid
        if (!UidPattern.matches(uid)) throw XmlyException("invalid_id", definitive = true)
        val response = http.call(
            Endpoints.AccountProfile, credentials, clock(), query = mapOf("device" to "android", "uid" to uid),
        )
        if (reader.anyText(response["uid"]) != uid) reader.invalid()
        return reader.cover(response["mobileMiddleLogo"], response["mobileSmallLogo"], response["mobileLargeLogo"])
    }

    private companion object {
        val UidPattern = Regex("^[1-9][0-9]{0,19}$")
    }
}
