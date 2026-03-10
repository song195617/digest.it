package com.digestit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF1D2939)
private val Slate = Color(0xFF52606D)
private val Cream = Color(0xFFF6F1E8)
private val Paper = Color(0xFFFFFBF5)
private val Tide = Color(0xFF0F5B66)
private val TideSoft = Color(0xFFD7ECEE)
private val Amber = Color(0xFF9A6A16)
private val Rose = Color(0xFFB94E63)
private val Moss = Color(0xFF5D7A5A)
private val Error = Color(0xFFB42318)
private val DarkBase = Color(0xFF0E141A)
private val DarkSurface = Color(0xFF172029)
private val DarkPanel = Color(0xFF1F2B35)
private val DarkText = Color(0xFFF4F0E9)
private val DarkMuted = Color(0xFFB9C3CC)

private val LightColorScheme = lightColorScheme(
    primary = Tide,
    onPrimary = Color.White,
    primaryContainer = TideSoft,
    onPrimaryContainer = Ink,
    secondary = Amber,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6E6C8),
    onSecondaryContainer = Ink,
    tertiary = Moss,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDE8DA),
    onTertiaryContainer = Ink,
    background = Cream,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE6E0D6),
    onSurfaceVariant = Slate,
    surfaceContainerLowest = Color(0xFFFFFDF8),
    surfaceContainerLow = Color(0xFFFBF6EE),
    surfaceContainer = Color(0xFFF3EEE5),
    surfaceContainerHigh = Color(0xFFEAE4DA),
    surfaceContainerHighest = Color(0xFFE1DACE),
    outline = Color(0xFFC2B9AC),
    error = Error,
    onError = Color.White,
    errorContainer = Color(0xFFFDE7E4),
    onErrorContainer = Color(0xFF661914),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8FD1DA),
    onPrimary = Color(0xFF06363C),
    primaryContainer = Color(0xFF18474E),
    onPrimaryContainer = Color(0xFFDBF3F5),
    secondary = Color(0xFFE8C785),
    onSecondary = Color(0xFF4A3406),
    secondaryContainer = Color(0xFF65470C),
    onSecondaryContainer = Color(0xFFFFE9B6),
    tertiary = Color(0xFFB6D3B2),
    onTertiary = Color(0xFF1F391D),
    tertiaryContainer = Color(0xFF375235),
    onTertiaryContainer = Color(0xFFD4ECCD),
    background = DarkBase,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkPanel,
    onSurfaceVariant = DarkMuted,
    surfaceContainerLowest = Color(0xFF0A1015),
    surfaceContainerLow = Color(0xFF131B22),
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = Color(0xFF23313B),
    surfaceContainerHighest = Color(0xFF2C3B47),
    outline = Color(0xFF445463),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.6).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
    ),
)

private val AppShapes = Shapes()

@Composable
fun DigestItTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

val platformColor = mapOf(
    "BILIBILI" to Rose,
    "XIAOYUZHOU" to Color(0xFF4E6CC6),
)
