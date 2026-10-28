package com.maximerollin.aqualog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF14675E), onPrimary = Color.White,
    primaryContainer = Color(0xFFDEEBE3), onPrimaryContainer = Color(0xFF1E3733),
    secondary = Color(0xFF546A64), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDEEBE3), onSecondaryContainer = Color(0xFF1E3733),
    tertiary = Color(0xFF66532E), onTertiary = Color.White,
    background = Color(0xFFF6F4EC), onBackground = Color(0xFF1E3733),
    surface = Color(0xFFFFFEF9), onSurface = Color(0xFF1E3733),
    surfaceVariant = Color(0xFFDEEBE3), onSurfaceVariant = Color(0xFF546A64),
    surfaceContainerLowest = Color(0xFFFFFEF9), surfaceContainerLow = Color(0xFFFFFEF9),
    surfaceContainer = Color(0xFFF6F4EC), surfaceContainerHigh = Color(0xFFDEEBE3),
    surfaceContainerHighest = Color(0xFFFFFEF9),
    outline = Color(0xFF7C9189), outlineVariant = Color(0xFFCCD7CF),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BD8C8), onPrimary = Color(0xFF102D26),
    primaryContainer = Color(0xFF294A3F), onPrimaryContainer = Color(0xFFEDF3E8),
    secondary = Color(0xFFB7C9C0), onSecondary = Color(0xFF102D26),
    secondaryContainer = Color(0xFF294A3F), onSecondaryContainer = Color(0xFFEDF3E8),
    tertiary = Color(0xFFE4C68E), onTertiary = Color(0xFF332A14),
    background = Color(0xFF122622), onBackground = Color(0xFFEDF3E8),
    surface = Color(0xFF1D342E), onSurface = Color(0xFFEDF3E8),
    surfaceVariant = Color(0xFF294A3F), onSurfaceVariant = Color(0xFFB7C9C0),
    surfaceContainerLowest = Color(0xFF122622), surfaceContainerLow = Color(0xFF1D342E),
    surfaceContainer = Color(0xFF1D342E), surfaceContainerHigh = Color(0xFF294A3F),
    surfaceContainerHighest = Color(0xFF1D342E),
    outline = Color(0xFF91AFA2), outlineVariant = Color(0xFF426056),
)

@Composable
fun AquaLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = aquaLogColorScheme(darkTheme),
        shapes = Shapes(
            small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(22.dp),
        ),
        content = content,
    )
}

// Keep the same palette on every Android version, independently of wallpaper.
fun aquaLogColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) DarkColors else LightColors
