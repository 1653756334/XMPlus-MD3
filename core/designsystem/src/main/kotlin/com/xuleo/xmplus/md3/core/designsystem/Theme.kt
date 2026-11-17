package com.xuleo.xmplus.md3.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme

object XmColors {
    /** 松绿, the default theme seed carried over from XMPlus. */
    val DefaultSeed = Color(0xFF245C56)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun XmTheme(
    seedColor: Color = XmColors.DefaultSeed,
    darkTheme: Boolean = isSystemInDarkTheme(),
    oledBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = rememberDynamicColorScheme(
        seedColor = seedColor,
        isDark = darkTheme,
        isAmoled = darkTheme && oledBlack,
        style = PaletteStyle.TonalSpot,
    )
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}