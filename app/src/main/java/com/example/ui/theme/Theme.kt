package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val TubeDeckDarkScheme = darkColorScheme(
    primary = SignalCrimson,
    onPrimary = Color.White,
    primaryContainer = SignalCrimsonContainer,
    onPrimaryContainer = Color(0xFFFFD9DC),
    secondary = ElectricMint,
    onSecondary = Color(0xFF04210E),
    secondaryContainer = ElectricMintContainer,
    onSecondaryContainer = Color(0xFFA6F4C5),
    tertiary = CyanTelemetry,
    onTertiary = Color(0xFF041E2B),
    tertiaryContainer = CyanTelemetryContainer,
    onTertiaryContainer = Color(0xFFBAE6FD),
    background = ObsidianCanvas,
    onBackground = TextPrimary,
    surface = CarbonSurface,
    onSurface = TextPrimary,
    surfaceVariant = CarbonElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianCanvas,
    surfaceContainerLow = CarbonSurface,
    surfaceContainer = CarbonElevated,
    surfaceContainerHigh = CarbonHigh,
    outline = HairlineBorder,
    outlineVariant = Color(0xFF1E2330),
    error = Color(0xFFFF5A5F),
    onError = Color.White
)

private val TubeDeckLightScheme = lightColorScheme(
    primary = SignalCrimsonDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3E6),
    onPrimaryContainer = Color(0xFF5E0D17),
    secondary = Color(0xFF0E9F47),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5F9E2),
    onSecondaryContainer = Color(0xFF043817),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0F2FE),
    onTertiaryContainer = Color(0xFF082F49),
    background = LightCanvas,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = Color(0xFFD5DBE6)
)

val TubeDeckShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun TubeDeckTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) TubeDeckDarkScheme else TubeDeckLightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TubeDeckTypography,
        shapes = TubeDeckShapes,
        content = content
    )
}
