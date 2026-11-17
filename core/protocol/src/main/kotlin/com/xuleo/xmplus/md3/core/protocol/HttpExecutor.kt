package com.xuleo.xmplus.md3.core.protocol

import java.io.IOException
import java.math.BigInteger
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import com.xuleo.xmplus.md3.core.model.dartTrim
import kotlin.coroutines.resumeWithException

/** [body] is null when the response exceeded the byte limit; the status and headers are still valid. */
internal class HttpReply(val status: Int, val retryAfter: String?, val body: ByteArray?)

/** A failed exchange. [status] is set when the response head arrived before the body broke off. */
internal class HttpTransportException(val status: Int?, val retryAfter: String?, cause: IOException) : IOException(cause)

/** One bounded HTTP exchange, with no retry policy. Both the API client and the login client sit on it. */
internal fun interface HttpExecutor {
    suspend fun execute(request: Request, maxBytes: Int): HttpReply
}

internal class OkHttpExecutor(private val http: OkHttpClient = newHttpClient()) : HttpExecutor {
    override suspend fun execute(request: Request, maxBytes: Int): HttpReply = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(HttpTransportException(null, null, e))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use { read(it, maxBytes) } }
                if (continuation.isActive) continuation.resumeWith(result)
            }
        })
    }

    private fun read(response: Response, maxBytes: Int): HttpReply {
        val retryAfter = response.header("Retry-After")
        val body = response.body
        if (body.contentLength() > maxBytes) return HttpReply(response.code, retryAfter, null)
        try {
            val source = body.source()
            if (source.request(maxBytes + 1L)) return HttpReply(response.code, retryAfter, null)
            return HttpReply(response.code, retryAfter, source.readByteArray())
        } catch (error: IOException) {
            throw HttpTransportException(response.code, retryAfter, error)
        }
    }
}

/** No redirects, no cookie jar and no silent connection retry: every attempt is visible to the caller. */
internal fun newHttpClient(writeTimeoutSeconds: Long = 25): OkHttpClient = OkHttpClient.Builder()
    .followRedirects(false).followSslRedirects(false).cookieJar(CookieJar.NO_COOKIES)
    .retryOnConnectionFailure(false).connectTimeout(10, TimeUnit.SECONDS)
    .writeTimeout(writeTimeoutSeconds, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
    .callTimeout(25, TimeUnit.SECONDS).build()

internal const val MaxDartDate = 8_640_000_000_000_000L

private val HttpDatePatterns = listOf("EEE, dd MMM yyyy HH:mm:ss 'GMT'", "EEEE, dd-MMM-yyyy HH:mm:ss 'GMT'", "EEE MMM d HH:mm:ss yyyy")

/**
 * The server's cooldown in milliseconds, with no policy cap: values past Dart's DateTime range saturate at the
 * latest representable deadline. Null when the header is absent or is neither seconds nor an HTTP date.
 */
internal fun parseRetryAfter(header: String?, now: Long): Long? {
    val value = header?.dartTrim()?.takeIf { it.isNotEmpty() } ?: return null
    if (value.all { it in '0'..'9' }) {
        return BigInteger(value).multiply(BigInteger.valueOf(1000))
            .min(BigInteger.valueOf((MaxDartDate - now).coerceAtLeast(0))).toLong()
    }
    // dart:io HttpDate accepts these three historical formats.
    for (pattern in HttpDatePatterns) {
        val parser = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT"); isLenient = false }
        val position = ParsePosition(0)
        val date = parser.parse(value, position)
        if (date != null && position.index == value.length) return (date.time - now).coerceAtLeast(0)
    }
    return null
}