package com.xuleo.xmplus.md3.core.account

import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.Quota
import com.xuleo.xmplus.md3.core.model.XmlyException
import kotlinx.serialization.json.*
import kotlin.time.Instant

/** This is the native private store format, not the XMPlus backup format. */
object AccountSnapshotCodec {
    fun encode(snapshot: AccountSnapshot): ByteArray {
        validate(snapshot)
        return buildJsonObject {
            put("version", 1)
            put("primary", snapshot.primaryIdentity?.let(::JsonPrimitive) ?: JsonNull)
            put("auto_claim", snapshot.autoClaim)
            putJsonArray("accounts") {
                snapshot.records.forEach { record -> add(buildJsonObject {
                    put("identity", record.identity)
                    put("uid", record.uid)
                    put("device_hint", record.deviceHint)
                    put("credentials", record.credentials?.toJson() ?: JsonNull)
                    put("remaining_seconds", record.quota.remainingSeconds)
                    put("new_device", record.quota.newDevice)
                    put("checked_at", record.quota.checkedAt.toEpochMilliseconds())
                    put("reward_paused", record.rewardPaused)
                    put("retained", record.retained)
                }) }
            }
        }.toString().toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): AccountSnapshot = try {
        val root = Json.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true)).jsonObject
        require(root.getValue("version").jsonPrimitive.int == 1)
        val primary = root.getValue("primary").let { if (it == JsonNull) null else it.string() }
        val records = root.getValue("accounts").jsonArray.map { element ->
            val record = element.jsonObject
            AccountRecord(
                identity = record.getValue("identity").string(),
                uid = record.getValue("uid").string(),
                deviceHint = record.getValue("device_hint").string(),
                credentials = record.getValue("credentials").let { if (it == JsonNull) null else Credentials.parse(it.jsonObject) },
                quota = Quota(
                    record.getValue("remaining_seconds").integer(),
                    record.getValue("new_device").let { require(it is JsonPrimitive && !it.isString); it.boolean },
                    Instant.fromEpochMilliseconds(record.getValue("checked_at").integer()),
                ),
                rewardPaused = record["reward_paused"]?.boolean() ?: false,
                retained = record.getValue("retained").jsonObject,
            )
        }
        AccountSnapshot(records, primary, root["auto_claim"]?.boolean() ?: false).also(::validate)
    } catch (_: Exception) {
        throw XmlyException("account_read_failed")
    }

    fun validate(snapshot: AccountSnapshot) {
        require(snapshot.records.map { it.identity }.distinct().size == snapshot.records.size)
        snapshot.records.forEach { record ->
            require(Regex("[0-9a-f]{64}").matches(record.identity))
            require(Regex("[1-9][0-9]{0,19}").matches(record.uid))
            record.credentials?.let { require(it.identity == record.identity && it.uid == record.uid) }
        }
        val active = snapshot.records.filter { it.credentials != null }
        require(if (active.isEmpty()) snapshot.primaryIdentity == null else active.any { it.identity == snapshot.primaryIdentity })
    }

    private fun JsonElement.string(): String {
        require(this is JsonPrimitive && isString)
        return content
    }

    private fun JsonElement.boolean(): Boolean {
        require(this is JsonPrimitive && !isString)
        return boolean
    }

    private fun JsonElement.integer(): Long {
        require(this is JsonPrimitive && !isString)
        return long
    }
}
