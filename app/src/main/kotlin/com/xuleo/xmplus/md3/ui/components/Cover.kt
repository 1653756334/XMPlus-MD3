package com.xuleo.xmplus.md3.ui.components

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/** A cover or avatar over a quiet placeholder; the image fades in unless the system has animations turned off. */
@Composable
fun Cover(url: String?, modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.medium) {
    val context = LocalContext.current
    val animated = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(url).crossfade(animated).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun Avatar(url: String?, modifier: Modifier = Modifier) = Cover(url, modifier, CircleShape)