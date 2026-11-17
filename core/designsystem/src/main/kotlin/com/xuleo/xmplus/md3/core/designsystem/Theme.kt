package com.xuleo.xmplus.md3.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme

object XmColors {
    /** The default accent: a clean green in the spirit of QQ Music, on neutral white surfaces. */
    val Brand = Color(0xFF0E9F6E)

    val Light: ColorScheme = lightColorScheme(
        primary = Color(0xFF007F57),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDDF5EA),
        onPrimaryContainer = Color(0xFF00382A),
        secondary = Color(0xFF4F6B5F),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE6F0EB),
        onSecondaryContainer = Color(0xFF0F1F18),
        tertiary = Color(0xFF456179),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD8E6F2),
        onTertiaryContainer = Color(0xFF0B1D2B),
        background = Color.White,
        onBackground = Color(0xFF191C1A),
        surface = Color.White,
        onSurface = Color(0xFF191C1A),
        surfaceVariant = Color(0xFFEEF1EF),
        onSurfaceVariant = Color(0xFF5B605D),
        outline = Color(0xFF8C918E),
        outlineVariant = Color(0xFFE3E6E4),
        inverseSurface = Color(0xFF2E312F),
        inverseOnSurface = Color(0xFFF1F1F0),
        inversePrimary = Color(0xFF55DBA0),
        surfaceBright = Color.White,
        surfaceDim = Color(0xFFE6E8E6),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF8F9F8),
        surfaceContainer = Color(0xFFF3F4F3),
        surfaceContainerHigh = Color(0xFFEEEFEE),
        surfaceContainerHighest = Color(0xFFE8EAE8),
    )

    val Dark: ColorScheme = darkColorScheme(
        primary = Color(0xFF3DDC97),
        onPrimary = Color(0xFF003823),
        primaryContainer = Color(0xFF005234),
        onPrimaryContainer = Color(0xFF9CF6C6),
        secondary = Color(0xFFB4CCBF),
        onSecondary = Color(0xFF20352B),
        secondaryContainer = Color(0xFF364B41),
        onSecondaryContainer = Color(0xFFD0E8DB),
        tertiary = Color(0xFFADCAE3),
        onTertiary = Color(0xFF123349),
        tertiaryContainer = Color(0xFF2D4960),
        onTertiaryContainer = Color(0xFFD0E5FF),
        background = Color(0xFF111312),
        onBackground = Color(0xFFE1E3E0),
        surface = Color(0xFF111312),
        onSurface = Color(0xFFE1E3E0),
        surfaceVariant = Color(0xFF2A2D2B),
        onSurfaceVariant = Color(0xFFC1C7C3),
        outline = Color(0xFF8B918D),
        outlineVariant = Color(0xFF3A3E3C),
        inverseSurface = Color(0xFFE1E3E0),
        inverseOnSurface = Color(0xFF2E312F),
        inversePrimary = Brand,
        surfaceBright = Color(0xFF373938),
        surfaceDim = Color(0xFF111312),
        surfaceContainerLowest = Color(0xFF0C0E0D),
        surfaceContainerLow = Color(0xFF191B1A),
        surfaceContainer = Color(0xFF1D1F1E),
        surfaceContainerHigh = Color(0xFF282A29),
        surfaceContainerHighest = Color(0xFF323433),
    )

    /** Pure black surfaces for OLED screens. */
    val Oled: ColorScheme = Dark.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0A0B0A),
        surfaceContainer = Color(0xFF111211),
        surfaceContainerHigh = Color(0xFF1A1C1B),
        surfaceContainerHighest = Color(0xFF242625),
    )
}

/**
 * The app theme. Without a [seedColor] it uses the neutral white scheme above; a user-chosen seed
 * generates a tonal palette from it instead.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun XmTheme(
    seedColor: Color? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    oledBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (seedColor != null) {
        rememberDynamicColorScheme(
            seedColor = seedColor,
            isDark = darkTheme,
            isAmoled = darkTheme && oledBlack,
            style = PaletteStyle.TonalSpot,
        )
    } else if (darkTheme) {
        if (oledBlack) XmColors.Oled else XmColors.Dark
    } else {
        XmColors.Light
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
