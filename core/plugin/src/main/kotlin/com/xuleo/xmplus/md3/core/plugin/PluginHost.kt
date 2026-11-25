package com.xuleo.xmplus.md3.core.plugin

import android.content.Context
import android.util.Log
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.evaluate
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient

/** The MusicFree version plugins are told they run on; their `appVersion` ranges are written against it. */
const val ReportedMusicFreeVersion = "0.6.2"

/** Why a plugin cannot be used. Shown to the user as text by the app. */
enum class PluginErrorReason(val code: String) {
    CannotParse("cannot_parse"), VersionNotMatch("version_not_match");

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code }
    }
}

/** A user variable a plugin asks for, as MusicFree's `userVariables` entries. */
data class PluginVariable(val key: String, val name: String?, val hint: String?)

/** What loading a plugin's code found out about it. */
data class PluginInfo(
    /** Empty when the code did not give one; such a plugin is never called. */
    val platform: String,
    val version: String?,
    val appVersion: String?,
    val author: String?,
    val description: String?,
    val srcUrl: String?,
    val methods: Set<String>,
    val variables: List<PluginVariable>,
    /** The plugin's other declared fields (primaryKey, cacheControl, hints, search types), as given. */
    val declared: JsonObject,
    /** SHA-256 of the code, as MusicFree computes it. */
    val hash: String,
    val error: PluginErrorReason?,
    /** The exception message when the code failed to run; for diagnostics only. */
    val message: String?,
) {
    val usable: Boolean get() = error == null && platform.isNotEmpty()
}

/** Loads the JS packages once and makes one [PluginHost] per plugin. */
class PluginRuntime(context: Context, client: OkHttpClient) {
    private val assets = context.applicationContext.assets
    private val http = PluginHttp(client)
    private val prelude by lazy {
        listOf("musicfree/packages.js", "musicfree/host.js").joinToString("\n;\n") { path ->
            assets.open(path).use { it.readBytes().decodeToString() }
        }
    }

    fun host(code: String, variables: () -> Map<String, String>): PluginHost = PluginHost(code, prelude, http, variables)
}

/**
 * One plugin in its own QuickJS context, created on first use. Calls run off the main thread; a call that throws
 * fails alone and the context stays usable.
 */
class PluginHost internal constructor(
    private val code: String,
    private val prelude: String,
    private val http: PluginHttp,
    private val variables: () -> Map<String, String>,
) {
    private val lock = Mutex()
    private var engine: QuickJs? = null
    private var loaded: PluginInfo? = null

    val hash: String = sha256(code)

    /** Runs the plugin's code once and reports what it declared. Later calls return the same result. */
    suspend fun load(): PluginInfo = lock.withLock { loaded ?: mount().also { loaded = it } }

    /**
     * Calls [method] with [args] passed as JSON. Null when the plugin has no such method, as MusicFree treats a
     * missing method as an empty result. Throws [PluginCallException] when the plugin throws.
     */
    suspend fun call(method: String, args: List<JsonElement>): JsonElement? {
        val info = load()
        if (!info.usable || method !in info.methods) return null
        val js = lock.withLock { engine } ?: return null
        val reply = try {
            js.evaluate<String>("await __mfCall(${quote(method)}, ${quote(JsonArray(args).toString())})")
        } catch (failure: QuickJsException) {
            throw PluginCallException(failure.message, failure)
        }
        val root = Json.parseToJsonElement(reply).jsonObject
        if (root.containsKey("missing")) return null
        return root["value"] ?: JsonNull
    }

    suspend fun close() = lock.withLock {
        engine?.close()
        engine = null
    }

    private suspend fun mount(): PluginInfo {
        val js = QuickJs.create(Dispatchers.Default)
        engine = js
        js.function("__mfLog") { args -> log(args.getOrNull(0) as? String, args.getOrNull(1) as? String) }
        js.function("__mfUserVariables") { _ -> JsonObject(variables().mapValues { JsonPrimitive(it.value) }).toString() }
        js.function("__mfSource") { _ -> code }
        js.asyncFunction("__mfHttp") { args -> http.execute(args[0] as String) }
        js.asyncFunction("__mfDelay") { args -> delay((args.getOrNull(0) as? Number)?.toLong() ?: 0L) }
        val reply = try {
            js.evaluate<Any?>("globalThis.__mfAppVersion = ${quote(ReportedMusicFreeVersion)};")
            js.evaluate<Any?>(prelude)
            js.evaluate<String>("__mfLoad()")
        } catch (failure: QuickJsException) {
            return failed(failure.message)
        }
        val root = Json.parseToJsonElement(reply).jsonObject
        fun text(name: String) = (root[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        val platform = text("platform").orEmpty()
        return PluginInfo(
            platform = platform,
            version = text("version"),
            appVersion = text("appVersion"),
            author = text("author"),
            description = text("description"),
            srcUrl = text("srcUrl"),
            methods = (root["methods"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet(),
            variables = (root["userVariables"] as? JsonArray).orEmpty().mapNotNull { element ->
                val row = element as? JsonObject ?: return@mapNotNull null
                fun field(name: String) = (row[name] as? JsonPrimitive)?.contentOrNull
                PluginVariable(field("key") ?: return@mapNotNull null, field("name"), field("hint"))
            },
            declared = JsonObject(root.filterKeys { it in DeclaredFields }),
            hash = if (platform.isEmpty()) "" else hash,
            error = PluginErrorReason.fromCode(text("error")) ?: if (platform.isEmpty()) PluginErrorReason.CannotParse else null,
            message = text("message"),
        )
    }

    private fun failed(message: String?) = PluginInfo(
        platform = "", version = null, appVersion = null, author = null, description = null, srcUrl = null,
        methods = emptySet(), variables = emptyList(), declared = JsonObject(emptyMap()), hash = "",
        error = PluginErrorReason.CannotParse, message = message,
    )

    private fun log(level: String?, text: String?) {
        val message = text.orEmpty()
        when (level) {
            "error" -> Log.e(LogTag, message)
            "warn" -> Log.w(LogTag, message)
            "info" -> Log.i(LogTag, message)
            else -> Log.d(LogTag, message)
        }
    }

    private companion object {
        const val LogTag = "MusicPlugin"
        val DeclaredFields = setOf("primaryKey", "cacheControl", "defaultSearchType", "supportedSearchType", "hints")

        fun quote(text: String) = JsonPrimitive(text).toString()

        fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray()).joinToString("") { "%02x".format(it) }
    }
}

/** The plugin threw, or its result could not be read. */
class PluginCallException(message: String?, cause: Throwable? = null) : Exception(message, cause)
