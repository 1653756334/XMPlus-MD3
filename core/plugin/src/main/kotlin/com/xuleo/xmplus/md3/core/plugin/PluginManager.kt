package com.xuleo.xmplus.md3.core.plugin

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request

/** Per-`platform` settings, kept across updates and dropped when the last plugin of that platform is removed. */
data class PluginMeta(
    val order: Int = 0,
    val disabled: Boolean = false,
    val variables: Map<String, String> = emptyMap(),
    /** The `platform` of the plugin asked for media sources instead of this one. */
    val alternative: String? = null,
)

/** An installed plugin as the 插件 page lists it. */
data class InstalledPlugin(val file: String, val info: PluginInfo, val meta: PluginMeta) {
    val platform: String get() = info.platform
    val enabled: Boolean get() = !meta.disabled && info.usable
}

data class PluginSubscription(val name: String, val url: String)

/** The outcome of installing one plugin file or link. */
sealed interface InstallResult {
    data class Installed(val platform: String, val version: String? = null) : InstallResult
    /** The same code is installed already; nothing changed. */
    data class AlreadyInstalled(val platform: String, val version: String? = null) : InstallResult
    data class Failed(val reason: InstallFailure, val source: String) : InstallResult
}

/** Error codes; the app turns them into text. */
enum class InstallFailure { CannotParse, LowerVersion, BadLink, Network }

/** How far a batch has got: [done] of [total] finished, [current] the file, link or plugin being installed. [total] is 0 while the links are still being read. */
fun interface InstallProgress {
    fun report(done: Int, total: Int, current: String?)

    companion object {
        val None = InstallProgress { _, _, _ -> }
    }
}

