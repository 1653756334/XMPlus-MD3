package com.xuleo.xmplus.md3.ui.components

import android.text.Html
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/** One part of an introduction: a run of text, or a picture on a line of its own. */
sealed interface IntroBlock {
    data class Text(val text: String) : IntroBlock

    /** [ratio] is the width over the height the markup gives the picture, or 0 when it gives none. */
    data class Image(val url: String, val ratio: Float) : IntroBlock
}

private val ImageTag = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
private val Attribute = Regex("([a-zA-Z-]+)\\s*=\\s*[\"']([^\"']*)[\"']")
private const val DefaultRatio = 4f / 3f
private const val MaxUrlLength = 2048

/**
 * Splits an introduction's HTML into text and pictures, in the order the publisher wrote them. Picture addresses are made
 * https, as the original app makes them; one without a usable https address is left out.
 */
fun introBlocks(html: String): List<IntroBlock> {
    val blocks = mutableListOf<IntroBlock>()
    var last = 0
    for (match in ImageTag.findAll(html)) {
        addText(blocks, html.substring(last, match.range.first))
        val attributes = Attribute.findAll(match.value).associate { it.groupValues[1].lowercase() to decode(it.groupValues[2]) }
        secureUrl(attributes["src"])?.let { blocks += IntroBlock.Image(it, ratioOf(attributes)) }
        last = match.range.last + 1
    }
    addText(blocks, html.substring(last))
    return blocks
}

/** The HTML as plain text, with paragraph and line breaks kept. */
fun plainText(html: String): String = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim()

/** The introduction's text and pictures, in order. */
@Composable
fun RichIntroduction(blocks: List<IntroBlock>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { block ->
            when (block) {
                is IntroBlock.Text -> Text(block.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                is IntroBlock.Image -> IntroImage(block.url, block.ratio)
            }
        }
    }
}

/** One picture, as wide as the column. Until it arrives it takes the shape the markup gives it; then its own. */
@Composable
private fun IntroImage(url: String, ratio: Float) {
    val context = LocalContext.current
    var picture by remember(url) { mutableFloatStateOf(if (ratio > 0f) ratio else DefaultRatio) }
    AsyncImage(
        model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        onSuccess = { success ->
            val size = success.painter.intrinsicSize
            if (size.width > 0f && size.height > 0f) picture = size.width / size.height
        },
        modifier = Modifier.fillMaxWidth().aspectRatio(picture).clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

private fun addText(blocks: MutableList<IntroBlock>, html: String) {
    val text = plainText(html)
    if (text.isNotEmpty()) blocks += IntroBlock.Text(text)
}

private fun decode(value: String): String = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()

/** Only https addresses are loaded. Protocol-relative and http addresses are upgraded, as the original app does. */
private fun secureUrl(raw: String?): String? {
    val value = decode(raw.orEmpty()).trim()
    val url = when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("http://") -> "https://" + value.removePrefix("http://")
        else -> value
    }
    return url.takeIf { it.startsWith("https://") && it.length <= MaxUrlLength && it.none { c -> c <= ' ' } }
}

private fun ratioOf(attributes: Map<String, String>): Float {
    val width = (attributes["data-large-width"] ?: attributes["width"])?.toFloatOrNull() ?: return 0f
    val height = (attributes["data-large-height"] ?: attributes["height"])?.toFloatOrNull() ?: return 0f
    return if (width > 0f && height > 0f) width / height else 0f
}