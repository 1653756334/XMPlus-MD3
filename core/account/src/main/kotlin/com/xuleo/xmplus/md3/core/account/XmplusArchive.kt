package com.xuleo.xmplus.md3.core.account

import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.XmlyException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One account of a backup. [credentials] is null when the backup was exported without logins. */
class ArchivedAccount(val identity: String, val credentials: Credentials?, val record: JsonObject)

/** What a backup holds that this app can use today, and what it only carries along. */
class ArchiveContents(
    val accounts: List<ArchivedAccount>,
    val primaryIdentity: String?,
    val hasSettings: Boolean,
    val hasLibrary: Boolean,
    val hasDownloads: Boolean,
) {
    val loginCount: Int get() = accounts.count { it.credentials != null }
}

/**
 * Reads an XMPlus backup (`meta.json`, `setting.json`, `library.json`, `account.json` in a ZIP). Everything is checked
 * before anything is used, and a backup that fails any check is rejected whole as "archive_invalid": there is no
 * partial import. Secrets and device-specific paths may not appear outside the login pairs.
 *
 * Only the account part is turned into data. Settings, bookmarks and downloads are checked for the forbidden keys and
 * types; their full validation arrives with their restore.
 */
object XmplusArchive {
    const val MaxBytes = 64 * 1024 * 1024
    private val Members = setOf("meta.json", "setting.json", "library.json", "account.json")
    private val RootKeys = setOf("root", "rootDirectory", "downloadRoot", "downloadDirectory")
    private val SecretKeys = setOf(
        "token", "device_id", "deviceId", "cookie", "cookie2", "headers", "x-tk", "password", "verificationCode",
        "credentialRef", "credentialCleanup", "cacheIdentity", "challenge", "captcha", "smsCode",
    )
    private val TransientKeys = setOf(
        "url", "uri", "audioUrl", "playUrl", "signedUrl", "resolvedAudio", "outputPath", "filePath", "cachePath",
        "cacheDirectory", "temporaryPath", "partialPath", "finalPath", "destination", "localPath",
    )
    private val UrlKey = Regex("^(?:playUrl|downloadUrl|audioUrl|signedUrl)", RegexOption.IGNORE_CASE)
    private val BooleanKeys = setOf(
        "usage_uncertain", "uncertain", "historical", "downloadExempt", "attempted", "terminal", "autoStart", "playing", "archived",
    )
    private val NumberKeys = setOf(
        "usage_seconds", "pendingSeconds", "unreportedSeconds", "seconds", "balanceBefore", "balanceAfter", "balance_before",
        "balance_after", "position", "duration",
    )
    private val StringKeys = setOf("operationId", "accountIdentity", "taskId", "trackId")
    private val Hex64 = Regex("[0-9a-f]{64}")
    private val Uid = Regex("[1-9][0-9]{0,19}")
    private val TrackId = Regex("(?:xmly:)?([1-9][0-9]{0,18})")

    fun read(bytes: ByteArray): ArchiveContents = try {
        parse(bytes)
    } catch (_: Exception) {
        throw XmlyException("archive_invalid", definitive = true)
    }

    private fun parse(bytes: ByteArray): ArchiveContents {
        require(bytes.isNotEmpty() && bytes.size <= MaxBytes)
        val files = readMembers(bytes)
        require("meta.json" in files && files.size >= 2)
        checkMeta(files.getValue("meta.json"), files.keys)
        for (name in files.keys - setOf("meta.json", "account.json")) checkData(files.getValue(name))
        val account = files["account.json"]
        return ArchiveContents(
            accounts = account?.let(::readAccounts).orEmpty(),
            primaryIdentity = (account?.get("primaryAccountId") as? JsonPrimitive)?.takeIf { it.isString }?.content,
            hasSettings = "setting.json" in files,
            hasLibrary = "library.json" in files,
            hasDownloads = account?.containsKey("downloads") == true || (files["library.json"]?.containsKey("downloads") == true),
        )
    }

