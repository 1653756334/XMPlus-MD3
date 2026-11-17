package com.xuleo.xmplus.md3.core.designsystem

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview

/** Wraps a preview in the app theme; light or dark follows the preview's own ui mode. */
@Composable
fun XmPreview(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    XmTheme(darkTheme = darkTheme) {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}

/** Light English, dark Chinese and a large font in Chinese: the combinations that break layouts first. */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "Light en", group = "appearance", locale = "en", showBackground = true)
@Preview(
    name = "Dark zh",
    group = "appearance",
    locale = "zh-rCN",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Preview(name = "Font 2x zh", group = "appearance", locale = "zh-rCN", fontScale = 2f, showBackground = true)
annotation class XmPreviews

/** Window sizes that switch the navigation between the bar and the rail. */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "Phone", group = "size", device = Devices.PHONE, showSystemUi = true)
@Preview(name = "Foldable", group = "size", device = Devices.FOLDABLE, showSystemUi = true)
@Preview(name = "Tablet", group = "size", device = Devices.TABLET, showSystemUi = true)
annotation class XmDevicePreviews
