package com.xuleo.xmplus.md3.core.protocol

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class LoginRequest(val method: String, val path: String, val device: String, val values: Map<String, String>?)
internal class LoginResponse(val status: Int, val retryAfter: String?, val body: ByteArray)

internal fun interface LoginTransport {
    suspend fun exchange(request: LoginRequest, beforeAttempt: () -> Unit): LoginResponse
}

/** One bounded attempt. The login state machine alone decides whether replay is safe. */
internal class HttpLoginTransport(private val executor: HttpExecutor = OkHttpExecutor(newHttpClient(writeTimeoutSeconds = 10))) : LoginTransport {
    override suspend fun exchange(request: LoginRequest, beforeAttempt: () -> Unit): LoginResponse {
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
        beforeAttempt()
        val reply = executor.execute(builder.build(), MaxBytes)
        return LoginResponse(reply.status, reply.retryAfter, reply.body ?: throw LoginFailure("response_too_large"))
    }

    private companion object { const val MaxBytes = 512 * 1024 }
}