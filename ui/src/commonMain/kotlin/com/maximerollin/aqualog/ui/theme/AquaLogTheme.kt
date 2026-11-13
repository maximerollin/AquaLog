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
import androidx.compose.ui.graphics.luminance
import com.maximerollin.aqualog.shared.assistance.ReadingLevel
import androidx.compose.ui.unit.dp

val CarnetLightColors = lightColorScheme(
    primary = Color(0xFF14675E), onPrimary = Color.White,
    primaryContainer = Color(0xFFDEEBE3), onPrimaryContainer = Color(0xFF1E3733),
    secondary = Color(0xFF546A64), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDEEBE3), onSecondaryContainer = Color(0xFF1E3733),
    tertiary = Color(0xFF66532E), onTertiary = Color.White,
    background = Color(0xFFF3EFE3), onBackground = Color(0xFF1E3733),
    surface = Color(0xFFFFFEF9), onSurface = Color(0xFF1E3733),
    surfaceVariant = Color(0xFFDEEBE3), onSurfaceVariant = Color(0xFF546A64),
    surfaceContainerLowest = Color(0xFFFFFEF9), surfaceContainerLow = Color(0xFFFFFEF9),
    surfaceContainer = Color(0xFFF6F4EC), surfaceContainerHigh = Color(0xFFDEEBE3),
    surfaceContainerHighest = Color(0xFFFFFEF9),
    outline = Color(0xFF7C9189), outlineVariant = Color(0xFFCCD7CF),
)
val CarnetDarkColors = darkColorScheme(
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

// Attention = value outside its indicative range. Text is amber-brown (>= 4.5:1 on paper), the mark is the brighter dot/line
// (>= 3:1 non-text contrast on paper, so a shade darker than the mockup's #C98A1B).
val AttentionText = Color(0xFF8A5A00)
val AttentionMark = Color(0xFFB87A10)
val AttentionTextDark = Color(0xFFE4B65A)
val AttentionMarkDark = Color(0xFFE0A93B)

// Alert = value beyond its alert threshold; same text/mark contrast rules as attention.
val AlertText = Color(0xFFA3341F)
val AlertMark = Color(0xFFB83A25)
val AlertTextDark = Color(0xFFFF8A75)
val AlertMarkDark = Color(0xFFF2705A)

@Composable
fun attentionTextColor(): Color = if (isDarkSurface()) AttentionTextDark else AttentionText

@Composable
fun attentionMarkColor(): Color = if (isDarkSurface()) AttentionMarkDark else AttentionMark

/** Text colour of a reading at [level]; null for OK so callers keep their default. */
@Composable
fun levelTextColor(level: ReadingLevel): Color? = when (level) {
    ReadingLevel.OK -> null
    ReadingLevel.ATTENTION -> attentionTextColor()
    ReadingLevel.ALERT -> if (isDarkSurface()) AlertTextDark else AlertText
}

// Tile backgrounds for out-of-range values; OK keeps the neutral surface.
private val AttentionContainer = Color(0xFFFBF0D9)
private val AlertContainer = Color(0xFFF7E1DB)
private val AttentionContainerDark = Color(0xFF3A3220)
private val AlertContainerDark = Color(0xFF3D2420)

/** Background of a tile showing a reading at [level]; null for OK so callers keep their neutral surface. */
@Composable
fun levelContainerColor(level: ReadingLevel): Color? = when (level) {
    ReadingLevel.OK -> null
    ReadingLevel.ATTENTION -> if (isDarkSurface()) AttentionContainerDark else AttentionContainer
    ReadingLevel.ALERT -> if (isDarkSurface()) AlertContainerDark else AlertContainer
}

@Composable
fun levelMarkColor(level: ReadingLevel): Color? = when (level) {
    ReadingLevel.OK -> null
    ReadingLevel.ATTENTION -> attentionMarkColor()
    ReadingLevel.ALERT -> if (isDarkSurface()) AlertMarkDark else AlertMark
}

// Follows the theme actually applied, not the system setting, so an explicit darkTheme argument is honoured.
@Composable
private fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
fun AquaLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = aquaLogColorScheme(darkTheme),
        typography = carnetTypography(),
        shapes = Shapes(
            small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(22.dp),
        ),
        content = content,
    )
}

// Keep the same palette on every Android version, independently of wallpaper.
fun aquaLogColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) CarnetDarkColors else CarnetLightColors
