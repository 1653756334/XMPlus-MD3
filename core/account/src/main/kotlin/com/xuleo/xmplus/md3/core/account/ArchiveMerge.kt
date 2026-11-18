package com.xuleo.xmplus.md3.core.account

import com.xuleo.xmplus.md3.core.model.Quota
import kotlin.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The fields of an XMPlus account record this app models itself; everything else is carried along untouched. */
private val KnownRecordKeys = setOf("identity", "uid", "deviceHint", "quota", "rewardPaused", "ledger", "archived")

/**
 * Folds an archived account into what the device already has, as XMPlus restore does: whatever is already here wins,
 * and a backup only adds. Online debt is a current balance rather than a receipt, so an account that already exists
 * keeps its own; a backup never resurrects debt, a pending debit or an uncertainty flag.
 *
 * A backup is history. Entries caught mid-send become uncertain, and its receipts only record what happened: they
 * never offset a new download.
 */
internal fun mergeArchivedAccount(archived: ArchivedAccount, existing: AccountRecord?, verified: Quota?): AccountRecord {
    val record = archived.record
    val incoming = record["ledger"]?.let { UsageStateCodec.decode(it as JsonObject, archived.identity) } ?: UsageState()
    val receipts = incoming.receipts.map { it.copy(historical = true, state = it.state.recovered()) }
    val usage = if (existing != null) {
        existing.usage.copy(receipts = mergeReceipts(receipts, existing.usage.receipts))
    } else {
        incoming.copy(pending = incoming.pending?.let { it.copy(state = it.state.recovered()) }, receipts = receipts)
    }
    val credentials = archived.credentials ?: existing?.credentials
    return AccountRecord(
        identity = archived.identity,
        uid = (record["uid"] as JsonPrimitive).content,
        deviceHint = if (archived.credentials != null) deviceHint(archived.credentials.deviceId)
            else existing?.deviceHint ?: (record["deviceHint"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: "****",
        credentials = credentials,
        quota = verified ?: existing?.quota ?: quotaOf(record["quota"]),
        rewardPaused = existing?.rewardPaused ?: ((record["rewardPaused"] as? JsonPrimitive)?.content == "true"),
        usage = usage,
        retained = mergeArchive(JsonObject(record.filterKeys { it !in KnownRecordKeys }), existing?.retained ?: JsonObject(emptyMap())) as JsonObject,
    )
}

internal fun deviceHint(deviceId: String): String = if (deviceId.length <= 4) "****" else "…" + deviceId.takeLast(4)

private fun DebitState.recovered() = if (this == DebitState.Submitting) DebitState.Uncertain else this

/** Incoming entries first, in order; an entry the device already has keeps the device's version. */
private fun mergeReceipts(incoming: List<DebitEntry>, existing: List<DebitEntry>): List<DebitEntry> {
    val merged = LinkedHashMap<String, DebitEntry>()
    incoming.forEach { merged[it.operationId] = it }
    existing.forEach { merged[it.operationId] = it }
    return merged.values.toList()
}

/** The balance a backup remembers, used only for an account that has no login here to ask. */
private fun quotaOf(value: JsonElement?): Quota = try {
    val map = value as JsonObject
    Quota(
        (map["remaining_seconds"] as JsonPrimitive).content.toLong(),
        (map["new_device"] as JsonPrimitive).content.toBooleanStrict(),
        Instant.fromEpochMilliseconds((map["checked_at"] as JsonPrimitive).content.toLong()),
    )
} catch (_: Exception) {
    Quota(0, false, Instant.fromEpochMilliseconds(0))
}

/** Port of XMPlus `_mergeArchive`: maps merge key by key, lists by identity, and the existing value wins a clash. */
internal fun mergeArchive(incoming: JsonElement?, existing: JsonElement?): JsonElement {
    if (incoming is JsonObject && existing is JsonObject) {
        val result = LinkedHashMap(incoming)
        for ((key, value) in existing) result[key] = result[key]?.let { mergeArchive(it, value) } ?: value
        return JsonObject(result)
    }
    if (incoming is JsonArray && existing is JsonArray) {
        fun key(item: JsonElement) = ((item as? JsonObject)?.get("operationId") as? JsonPrimitive)?.takeIf { it.isString }
            ?.let { "operation:${it.content}" } ?: item.toString()
        val result = LinkedHashMap<String, JsonElement>()
        incoming.forEach { result[key(it)] = it }
        existing.forEach { result[key(it)] = it }
        return JsonArray(result.values.toList())
    }
    return existing ?: incoming ?: JsonObject(emptyMap())
}