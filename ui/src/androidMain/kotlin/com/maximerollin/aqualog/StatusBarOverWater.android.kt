package com.maximerollin.aqualog

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
actual fun StatusBarOverWater() {
    val dark = isSystemInDarkTheme()
    val view = LocalView.current
    val controller = remember(view) {
        (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view) }
    }
    // A SideEffect, not a DisposableEffect body: it runs after the host's own system-bar SideEffect on every recomposition,
    // so a theme change cannot hand the icons back to the light-theme default while the water is shown.
    SideEffect { controller?.isAppearanceLightStatusBars = false }
    DisposableEffect(controller, dark) {
        onDispose { controller?.isAppearanceLightStatusBars = !dark }
    }
}
