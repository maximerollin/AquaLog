package com.maximerollin.aqualog.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// shortcut: spike-only placeholder palette, replaced by the real theme when the UI moves here (Stage 4).
@Composable
fun AquaLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Color(0xFF14675E), background = Color(0xFFF6F4EC)),
        content = content,
    )
}
