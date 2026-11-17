package com.xuleo.xmplus.md3.core.account

import com.xuleo.xmplus.md3.core.model.XmlyException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The ledger as XMPlus wrote it (`accounts[identity].ledger`). The native store and the XMPlus backup importer share
 * it, so a record survives a round trip through either. Anything malformed is an "invalid_ledger" failure: the data is
 * kept, never repaired.
 */
object UsageStateCodec {
    private val TrackPattern = Regex("(?:xmly:)?([1-9][0-9]{0,18})")

    fun encode(state: UsageState, identity: String): JsonObject = buildJsonObject {
        put("usage_seconds", state.debtSeconds)
        put("usage_uncertain", state.uncertain)
        state.pending?.let { put("usage_pending", encode(it, identity)) }
        put("receipts", JsonArray(state.receipts.map { encode(it, identity) }))
    }

    fun decode(ledger: JsonObject, identity: String): UsageState {
        val debt = when (val value = ledger["usage_seconds"]) {
            null, JsonNull -> 0.0
            is JsonPrimitive -> value.takeUnless { it.isString }?.content?.toDoubleOrNull()
            else -> null
        }?.takeIf { it.isFinite() && it >= 0 } ?: invalid()
        val uncertain = when (val value = ledger["usage_uncertain"]) {
            null, JsonNull -> false
            is JsonPrimitive -> value.takeUnless { it.isString }?.content?.toBooleanStrictOrNull() ?: invalid()
            else -> invalid()
        }
        val pending = when (val value = ledger["usage_pending"]) {
            null, JsonNull -> null
            is JsonObject -> decodeEntry(value, identity, pending = true)
            else -> invalid()
        }
        val receipts = when (val value = ledger["receipts"]) {
            null, JsonNull -> emptyList()
            is JsonArray -> value.map { decodeEntry(it as? JsonObject ?: invalid(), identity, pending = false) }
            else -> invalid()
        }
        return UsageState(debt, pending, uncertain, receipts)
    }

    private fun encode(entry: DebitEntry, identity: String): JsonObject = buildJsonObject {
        put("operationId", entry.operationId)
        put("accountIdentity", identity)
        put("trackId", entry.trackId)
        put("taskId", entry.taskId)
        put("seconds", entry.seconds)
        put("balanceBefore", entry.balanceBefore)
        put("balanceAfter", entry.balanceAfter?.let(::JsonPrimitive) ?: JsonNull)
        put("createdAt", entry.createdAt)
        put("state", if (entry.historical && entry.state == DebitState.Confirmed) "historical" else entry.state.key)
        if (entry.historical) {
            put("historical", true)
            put("downloadExempt", false)
        }
    }

    private fun decodeEntry(value: JsonObject, identity: String, pending: Boolean): DebitEntry {
        val historical = value["historical"].let { it is JsonPrimitive && !it.isString && it.content == "true" } ||
            value["state"].let { it is JsonPrimitive && it.isString && it.content == "historical" }
        val stateKey = (value["state"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val state = if (stateKey == "historical") DebitState.Confirmed else DebitState.of(stateKey) ?: invalid()
        if (pending && state != DebitState.Uncertain && state != DebitState.Submitting) invalid()
        fun text(key: String, required: Boolean): String {
            val field = value[key]
            val primitive = field as? JsonPrimitive
            // An online debit has no task, and may have no track either: those are written as empty or left out.
            if (!required && (field == null || field == JsonNull || primitive?.isString == true && primitive.content.isEmpty())) return ""
            return primitive?.takeIf { it.isString && it.content.isNotEmpty() }?.content ?: invalid()
        }
        fun integer(key: String): Long? = (value[key] as? JsonPrimitive)?.takeUnless { it.isString }?.content?.toLongOrNull()
        val seconds = integer("seconds")?.takeIf { it in 1..Int.MAX_VALUE } ?: invalid()
        val before = integer("balanceBefore")?.takeIf { it >= 0 } ?: invalid()
        val after = when (val field = value["balanceAfter"]) {
            null, JsonNull -> null
            else -> (field as? JsonPrimitive)?.takeUnless { it.isString }?.content?.toLongOrNull()?.takeIf { it >= 0 } ?: invalid()
        }
        if (text("accountIdentity", required = true) != identity) throw XmlyException("invalid_ledger")
        // An online debit may predate any track; a receipt always names one, normalised to `xmly:<id>`.
        val rawTrack = text("trackId", required = !pending)
        val track = if (rawTrack.isEmpty()) "" else "xmly:" + (TrackPattern.matchEntire(rawTrack)?.groupValues?.get(1) ?: invalid())
        return DebitEntry(
            operationId = text("operationId", required = true), trackId = track, taskId = text("taskId", required = !pending),
            seconds = seconds.toInt(), balanceBefore = before, balanceAfter = after,
            createdAt = integer("createdAt") ?: invalid(), state = state, historical = historical,
        )
    }

    private fun invalid(): Nothing = throw XmlyException("invalid_ledger")
}