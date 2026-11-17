package com.xuleo.xmplus.md3.core.protocol

import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.XmlyException
import java.io.IOException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class LoginFailure(code: String, val upstreamCode: Int? = null, retryAfter: Duration? = null, definitive: Boolean = false) :
    XmlyException(code, retryAfter = retryAfter, definitive = definitive)

class SmsChallenge(val id: String) {
    override fun toString() = "SmsChallenge(redacted)"
}

/** Rule-for-rule port of XMPlus xmly_login.dart. One application session owns its cooldowns. */
class XmlyLogin internal constructor(
    private val transport: LoginTransport,
    private val clock: () -> Long,
    private val monotonic: () -> Long,
    private val random: SecureRandom,
) {
    constructor() : this(HttpLoginTransport(), System::currentTimeMillis, { System.nanoTime() / 1_000_000 }, SecureRandom())

    private class Pending(val id: String, val mobile: String, val device: String, val expires: Long, var attempts: Int = 0)
    private val mutex = Mutex()
    private val metadataLock = Any()
    private val device = UUID.randomUUID().toString()
    private val generation = AtomicInteger()
    private val cleared = AtomicReference(CompletableDeferred<Unit>())
    private val lastSms = LinkedHashMap<String, Long>()
    private val usedLots = LinkedHashMap<String, Long>()
    @Volatile private var pending: Pending? = null
    @Volatile private var closed = false

    fun clear() {
        generation.incrementAndGet()
        pending = null
        cleared.getAndSet(CompletableDeferred()).complete(Unit)
        // Unknown send outcomes still own their cooldown and used captcha lot.
    }

    fun close() { closed = true; clear() }

    fun smsCooldownSeconds(mobile: String): Int = synchronized(metadataLock) {
        val stamp = lastSms[phoneKey(mobile)] ?: return@synchronized 0
        val remaining = 180_000 - (monotonic() - stamp)
        if (remaining <= 0) 0 else ((remaining + 999) / 1000).toInt()
    }

    fun challengeRemainingSeconds(id: String): Int {
        val value = pending ?: return 0
        if (!sameChallenge(value.id, id)) return 0
        return ((value.expires - monotonic()).coerceAtLeast(0) + 999).div(1000).toInt()
    }

    suspend fun sendSms(mobile: String, proof: GeetestProof, deviceId: String = ""): SmsChallenge {
        if (!validPhone(mobile)) fail("invalid_mobile")
        if (deviceId.length > 128 || deviceId.any { it.code <= 32 || it.code >= 127 || it in ";&,\"\\" }) fail("invalid_device")
        val epoch = generation.get()
        val abort = cleared.get()
        return mutex.withLock {
            checkSession(epoch)
            val phone = phoneKey(mobile)
            val lot = hash("SHA-256", proof.lot)
            synchronized(metadataLock) {
                val now = monotonic()
                if (usedLots[lot]?.let { now - it < 600_000 } == true) fail("captcha_reused")
                val cooldown = smsCooldownSeconds(mobile)
                if (cooldown > 0) throw LoginFailure("sms_cooldown", retryAfter = (cooldown * 1000L).milliseconds)
                prune(now)
            }
            val selectedDevice = deviceId.ifEmpty { device }
            val encrypted = encryptMobile(mobile, random)
            val nonce = nonce(selectedDevice, epoch, abort)
            checkSession(epoch)
            post("mobile/sms/v3/send", selectedDevice, linkedMapOf(
                "mobile" to encrypted, "sendType" to "1", "biz" to "1", "nonce" to nonce,
                "fdsOtp" to proof.toJson().toString(),
            ), "sms_send", epoch, abort, beforeAttempt = {
                synchronized(metadataLock) {
                    val sentAt = monotonic()
                    usedLots[lot] = sentAt
                    lastSms[phone] = sentAt
                }
                pending = null
            }) { Unit }
            checkSession(epoch)
            val id = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
            pending = Pending(id, encrypted, selectedDevice, monotonic() + 600_000)
            SmsChallenge(id)
        }
    }

    suspend fun verifySms(challengeId: String, code: String): Credentials {
        if (!Regex("[A-Za-z0-9_-]{1,128}").matches(challengeId)) fail("challenge_invalid")
        if (!validCode(code)) fail("invalid_code")
        val epoch = generation.get()
        val abort = cleared.get()
        return mutex.withLock {
            checkSession(epoch)
            val value = pending ?: fail("challenge_invalid")
            if (!sameChallenge(value.id, challengeId)) fail("challenge_invalid")
            active(value)
            try {
                var nonce = nonce(value.device, epoch, abort)
                active(value)
                value.attempts++
                val key = post("mobile/sms/v2/verify", value.device,
                    linkedMapOf("mobile" to value.mobile, "code" to code, "nonce" to nonce),
                    "sms_verify", epoch, abort) { result ->
                    string(fallback(result["bizKey"], result["smsKey"]))
                        ?.takeIf { it.isNotEmpty() && it.length <= 8192 } ?: fail("invalid_response")
                }
                active(value)
                nonce = nonce(value.device, epoch, abort)
                active(value)
                val credentials = post("mobile/login/quick/v2", value.device,
                    linkedMapOf("mobile" to value.mobile, "smsKey" to key, "nonce" to nonce),
                    "quick_login", epoch, abort) { result ->
                    val nested = result["data"] as? JsonObject
                    val uid = fallback(result["uid"], nested?.get("uid")) as? JsonPrimitive ?: fail("invalid_response")
                    val token = string(fallback(result["token"], nested?.get("token"))) ?: fail("invalid_response")
                    if (!Regex("[1-9][0-9]{0,19}").matches(uid.content) ||
                        (!uid.isString && uid.content.toLongOrNull() == null) ||
                        !Regex("[A-Za-z0-9._~+/=\\-]{1,8192}").matches(token)) fail("invalid_response")
                    Credentials("${uid.content}&$token", value.device)
                }
                active(value)
                pending = null
                credentials
            } finally {
                if (value.attempts >= 5 && pending === value) pending = null
            }
        }
    }

    private fun active(value: Pending) {
        if (pending !== value) fail("challenge_invalid")
        if (monotonic() >= value.expires) { pending = null; fail("challenge_expired") }
    }

    private fun prune(now: Long) {
        for ((map, ttl) in listOf(lastSms to 180_000L, usedLots to 600_000L)) {
            map.entries.removeAll { now - it.value >= ttl }
            while (map.size >= 128) map.remove(map.keys.first())
        }
        if (pending?.let { now >= it.expires } == true) pending = null
    }

    private suspend fun nonce(device: String, epoch: Int, abort: CompletableDeferred<Unit>): String =
        request(LoginRequest("GET", "mobile/nonce/${clock()}", device, null), epoch, abort) { result ->
            if (result.containsKey("ret")) checkResult(result, "nonce")
            string(result["nonce"])?.takeIf { it.isNotEmpty() && it.length <= 1024 } ?: fail("invalid_response")
        }

    private suspend fun <T> post(path: String, device: String, values: Map<String, String>, operation: String,
        epoch: Int, abort: CompletableDeferred<Unit>, beforeAttempt: () -> Unit = {}, decode: (JsonObject) -> T): T =
        request(LoginRequest("POST", path, device, LinkedHashMap(values).apply { put("signature", signature(values)) }),
            epoch, abort, beforeAttempt) { result -> checkResult(result, operation); decode(result) }

    private suspend fun <T> request(request: LoginRequest, epoch: Int, abort: CompletableDeferred<Unit>,
        beforeAttempt: () -> Unit = {}, decode: (JsonObject) -> T): T {
        repeat(5) { attempt ->
            checkSession(epoch)
            var status: Int? = null
            var retryHeader: String? = null
            var transportError: Throwable? = null
            val failure = try {
                val response = interruptible(abort, epoch) { transport.exchange(request) {
                    checkSession(epoch)
                    beforeAttempt()
                } }
                status = response.status
                retryHeader = response.retryAfter
                if (status != 200) throw LoginFailure("http_error",
                    retryAfter = retryHeader?.let { maxOf(1000L shl attempt, parseRetryAfter(it, clock()) ?: 0).milliseconds })
                val result = try {
                    Json.parseToJsonElement(decodeUtf8Strict(response.body)) as? JsonObject ?: fail("invalid_response")
                } catch (_: Exception) { fail("invalid_response") }
                checkSession(epoch)
                return decode(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                checkSession(epoch)
                if (error is HttpTransportException) { status = error.status; retryHeader = error.retryAfter; transportError = error.cause }
                else if (error is IOException) transportError = error
                when {
                    error is LoginFailure -> error
                    status != null && status != 200 -> LoginFailure("http_error")
                    else -> LoginFailure("network_error")
                }
            }
            val getStatusRetry = request.method == "GET" && (status == 408 || status == 425 ||
                status?.let { it in 500..599 && it != 501 && it != 505 } == true)
            val retry = (failure.code == "http_error" && (status == 429 || getStatusRetry)) ||
                (failure.definitive && failure.upstreamCode == 429) ||
                (request.method == "GET" && transportError != null && transportError !is SSLException)
            if (!retry || attempt == 4) throw failure
            interruptible(abort, epoch) { delay(maxOf(1000L shl attempt, parseRetryAfter(retryHeader, clock()) ?: 0)) }
        }
        fail("network_error")
    }

    private suspend fun <T> interruptible(abort: CompletableDeferred<Unit>, epoch: Int, action: suspend () -> T): T = coroutineScope {
        val work = async { action() }
        select {
            work.onAwait { it }
            abort.onAwait { checkSession(epoch); fail("challenge_invalid") }
        }
    }

    private fun checkSession(expected: Int) {
        if (closed) fail("closed")
        if (generation.get() != expected) fail("challenge_invalid")
    }

    companion object {
        const val CaptchaId = "3723312ce42a04b5c0b40e605a882037"
        private const val Salt = "MOBILE-V1-PRODUCT-7D74899B338B4F348E2383970CC09991E8E8D8F2BC744EF0BEE94D76D718C089"
        private const val Modulus = "9585a4773abeecb949701d49762f2dfab9599ba19dfe1e1a2fa200e32e0444f426da528912" +
            "d9ea8669515f6f1014c454e1343b97abf7c10fe49d520a6999c66b230e0730c3f8" +
            "02d136a892501ff2b13d699b5c7ecbbfef428ac36d3d83a5bd627f18746a7fdc77" +
            "4c12a38de2760a3b95c653c10d7eb7f84722976251f649556b"

        fun validPhone(value: String) = Regex("\\+?[0-9]{6,18}").matches(value)
        fun validCode(value: String) = Regex("[0-9]{4,8}").matches(value)
        fun signature(values: Map<String, String>): String = hash("SHA-1",
            values.keys.sorted().joinToString("") { "$it=${values.getValue(it)}&" }.uppercase(Locale.ROOT) + Salt)

        internal fun encryptMobile(value: String, random: SecureRandom): String {
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(BigInteger(Modulus, 16), BigInteger.valueOf(65537)))
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding").apply { init(Cipher.ENCRYPT_MODE, publicKey, random) }
            return Base64.getEncoder().encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
        }

        private fun checkResult(result: JsonObject, operation: String) {
            val raw = result["ret"] as? JsonPrimitive ?: fail("invalid_response")
            if (!Regex("-?[0-9]{1,9}").matches(raw.content)) fail("invalid_response")
            val code = raw.content.toInt()
            if (code != 0) throw LoginFailure("${operation}_rejected", upstreamCode = code, definitive = true)
        }

        private fun sameChallenge(left: String, right: String): Boolean {
            var difference = left.length xor right.length
            for (index in 0 until maxOf(left.length, right.length)) difference = difference or
                ((left.getOrNull(index)?.code ?: 0) xor (right.getOrNull(index)?.code ?: 0))
            return difference == 0
        }

        private fun hash(algorithm: String, value: String) = MessageDigest.getInstance(algorithm)
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        private fun phoneKey(value: String) = hash("SHA-256", value.removePrefix("+"))
        private fun string(value: JsonElement?) = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
        private fun fallback(value: JsonElement?, other: JsonElement?): JsonElement? {
            val primitive = value as? JsonPrimitive
            return if (value == null || value == JsonNull || (primitive?.isString == true && primitive.content.isEmpty()) ||
                (primitive?.isString == false && (primitive.content == "false" || primitive.content.toDoubleOrNull() == 0.0))) other else value
        }
        private fun fail(code: String): Nothing = throw LoginFailure(code)
    }
}
