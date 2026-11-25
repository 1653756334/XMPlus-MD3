package com.xuleo.xmplus.md3.core.plugin

import android.content.Context
import com.xuleo.xmplus.md3.core.model.MusicItem
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** A song's lyric and its translation, as LRC text. */
data class LyricSource(val rawLrc: String?, val translation: String?) {
    val isEmpty: Boolean get() = rawLrc.isNullOrBlank() && translation.isNullOrBlank()
}

/** One comment of a song, with MusicFree's fields. */
data class MusicComment(
    val id: String?,
    val nickName: String,
    val avatar: String?,
    val comment: String,
    val like: Long?,
    /** Milliseconds since the epoch. */
    val createAt: Long?,
    val location: String?,
    val replies: List<MusicComment>,
)

/**
 * Lyrics and comments of songs, by MusicFree's `getLyric` and `getMusicComments`. Lyrics are looked for in order: a
 * lyric the user chose for the song, the cached lyric, the plugin's `getLyric` and, when it gives no text, the
 * deprecated `lrc` link, then the song's `rawLrc`. Lyric and translation are kept as files.
 */
class MusicLyrics(
    context: Context,
    private val manager: PluginManager,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build(),
) {
    /** Lyrics the user chose; kept until the app is removed. */
    private val chosen = File(context.applicationContext.filesDir, "lyrics")
    private val cache = File(context.applicationContext.cacheDir, "lyrics")

    suspend fun lyric(song: MusicItem): LyricSource? = withContext(Dispatchers.IO) {
        read(chosen, song)?.let { return@withContext it }
        read(cache, song)?.let { return@withContext it }
        val found = fetch(song) ?: return@withContext null
        write(cache, song, found)
        found
    }

    /** Uses the lyric of [match], a result of a lyric search, for [song] from now on. False when it has none. */
    suspend fun choose(song: MusicItem, match: MusicItem): Boolean = withContext(Dispatchers.IO) {
        val found = read(cache, match) ?: fetch(match) ?: return@withContext false
        write(chosen, song, found)
        true
    }

    /** Plugins that search lyrics, in the user's order: MusicFree's `getSortedSearchablePlugins("lyric")`. */
    fun lyricSearchPlatforms(): List<String> = manager.plugins.value.orEmpty().filter { plugin ->
        plugin.enabled && Search in plugin.info.methods && plugin.searchTypes().let { it == null || LyricType in it }
    }.map { it.platform }

    fun supportsComments(platform: String): Boolean =
        manager.plugins.value.orEmpty().any { it.platform == platform && it.enabled && GetMusicComments in it.info.methods }

    /** One page of [song]'s comments. Throws when the plugin fails or returns nothing. */
    suspend fun comments(song: MusicItem, page: Int): PluginPage<MusicComment> {
        val host = manager.host(song.platform) ?: throw PluginCallException("unavailable")
        val reply = host.call(GetMusicComments, listOf(song.raw, JsonPrimitive(page)))
        val root = reply as? JsonObject ?: throw PluginCallException("empty")
        val data = (root["data"] as? JsonArray).orEmpty().mapNotNull { comment(it) }
        val isEnd = (root["isEnd"] as? JsonPrimitive)?.contentOrNull != "false"
        return PluginPage(data, isEnd)
    }

    private suspend fun fetch(song: MusicItem): LyricSource? {
        val reply = try {
            manager.host(song.platform)?.call(GetLyric, listOf(song.raw))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        // As in MusicFree, a plugin that gives nothing means the song has no lyric.
        val source = reply as? JsonObject ?: return null
        var rawLrc = source.text("rawLrc") ?: song.raw.text("rawLrc")
        val translation = source.text("translation")
        if (source.text("rawLrc") == null && translation == null) {
            val link = source.text("lrc") ?: song.raw.text("lrc")
            rawLrc = if (link != null) download(link) else song.raw.text("rawLrc")
        }
        return LyricSource(rawLrc, translation).takeUnless { it.isEmpty }
    }

    private fun download(link: String): String? = try {
        client.newCall(Request.Builder().url(link).build()).execute().use { if (it.isSuccessful) it.body.string() else null }
    } catch (_: Exception) {
        null
    }

    private fun read(folder: File, song: MusicItem): LyricSource? {
        val (lrc, tran) = files(folder, song)
        if (!lrc.isFile && !tran.isFile) return null
        return try {
            LyricSource(lrc.takeIf { it.isFile }?.readText(), tran.takeIf { it.isFile }?.readText()).takeUnless { it.isEmpty }
        } catch (_: Exception) {
            null
        }
    }

    private fun write(folder: File, song: MusicItem, source: LyricSource) {
        val (lrc, tran) = files(folder, song)
        try {
            lrc.parentFile?.mkdirs()
            if (source.rawLrc.isNullOrBlank()) lrc.delete() else lrc.writeText(source.rawLrc)
            if (source.translation.isNullOrBlank()) tran.delete() else tran.writeText(source.translation)
        } catch (_: Exception) {
            // Without the file the lyric is only asked again next time.
        }
    }

    /** MusicFree's file names: md5 of the platform as the folder, md5 of the id as the name. */
    private fun files(folder: File, song: MusicItem): Pair<File, File> {
        val dir = File(folder, md5(song.platform))
        val name = md5(song.id)
        return File(dir, "$name.lrc") to File(dir, "$name.tran.lrc")
    }

    private fun comment(element: JsonElement): MusicComment? {
        val raw = element as? JsonObject ?: return null
        val text = raw.text("comment") ?: return null
        return MusicComment(
            id = raw.text("id"),
            nickName = raw.text("nickName").orEmpty(),
            avatar = raw.text("avatar"),
            comment = text,
            like = (raw["like"] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() },
            createAt = (raw["createAt"] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() },
            location = raw.text("location"),
            replies = (raw["replies"] as? JsonArray).orEmpty().mapNotNull { comment(it) },
        )
    }

    private fun InstalledPlugin.searchTypes(): List<String>? =
        (info.declared["supportedSearchType"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

    private companion object {
        const val GetLyric = "getLyric"
        const val GetMusicComments = "getMusicComments"
        const val Search = "search"
        const val LyricType = "lyric"
    }
}

private fun md5(text: String): String =
    MessageDigest.getInstance("MD5").digest(text.encodeToByteArray()).joinToString("") { "%02x".format(it) }

private fun JsonObject.text(name: String): String? {
    val value = this[name] as? JsonPrimitive ?: return null
    if (value is JsonNull) return null
    return value.contentOrNull?.takeIf { it.isNotEmpty() }
}

/** One line of a lyric. */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * Parses LRC text: a line may carry several time tags, `[offset:]` moves every line (positive is earlier, as in LRC),
 * lines are sorted by time. Text without any time tag is shown as one line per row from 0.
 */
fun parseLrc(text: String?): List<LyricLine> {
    if (text.isNullOrBlank()) return emptyList()
    val rows = text.lines()
    val offset = rows.firstNotNullOfOrNull { OffsetTag.find(it)?.groupValues?.get(1)?.trim()?.toLongOrNull() } ?: 0L
    val lines = mutableListOf<LyricLine>()
    var plain = 0
    for (row in rows) {
        val tags = TimeTag.findAll(row).toList()
        if (tags.isEmpty()) {
            if (MetaTag.matches(row.trim()) || row.isBlank()) continue
            plain++
            continue
        }
        val words = row.substring(tags.last().range.last + 1).trim()
        for (tag in tags) {
            val minutes = tag.groupValues[1].toLongOrNull() ?: continue
            val seconds = tag.groupValues[2].toLongOrNull() ?: continue
            val fraction = tag.groupValues[3]
            val millis = when (fraction.length) {
                0 -> 0L
                1 -> fraction.toLong() * 100
                2 -> fraction.toLong() * 10
                else -> fraction.take(3).toLong()
            }
            val time = (minutes * 60_000 + seconds * 1_000 + millis - offset).coerceAtLeast(0)
            lines += LyricLine(time, words)
        }
    }
    if (lines.isEmpty() && plain > 0) {
        return rows.map { it.trim() }.filter { it.isNotEmpty() && !MetaTag.matches(it) }.map { LyricLine(0, it) }
    }
    return lines.sortedBy { it.timeMs }
}

private val TimeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
private val OffsetTag = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
private val MetaTag = Regex("""^\[[a-zA-Z#]+:.*]$""")
