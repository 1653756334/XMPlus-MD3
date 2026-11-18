package com.xuleo.xmplus.md3.core.protocol

import com.xuleo.xmplus.md3.core.model.XmlyException
import java.net.URI
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Reads server JSON the way the XMPlus parsers do: anything of the wrong shape fails the whole response with [code]
 * rather than yielding a half-filled screen. Each API names its own failure code.
 */
internal class Reader(private val code: String) {
    fun invalid(): Nothing = throw XmlyException(code)

    fun obj(value: JsonElement?): JsonObject = value as? JsonObject ?: invalid()

    /** A list of objects; more than [maximum] rows is as invalid as a row that is not an object. */
    fun rows(value: JsonElement?, maximum: Int = 1000): List<JsonObject> {
        val array = value as? JsonArray ?: invalid()
        if (array.size > maximum) invalid()
        return array.map { obj(it) }
    }

    fun text(value: JsonElement?): String = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""

    /** Any scalar as the string it prints as; objects and arrays count as absent. */
    fun anyText(value: JsonElement?): String = (value as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content ?: ""

    fun boolean(value: JsonElement?): Boolean? = (value as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

    /** An integer sent as a number or as digits in a string; fractions, overflow and everything else are null. */
    fun integer(value: JsonElement?): Long? {
        val primitive = value as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        if (primitive.isString) return primitive.content.takeIf { IntegerPattern.matches(it) }?.toLongOrNull()
        primitive.longOrNull?.let { return it }
        val number = primitive.doubleOrNull ?: return null
        return if (number.isFinite() && number == Math.floor(number) && Math.abs(number) < 9.2e18) number.toLong() else null
    }

    fun count(value: JsonElement?): Long? = integer(value)?.takeIf { it >= 0 }

    fun countInt(value: JsonElement?): Int? = count(value)?.takeIf { it <= Int.MAX_VALUE }?.toInt()

    fun positive(value: JsonElement?): Int = integer(value)?.takeIf { it in 1..Int.MAX_VALUE }?.toInt() ?: invalid()

    /** A Ximalaya id: 1 to 19 digits, no leading zero, sent as a number or a string. */
    fun id(value: JsonElement?): String {
        val text = anyText(value)
        if (!IdPattern.matches(text)) invalid()
        return text
    }

    /** Server paths are relative to the image host; this makes them absolute without judging them. */
    fun coverPath(value: JsonElement?): String? {
        val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        if (text.length > 16384 || text.any { it.code <= 0x20 }) return null
        return when {
            text.startsWith("//") -> "https:$text"
            text.startsWith("storages/") || GroupPath.containsMatchIn(text) -> "https://imagev2.xmcdn.com/$text"
            else -> text
        }
    }

    /** The first usable image among [values], in order; an http link on the image host is upgraded to https. */
    fun cover(vararg values: JsonElement?): String? {
        for (value in values) {
            val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
            if (text.length > 16384 || text.any { it.code <= 0x20 }) continue
            val absolute = if (text.startsWith("//")) "https:$text" else text
            val uri = runCatching { URI(absolute) }.getOrNull() ?: continue
            val host = uri.host
            val scheme = uri.scheme?.lowercase()
            if (scheme !in WebSchemes || host.isNullOrEmpty() || uri.userInfo != null) continue
            return if (scheme == "http" && (host == "xmcdn.com" || host.endsWith(".xmcdn.com"))) "https" + absolute.substring(4) else absolute
        }
        return null
    }

    /** An avatar or cover given by one field, with server paths made absolute first. */
    fun image(value: JsonElement?): String? = cover(coverPath(value)?.let(::JsonPrimitive))

    fun score(value: JsonElement?): Double? {
        val primitive = value as? JsonPrimitive ?: return null
        val number = if (primitive.isString) primitive.content.toDoubleOrNull() else primitive.doubleOrNull
        return number?.takeIf { it.isFinite() }
    }

    private companion object {
        val IntegerPattern = Regex("^-?[0-9]+$")
        val IdPattern = Regex("^[1-9][0-9]{0,18}$")
        val GroupPath = Regex("^group[0-9]+/")
        val WebSchemes = setOf("http", "https")
    }
}