    /** Streams the ZIP with a hard cap on the inflated size, so a small file cannot expand into memory. */
    private fun readMembers(bytes: ByteArray): Map<String, JsonObject> {
        val files = LinkedHashMap<String, JsonObject>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && entry.name in Members && entry.name !in files)
                val content = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = zip.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MaxBytes)
                    content.write(buffer, 0, read)
                }
                files[entry.name] = Json.parseToJsonElement(content.toByteArray().decodeToString(throwOnInvalidSequence = true)) as JsonObject
            }
        }
        return files
    }

    private fun checkMeta(meta: JsonObject, names: Set<String>) {
        require(meta.string("type") == "data_archive" && meta.string("appName") == "XMPlus")
        require(((meta.integer("formatVersion") ?: error("")) shr 16) == 1L)
        require((meta.integer("versionCode") ?: error("")) >= 1)
        meta.string("versionName") ?: error("")
        meta.string("platform") ?: error("")
        require(validTimestamp(meta.string("exportTime") ?: error("")))
        require(Hex64.matches(meta.string("origin") ?: error("")))
        val listed = (meta["files"] as JsonArray).map { (it as JsonPrimitive).takeIf { p -> p.isString }?.content ?: error("") }
        require("meta.json" !in listed && listed.toSet().size == listed.size && listed.toSet() == names - "meta.json")
    }

    private fun readAccounts(account: JsonObject): List<ArchivedAccount> {
        val rawAccounts = account["accounts"] as JsonArray
        val primary = account["primaryAccountId"]
        require(primary == null || primary == JsonNull || (primary as JsonPrimitive).isString)
        require(account["downloads"].let { it == null || it is JsonArray } && account["queue"].let { it == null || it is JsonObject })
        for (name in listOf("downloads", "downloadAlbums", "queue")) account[name]?.let(::checkData)
        val identities = HashSet<String>()
        val withLogin = HashSet<String>()
        val accounts = rawAccounts.map { raw ->
            val entry = raw as JsonObject
            val identity = entry.string("identity") ?: error("")
            require(Hex64.matches(identity) && identities.add(identity))
            val record = entry["record"] as JsonObject
            checkData(record)
            val uid = record.string("uid") ?: error("")
            require(Uid.matches(uid))
            record["ledger"]?.let { UsageStateCodec.decode(it as JsonObject, identity) }
            val pair = entry["credentials"]?.takeIf { it != JsonNull }?.let { it as JsonObject }
            val credentials = pair?.let {
                require(it.string("token") != null && it.string("device_id") != null)
                Credentials.parse(JsonObject(mapOf("token" to it.getValue("token"), "device_id" to it.getValue("device_id"))))
            }
            if (credentials != null) {
                require(credentials.identity == identity && credentials.uid == uid)
                withLogin.add(identity)
            }
            ArchivedAccount(identity, credentials, record)
        }
        val primaryId = (primary as? JsonPrimitive)?.takeIf { it.isString }?.content
        require(if (withLogin.isNotEmpty()) primaryId in withLogin else primaryId == null)
        return accounts
    }

    private fun checkData(value: JsonElement, depth: Int = 0, rawTrackId: String? = null) {
        require(depth <= 64)
        when (value) {
            is JsonObject -> for ((key, item) in value) {
                require(key !in SecretKeys && key !in TransientKeys && !UrlKey.containsMatchIn(key))
                if (key in RootKeys) require(item.isString())
                if (key in BooleanKeys) require(item is JsonPrimitive && !item.isString && item.content.toBooleanStrictOrNull() != null)
                if (key in NumberKeys && item != JsonNull) {
                    require(item is JsonPrimitive && !item.isString && item.content.toDoubleOrNull()?.let { it.isFinite() && it >= 0 } == true)
                }
                // API metadata keeps numeric ids; persisted operations use strings.
                if (key == "trackId" && rawTrackId != null && item is JsonPrimitive && !item.isString && item.content.toLongOrNull() != null) {
                    require(identifier(item.content) == identifier(rawTrackId))
                } else if (key in StringKeys) {
                    require(item.isString())
                }
                if (key == "state") require(item.isString())
                val raw = if (key == "raw" && value["id"].isString() && value["album_id"].isString()) (value["id"] as JsonPrimitive).content else null
                checkData(item, depth + 1, raw)
            }
            is JsonArray -> value.forEach { checkData(it, depth + 1) }
            is JsonPrimitive -> if (!value.isString) require(value.content.toDoubleOrNull()?.isFinite() != false)
        }
    }

    private fun identifier(value: String): String = TrackId.matchEntire(value)?.groupValues?.get(1) ?: error("")

    private fun JsonElement?.isString() = this is JsonPrimitive && isString

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.integer(key: String): Long? = (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.content?.toLongOrNull()

    private fun validTimestamp(value: String): Boolean =
        runCatching { OffsetDateTime.parse(value) }.isSuccess || runCatching { LocalDateTime.parse(value) }.isSuccess ||
            runCatching { LocalDate.parse(value) }.isSuccess
}