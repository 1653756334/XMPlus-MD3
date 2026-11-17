package com.xuleo.xmplus.md3.core.model

private val TokenPattern = Regex("[1-9][0-9]{0,19}&[A-Za-z0-9._~+/=\\-]{1,8192}")
private val DeviceIdPattern = Regex("[A-Za-z0-9_.:\\-]{1,128}")
private val IdentifierPattern = Regex("(?:xmly:)?([1-9][0-9]{0,18})")

/** A Ximalaya account login: a `uid&token` pair and the device id it was issued to. */
class Credentials(token: String, deviceId: String) {
    val token: String = token.dartTrim().removePrefix("1&_token=")
    val deviceId: String = deviceId.dartTrim()

    init {
        if (!TokenPattern.matches(this.token) || !DeviceIdPattern.matches(this.deviceId)) {
            throw XmlyException("invalid_credentials", definitive = true)
        }
    }

    val uid: String get() = token.substringBefore('&')
}

/** Normalises a Ximalaya id: a positive integer, optionally written `xmly:<id>`. */
fun xmlyIdentifier(value: String): String {
    val match = IdentifierPattern.matchEntire(value) ?: throw XmlyException("invalid_id", definitive = true)
    return match.groupValues[1]
}