package com.example.ui.theme

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

private val DarkColorScheme =
  darkColorScheme(
    primary = CyberPrimary,
    secondary = CyberSecondary,
    tertiary = CyberTertiary,
    background = CyberDarkBg,
    surface = CyberCardBg,
    onPrimary = Color(0xFF381E72), // CyberSecondary raw
    onSecondary = CyberPrimary,
    onTertiary = CyberWhite,
    onBackground = CyberWhite,
    onSurface = CyberWhite,
    surfaceVariant = CyberCardBg,
    outline = ImmersiveDivider
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF6750A4), 
    secondary = Color(0xFF625B71),
    tertiary = Color(0xFF7D5260),
    background = Color(0xFFF6F8FE),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disabling dynamic colors by default to preserve the rich immersive visual design language
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
