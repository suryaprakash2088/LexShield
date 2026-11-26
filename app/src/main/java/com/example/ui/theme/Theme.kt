package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = JudicialNavy,
    onPrimary = Color.White,
    primaryContainer = JudicialNavyContainer,
    onPrimaryContainer = JudicialNavy,
    secondary = SovereignGold,
    onSecondary = Color.White,
    secondaryContainer = SovereignGoldContainer,
    onSecondaryContainer = Color(0xFF4A3507),
    tertiary = VerifiedEmerald,
    onTertiary = Color.White,
    tertiaryContainer = VerifiedEmeraldContainer,
    onTertiaryContainer = Color(0xFF064E48),
    error = EmergencyCrimson,
    onError = Color.White,
    errorContainer = EmergencyCrimsonContainer,
    onErrorContainer = Color(0xFF7F1D1D),
    background = IvoryBackground,
    onBackground = InkPrimary,
    surface = IvorySurface,
    onSurface = InkPrimary,
    surfaceVariant = IvorySurfaceVariant,
    onSurfaceVariant = InkSecondary,
    outline = SlateBorder
)

private val DarkColorScheme = darkColorScheme(
    primary = SovereignGoldBright,
    onPrimary = JudicialNavy,
    primaryContainer = JudicialNavyLight,
    onPrimaryContainer = Color.White,
    secondary = SovereignGold,
    onSecondary = JudicialNavy,
    secondaryContainer = Color(0xFF3E2D0C),
    onSecondaryContainer = SovereignGoldContainer,
    tertiary = VerifiedEmeraldBright,
    onTertiary = Color(0xFF002D29),
    tertiaryContainer = Color(0xFF0A3D38),
    onTertiaryContainer = VerifiedEmeraldContainer,
    error = EmergencyCrimsonBright,
    onError = Color.White,
    errorContainer = EmergencyCrimsonDarkContainer,
    onErrorContainer = Color(0xFFFECACA),
    background = DarkBackground,
    onBackground = DarkInkPrimary,
    surface = DarkSurface,
    onSurface = DarkInkPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkInkSecondary,
    outline = DarkBorder
)

val LexShieldShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LexShieldTheme(
    themeMode: com.example.data.AppThemeMode = com.example.data.AppThemeMode.LIGHT,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        com.example.data.AppThemeMode.LIGHT -> false
        com.example.data.AppThemeMode.DARK -> true
        com.example.data.AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LexShieldTypography,
        shapes = LexShieldShapes,
        content = content
    )
}