/** The file name at the end of a link, for showing which plugin a batch is at; other text is returned as it is. */
fun linkName(source: String): String {
    if ("://" !in source) return source
    return Uri.decode(source.substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/')).ifEmpty { source }
}

/**
 * Installed plugins, their per-platform meta and the subscriptions, following MusicFree's rules (`docs/MUSIC.md` ·
 * Install, update, remove). Plugin files live in `files/plugins`; what loading each one reported is kept in the index,
 * so the list shows without running any plugin, and a plugin runs only when it is first called.
 */
class PluginManager(context: Context, client: OkHttpClient) {
    private val runtime = PluginRuntime(context, client)
    private val download = client
    private val folder = File(context.applicationContext.filesDir, "plugins")
    private val index = AtomicFile(File(folder, "index.json"))
    private val lock = Mutex()
    private val hosts = mutableMapOf<String, PluginHost>()
    private var metas = mapOf<String, PluginMeta>()
    private var records = listOf<Pair<String, PluginInfo>>()

    private val mutablePlugins = MutableStateFlow<List<InstalledPlugin>?>(null)
    /** Installed plugins in the user's order; null until the index is read. */
    val plugins: StateFlow<List<InstalledPlugin>?> = mutablePlugins.asStateFlow()

    private val mutableSubscriptions = MutableStateFlow<List<PluginSubscription>>(emptyList())
    val subscriptions: StateFlow<List<PluginSubscription>> = mutableSubscriptions.asStateFlow()

    suspend fun start() = lock.withLock { if (mutablePlugins.value == null) withContext(Dispatchers.IO) { read() }; publish() }

    /** The running plugin for [platform], loaded on first use. Null when it is missing, broken or disabled. */
    suspend fun host(platform: String): PluginHost? = lock.withLock {
        val (file, info) = records.firstOrNull { it.second.platform == platform } ?: return null
        if (!info.usable || metas[platform]?.disabled == true) return null
        hosts.getOrPut(file) {
            val code = withContext(Dispatchers.IO) { File(folder, file).readText() }
            runtime.host(code) { metas[platform]?.variables.orEmpty() }
        }
    }

    // Install

    suspend fun installFiles(files: List<Pair<String, String>>, progress: InstallProgress = InstallProgress.None): List<InstallResult> =
        batch(files, progress, { it.first }) { (name, code) -> install(code, name) }

    /** A `.json` link installs every plugin it lists, a `.js` link one plugin; other links are refused. */
    suspend fun installLink(url: String, progress: InstallProgress = InstallProgress.None): List<InstallResult> =
        installLinks(listOf(url), progress)

    /** Reads every link first so the batch knows its size, then installs what they list, in order. */
    private suspend fun installLinks(links: List<String>, progress: InstallProgress): List<InstallResult> {
        progress.report(0, 0, null)
        val failures = mutableListOf<InstallResult>()
        val urls = links.flatMap { resolve(it.trim(), failures) }
        return batch(urls, progress, ::linkName) { installCode(it) } + failures
    }

    private suspend fun resolve(link: String, failures: MutableList<InstallResult>): List<String> {
        val path = link.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.endsWith(".json") -> {
                val text = fetch(link)
                if (text == null) {
                    failures += InstallResult.Failed(InstallFailure.Network, link)
                    return emptyList()
                }
                try {
                    (Json.parseToJsonElement(text).jsonObject["plugins"] as? JsonArray).orEmpty()
                        .mapNotNull { (it as? JsonObject)?.text("url") }
                } catch (_: Exception) {
                    failures += InstallResult.Failed(InstallFailure.CannotParse, link)
                    emptyList()
                }
            }
            path.endsWith(".js") -> listOf(link)
            else -> {
                failures += InstallResult.Failed(InstallFailure.BadLink, link)
                emptyList()
            }
        }
    }

    private suspend fun <T> batch(
        items: List<T>,
        progress: InstallProgress,
        name: (T) -> String,
        install: suspend (T) -> InstallResult,
    ): List<InstallResult> {
        val results = items.mapIndexed { index, item ->
            progress.report(index, items.size, name(item))
            install(item)
        }
        progress.report(items.size, items.size, null)
        return results
    }


    private suspend fun installCode(url: String): InstallResult {
        val code = fetch(url) ?: return InstallResult.Failed(InstallFailure.Network, url)
        return install(code, url)
    }

    private suspend fun install(code: String, source: String): InstallResult {
        val probe = runtime.host(code) { emptyMap() }
        val info = try {
            probe.load()
        } finally {
            probe.close()
        }
        if (info.platform.isEmpty()) return InstallResult.Failed(InstallFailure.CannotParse, source)
        return lock.withLock {
            if (records.any { it.second.hash == info.hash }) return@withLock InstallResult.AlreadyInstalled(info.platform, info.version)
            val old = records.filter { it.second.platform == info.platform }
            if (old.any { compareVersions(it.second.version, info.version) > 0 }) {
                return@withLock InstallResult.Failed(InstallFailure.LowerVersion, source)
            }
            val file = UUID.randomUUID().toString() + ".js"
            try {
                withContext(Dispatchers.IO) {
                    folder.mkdirs()
                    File(folder, file).writeText(code)
                }
            } catch (_: IOException) {
                return@withLock InstallResult.Failed(InstallFailure.CannotParse, source)
            }
            old.forEach { (oldFile, _) -> drop(oldFile) }
            records = records.filterNot { it.second.platform == info.platform } + (file to info)
            if (info.platform !in metas) metas = metas + (info.platform to PluginMeta(order = nextOrder()))
            save()
            InstallResult.Installed(info.platform, info.version)
        }
    }

    // Update

    /** Reinstalls the plugin of [platform] from its `srcUrl`. */
    suspend fun update(platform: String): InstallResult {
        val src = lock.withLock { records.firstOrNull { it.second.platform == platform }?.second?.srcUrl }
            ?: return InstallResult.Failed(InstallFailure.BadLink, platform)
        return installCode(src)
    }

    /** Updates every plugin that has a `srcUrl`; one failure does not stop the others. */
    suspend fun updateAll(progress: InstallProgress = InstallProgress.None): List<InstallResult> {
        val platforms = lock.withLock { records.filter { it.second.srcUrl != null }.map { it.second.platform } }
        return batch(platforms, progress, { it }) { update(it) }
    }

    /** Installs from every subscription, in order. */
    suspend fun updateSubscriptions(progress: InstallProgress = InstallProgress.None): List<InstallResult> =
        installLinks(subscriptions.value.map { it.url }, progress)

    // Remove and settings

    suspend fun uninstall(platform: String) = lock.withLock {
        records.filter { it.second.platform == platform }.forEach { (file, _) -> drop(file) }
        records = records.filterNot { it.second.platform == platform }
        metas = metas - platform
        save()
    }

    suspend fun setEnabled(platform: String, enabled: Boolean) = editMeta(platform) { it.copy(disabled = !enabled) }

    suspend fun setVariables(platform: String, values: Map<String, String>) = editMeta(platform) { it.copy(variables = values) }

    suspend fun setAlternative(platform: String, alternative: String?) =
        editMeta(platform) { it.copy(alternative = alternative?.takeIf { name -> name != platform }) }

    /** Saves the order shown after the user drags plugins into place. */
    suspend fun reorder(platforms: List<String>) = lock.withLock {
        metas = metas.mapValues { (platform, meta) ->
            val at = platforms.indexOf(platform)
            if (at < 0) meta else meta.copy(order = at)
        }
        save()
    }

    suspend fun setSubscriptions(list: List<PluginSubscription>) = lock.withLock {
        mutableSubscriptions.value = list.distinctBy { it.url }
        save()
    }

    private suspend fun editMeta(platform: String, change: (PluginMeta) -> PluginMeta) = lock.withLock {
        val meta = metas[platform] ?: return@withLock
        metas = metas + (platform to change(meta))
        if (change(meta).variables != meta.variables || change(meta).disabled) {
            records.filter { it.second.platform == platform }.forEach { (file, _) -> hosts.remove(file)?.close() }
        }
        save()
    }

    // Storage

    private suspend fun drop(file: String) {
        hosts.remove(file)?.close()
        withContext(Dispatchers.IO) { File(folder, file).delete() }
    }

    private fun nextOrder() = (metas.values.maxOfOrNull { it.order } ?: -1) + 1

    private fun publish() {
        mutablePlugins.value = records
            .map { (file, info) -> InstalledPlugin(file, info, metas[info.platform] ?: PluginMeta()) }
            .sortedBy { it.meta.order }
    }

    private suspend fun save() {
        publish()
        val text = buildJsonObject {
            put("version", 1)
            put("plugins", buildJsonArray { records.forEach { (file, info) -> add(encode(file, info)) } })
            put("meta", JsonObject(metas.mapValues { (_, meta) -> encode(meta) }))
            put("subscriptions", buildJsonArray {
                mutableSubscriptions.value.forEach { add(buildJsonObject { put("name", it.name); put("url", it.url) }) }
            })
        }.toString()
        withContext(Dispatchers.IO) {
            folder.mkdirs()
            val stream = index.startWrite()
            try {
                stream.write(text.encodeToByteArray())
                index.finishWrite(stream)
            } catch (_: IOException) {
                index.failWrite(stream)
            }
        }
    }

    private fun read() {
        val root = try {
            if (!index.baseFile.exists()) null else Json.parseToJsonElement(index.readFully().decodeToString()) as? JsonObject
        } catch (_: Exception) {
            null
        }
        records = (root?.get("plugins") as? JsonArray).orEmpty().mapNotNull { decodeRecord(it) }
            .filter { File(folder, it.first).isFile }
        metas = (root?.get("meta") as? JsonObject).orEmpty().mapValues { (_, value) -> decodeMeta(value) }
        mutableSubscriptions.value = (root?.get("subscriptions") as? JsonArray).orEmpty().mapNotNull {
            val row = it as? JsonObject ?: return@mapNotNull null
            PluginSubscription(row.text("name").orEmpty(), row.text("url") ?: return@mapNotNull null)
        }
    }

    private fun encode(file: String, info: PluginInfo) = buildJsonObject {
        put("file", file)
        put("platform", info.platform)
        put("version", info.version)
        put("appVersion", info.appVersion)
        put("author", info.author)
        put("description", info.description)
        put("srcUrl", info.srcUrl)
        put("methods", buildJsonArray { info.methods.forEach { add(JsonPrimitive(it)) } })
        put("variables", buildJsonArray {
            info.variables.forEach { v -> add(buildJsonObject { put("key", v.key); put("name", v.name); put("hint", v.hint) }) }
        })
        put("declared", info.declared)
        put("hash", info.hash)
        put("error", info.error?.code)
    }

    private fun decodeRecord(element: JsonElement): Pair<String, PluginInfo>? {
        val row = element as? JsonObject ?: return null
        val file = row.text("file") ?: return null
        return file to PluginInfo(
            platform = row.text("platform") ?: return null,
            version = row.text("version"),
            appVersion = row.text("appVersion"),
            author = row.text("author"),
            description = row.text("description"),
            srcUrl = row.text("srcUrl"),
            methods = (row["methods"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet(),
            variables = (row["variables"] as? JsonArray).orEmpty().mapNotNull {
                val v = it as? JsonObject ?: return@mapNotNull null
                PluginVariable(v.text("key") ?: return@mapNotNull null, v.text("name"), v.text("hint"))
            },
            declared = row["declared"] as? JsonObject ?: JsonObject(emptyMap()),
            hash = row.text("hash").orEmpty(),
            error = PluginErrorReason.fromCode(row.text("error")),
            message = null,
        )
    }

    private fun encode(meta: PluginMeta) = buildJsonObject {
        put("order", meta.order)
        put("disabled", meta.disabled)
        put("variables", JsonObject(meta.variables.mapValues { JsonPrimitive(it.value) }))
        put("alternative", meta.alternative)
    }

    private fun decodeMeta(element: JsonElement): PluginMeta {
        val row = element as? JsonObject ?: return PluginMeta()
        return PluginMeta(
            order = (row["order"] as? JsonPrimitive)?.intOrNull ?: 0,
            disabled = (row["disabled"] as? JsonPrimitive)?.booleanOrNull ?: false,
            variables = (row["variables"] as? JsonObject).orEmpty()
                .mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }.toMap(),
            alternative = row.text("alternative"),
        )
    }

    private suspend fun fetch(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
            download.newCall(request).execute().use { if (it.isSuccessful) it.body.string() else null }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun JsonObject.text(name: String) = (this[name] as? JsonPrimitive)?.contentOrNull
}

/** Compares dotted versions part by part, numbers numerically; a missing version counts as the lowest. */
internal fun compareVersions(a: String?, b: String?): Int {
    if (a == b) return 0
    if (a.isNullOrBlank()) return -1
    if (b.isNullOrBlank()) return 1
    val left = a.trim().removePrefix("v").split('.', '-', '+')
    val right = b.trim().removePrefix("v").split('.', '-', '+')
    for (i in 0 until maxOf(left.size, right.size)) {
        val x = left.getOrNull(i) ?: "0"
        val y = right.getOrNull(i) ?: "0"
        val nx = x.toLongOrNull()
        val ny = y.toLongOrNull()
        val c = if (nx != null && ny != null) nx.compareTo(ny) else x.compareTo(y)
        if (c != 0) return c
    }
    return 0
}
