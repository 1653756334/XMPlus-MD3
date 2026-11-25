package com.xuleo.xmplus.md3.core.plugin

import java.io.InterruptedIOException
import java.util.Base64
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The network behind plugins' axios and fetch. A request arrives as JSON from the JS side and the reply goes back as
 * JSON, so the bridge stays one string each way. Headers are sent exactly as given (no CORS, Referer and Cookie
 * included). Cookies a server sets are kept for later requests, as React Native's shared cookie store does, except
 * when the plugin sends its own Cookie header, which then wins unchanged.
 */
class PluginHttp(base: OkHttpClient) {
    private val cookies = MemoryCookieJar()
    private val client = base.newBuilder().cookieJar(cookies).build()

    suspend fun execute(requestJson: String): String {
        val request = Json.parseToJsonElement(requestJson).jsonObject
        val call = try {
            newCall(request)
        } catch (_: IllegalArgumentException) {
            return error("network")
        }
        return try {
            call.await().use { reply(it, request.flag("binary") == true) }
        } catch (_: InterruptedIOException) {
            error("timeout")
        } catch (_: IOException) {
            error("network")
        }
    }

    private fun newCall(request: JsonObject): Call {
        val method = request.text("method") ?: "GET"
        val headers = request["headers"] as? JsonObject ?: JsonObject(emptyMap())
        val contentType = headers.entries.firstOrNull { it.key.equals("content-type", true) }?.value?.let { (it as? JsonPrimitive)?.contentOrNull }
        val body = request.text("bodyBase64")?.let { Base64.getDecoder().decode(it).toRequestBody(null) }
            ?: request.text("body")?.encodeToByteArray()?.toRequestBody(null)
        val builder = Request.Builder().url(request.text("url").orEmpty())
        headers.forEach { (name, value) -> (value as? JsonPrimitive)?.contentOrNull?.let { builder.addHeader(name, it) } }
        if (contentType == null && body != null && method !in BodylessMethods) builder.header("Content-Type", "text/plain;charset=UTF-8")
        builder.method(method, if (method in BodylessMethods) null else body ?: ByteArray(0).toRequestBody(null))

        val timeout = request.long("timeout") ?: 0L
        val ownCookie = headers.keys.any { it.equals("cookie", true) }
        val redirects = (request.int("maxRedirects") ?: 5) > 0
        val tuned = client.newBuilder()
            .callTimeout(timeout.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .followRedirects(redirects).followSslRedirects(redirects)
            .apply { if (ownCookie) cookieJar(ReadOnlyCookieJar(cookies)) }
            .build()
        return tuned.newCall(builder.build())
    }

    private fun reply(response: Response, binary: Boolean): String = buildJsonObject {
        put("status", response.code)
        put("statusText", response.message)
        put("url", response.request.url.toString())
        put("headers", buildJsonArray {
            response.headers.forEach { (name, value) -> add(buildJsonArray { add(JsonPrimitive(name)); add(JsonPrimitive(value)) }) }
        })
        if (binary) put("bodyBase64", Base64.getEncoder().encodeToString(response.body.bytes()))
        else put("body", response.body.string())
    }.toString()

    private fun error(code: String) = buildJsonObject { put("error", code) }.toString()

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
        })
    }

    private fun JsonObject.text(name: String) = (this[name] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.long(name: String) = (this[name] as? JsonPrimitive)?.longOrNull
    private fun JsonObject.int(name: String) = (this[name] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.flag(name: String) = (this[name] as? JsonPrimitive)?.booleanOrNull

    private companion object {
        val BodylessMethods = setOf("GET", "HEAD")
    }
}

/** Cookies set by servers, kept for this app run, shared by all plugins as in MusicFree. */
private class MemoryCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cookie ->
            this.cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
            this.cookies += cookie
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt < now }
        return cookies.filter { it.matches(url) }
    }
}

/** Stores what the server sets but adds nothing, so a plugin's own Cookie header goes out unchanged. */
private class ReadOnlyCookieJar(private val store: CookieJar) : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = store.saveFromResponse(url, cookies)
    override fun loadForRequest(url: HttpUrl): List<Cookie> = emptyList()
}
