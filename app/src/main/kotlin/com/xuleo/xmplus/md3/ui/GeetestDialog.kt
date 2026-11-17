package com.xuleo.xmplus.md3.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.protocol.GeetestProof
import com.xuleo.xmplus.md3.core.protocol.XmlyLogin
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Duration.Companion.seconds

private const val AssetHost = "appassets.androidplatform.net"
private const val AssetOrigin = "https://$AssetHost"
private const val BridgeName = "xmplusCaptcha"
private val LoadTimeout = 45.seconds
private val AllowedDomains = listOf("geetest.com", "geevisit.com", "gsensebot.com")

/**
 * The official Geetest v4 check in a locked-down WebView. The page is served by the app, only
 * Geetest hosts load, and results are accepted from this page's origin with this challenge's nonce.
 */
@Composable
internal fun GeetestDialog(onProof: (GeetestProof) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val language = if (LocalConfiguration.current.locales[0].language == "zh") "zho" else "eng"
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) }
    val latestProof by rememberUpdatedState(onProof)
    val latestDismiss by rememberUpdatedState(onDismiss)
    val session = remember {
        CaptchaSession(
            template = captchaTemplate(context),
            language = language,
            onReady = { ready = true },
            onFailure = { failed = true },
            onProof = { latestProof(it) },
            onClosed = { latestDismiss() },
        )
    }
    LaunchedEffect(session) {
        delay(LoadTimeout)
        if (!ready) failed = true
    }
    DisposableEffect(session) { onDispose { session.stop() } }

    val height = minOf(440.dp, LocalConfiguration.current.screenHeightDp.dp * 0.6f)
    AlertDialog(
        onDismissRequest = { session.stop(); onDismiss() },
        title = { Text(stringResource(R.string.captcha_title)) },
        text = {
            Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
                if (failed) {
                    Text(stringResource(R.string.captcha_failed))
                } else {
                    CaptchaWebView(session)
                    if (!ready) CircularProgressIndicator()
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { session.stop(); onDismiss() }) { Text(stringResource(R.string.action_close)) }
        },
        properties = DialogProperties(dismissOnClickOutside = false),
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CaptchaWebView(session: CaptchaSession) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    setSupportMultipleWindows(false)
                    javaScriptCanOpenWindowsAutomatically = false
                    mediaPlaybackRequiresUserGesture = true
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    setGeolocationEnabled(false)
                }
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                setDownloadListener { _, _, _, _, _ -> session.fail() }
                webViewClient = session.client
                WebViewCompat.addWebMessageListener(this, BridgeName, setOf(AssetOrigin)) { view, message, origin, isMainFrame, _ ->
                    session.receive(view, message, origin, isMainFrame)
                }
                loadUrl(session.documentUrl)
            }
        },
        onRelease = { view ->
            // The challenge profile holds nothing worth keeping.
            view.stopLoading()
            view.clearCache(true)
            view.clearHistory()
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
            view.destroy()
        },
    )
}

private fun captchaTemplate(context: Context): String =
    context.resources.openRawResource(R.raw.geetest_document).use { it.readBytes() }.decodeToString()

private fun newNonce(): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))

/** One challenge: its served document, its nonce, and what it will accept from the page. */
private class CaptchaSession(
    template: String,
    language: String,
    private val onReady: () -> Unit,
    private val onFailure: () -> Unit,
    private val onProof: (GeetestProof) -> Unit,
    private val onClosed: () -> Unit,
) {
    private val nonce = newNonce()
    private val document = template
        .replace("%NONCE%", Json.encodeToString(nonce))
        .replace("%CAPTCHA_ID%", Json.encodeToString(XmlyLogin.CaptchaId))
        .replace("%LANGUAGE%", Json.encodeToString(language))
        .toByteArray()
    private var accepting = true
    val documentUrl = "$AssetOrigin/xmplus-captcha/$nonce/index.html"

    val client = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            request.isForMainFrame && !(request.url.toString() == "about:blank" || (accepting && request.url.toString() == documentUrl))

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = when {
            request.isForMainFrame -> if (accepting && request.url.toString() == documentUrl) servedDocument() else forbidden()
            allowedResource(request.url) -> null
            else -> forbidden()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
            if (request.isForMainFrame) fail()
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
            if (request.isForMainFrame) fail()
        }
    }

    fun stop() {
        accepting = false
    }

    fun fail() {
        if (accepting) onFailure()
    }

    fun receive(view: WebView, message: WebMessageCompat, origin: Uri, isMainFrame: Boolean) {
        if (!accepting || !isMainFrame || origin.toString() != AssetOrigin || view.url != documentUrl) return
        val body = runCatching { Json.parseToJsonElement(message.data.orEmpty()).jsonObject }.getOrNull() ?: return
        if (body["nonce"]?.jsonPrimitive?.contentOrNull != nonce) return
        when (body["kind"]?.jsonPrimitive?.contentOrNull) {
            "ready" -> onReady()
            "success" -> proof(body["value"])?.let { stop(); onProof(it) } ?: fail()
            "error" -> fail()
            "close" -> { stop(); onClosed() }
        }
    }

    private fun proof(value: JsonElement?): GeetestProof? =
        (value as? JsonObject)?.let { runCatching { GeetestProof.fromBridge(it) }.getOrNull() }

    private fun servedDocument() = WebResourceResponse("text/html", "utf-8", 200, "OK", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(document))

    private fun forbidden() = WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    private fun allowedResource(uri: Uri): Boolean =
        uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443) &&
            AllowedDomains.any { domain -> uri.host == domain || uri.host?.endsWith(".$domain") == true }
}