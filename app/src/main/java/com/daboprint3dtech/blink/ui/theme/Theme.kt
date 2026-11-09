package com.daboprint3dtech.blink.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val BlinkBlue = Color(0xFF0B6BCB)

private val LightColors = lightColorScheme(
    primary = BlinkBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001C38),
    secondary = Color(0xFF545F70),
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFF7F9FC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA2C9FF),
    onPrimary = Color(0xFF00315C),
    primaryContainer = Color(0xFF004882),
    onPrimaryContainer = Color(0xFFD3E4FF),
    background = Color(0xFF101418),
    surface = Color(0xFF101418),
)

@Composable
fun BlinkTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Colori di stato usati nei badge. */
object StatusColors {
    val Armed = Color(0xFF2E7D32)
    val Disarmed = Color(0xFF757575)
    val Warning = Color(0xFFF9A825)
    val Error = Color(0xFFC62828)
}
