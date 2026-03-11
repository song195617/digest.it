package com.digestit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PrimaryColor = Color(0xFF6200EE)
private val SecondaryColor = Color(0xFF03DAC6)
private val BackgroundColor = Color(0xFFF8F8F8)
private val SurfaceColor = Color(0xFFFFFFFF)
private val BilibiliColor = Color(0xFFFF6699)
private val XiaoyuzhouColor = Color(0xFF5E8AEB)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryColor,
    secondary = SecondaryColor,
    background = BackgroundColor,
    surface = SurfaceColor
)

@Composable
fun DigestItTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}

// Platform brand colors
val platformColor = mapOf(
    "BILIBILI" to BilibiliColor,
    "XIAOYUZHOU" to XiaoyuzhouColor
)
