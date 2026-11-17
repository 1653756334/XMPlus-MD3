package com.xuleo.xmplus.md3.core.protocol

import java.io.IOException
import java.math.BigInteger
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal class LoginRequest(val method: String, val path: String, val device: String, val values: Map<String, String>?)
internal class LoginResponse(val status: Int, val retryAfter: String?, val body: ByteArray)
internal class LoginTransportFailure(val status: Int?, val retryAfter: String?, cause: IOException) : IOException(cause)

internal fun interface LoginTransport {
    suspend fun exchange(request: LoginRequest, beforeAttempt: () -> Unit): LoginResponse
}

/** One bounded attempt. The login state machine alone decides whether replay is safe. */
internal class HttpLoginTransport : LoginTransport {
    private val http = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false).cookieJar(CookieJar.NO_COOKIES)
        .retryOnConnectionFailure(false).connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS).build()

    override suspend fun exchange(request: LoginRequest, beforeAttempt: () -> Unit): LoginResponse =
        suspendCancellableCoroutine { continuation ->
            val builder = Request.Builder().url("https://passport.ximalaya.com/" + request.path)
                .header("User-Agent", "ting_9.5.1(M2102J2SC,Android31)")
                .header("Accept", "application/json, text/plain, */*")
                .header("Cookie2", "\$version=1")
                .header("Content-Type", "application/json")
                .header("Cookie", "1&_device=android&${request.device}&9.5.1;channel=and-d10;" +
                    "impl=com.ximalaya.ting.android;qimei36=;osversion=31;device_model=M2102J2SC;" +
                    "XIM=;net-mode=WIFI;manufacturer=Xiaomi;")
            if (request.method == "POST") {
                val json = JsonObject(requireNotNull(request.values).mapValues { JsonPrimitive(it.value) })
                builder.post(json.toString().toRequestBody("application/json".toMediaType()))
            }
            val call = http.newCall(builder.build())
            continuation.invokeOnCancellation { call.cancel() }
            if (!continuation.isActive) return@suspendCancellableCoroutine
            try {
                beforeAttempt()
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWith(Result.failure(LoginTransportFailure(null, null, e)))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = runCatching {
                            response.use {
                                val body = response.body
                                if (body.contentLength() > MaxBytes) throw LoginFailure("response_too_large")
                                val source = body.source()
                                try {
                                    if (source.request(MaxBytes + 1L)) throw LoginFailure("response_too_large")
                                    LoginResponse(response.code, response.header("Retry-After"), source.readByteArray())
                                } catch (error: IOException) {
                                    throw LoginTransportFailure(response.code, response.header("Retry-After"), error)
                                }
                            }
                        }
                        if (continuation.isActive) continuation.resumeWith(result)
                    }
                })
            } catch (failure: Exception) {
                call.cancel()
                if (continuation.isActive) continuation.resumeWith(Result.failure(failure))
            }
        }

    private companion object { const val MaxBytes = 512 * 1024 }
}

/** Same uncapped server cooldown used by the Dart login transport. */
internal fun loginRetryAfter(header: String?, now: Long): Long? {
    val value = header?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (value.all { it in '0'..'9' }) return BigInteger(value).multiply(BigInteger.valueOf(1000))
        .min(BigInteger.valueOf((8_640_000_000_000_000L - now).coerceAtLeast(0))).toLong()
    for (pattern in listOf("EEE, dd MMM yyyy HH:mm:ss 'GMT'", "EEEE, dd-MMM-yyyy HH:mm:ss 'GMT'", "EEE MMM d HH:mm:ss yyyy")) {
        val parser = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT"); isLenient = false }
        val position = ParsePosition(0)
        val date = parser.parse(value, position)
        if (date != null && position.index == value.length) return (date.time - now).coerceAtLeast(0)
    }
    return null
}
