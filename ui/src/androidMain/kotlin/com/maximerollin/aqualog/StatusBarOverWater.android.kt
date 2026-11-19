package com.maximerollin.aqualog

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

@Composable
actual fun StatusBarOverWater(overWater: Boolean) {
    val dark = isSystemInDarkTheme()
    val view = LocalView.current
    val controller = remember(view) {
        // Inside a dialog drawn under the bars (the photo crop), its own window owns the icons, not the activity's.
        val window = (view as? DialogWindowProvider ?: view.parent as? DialogWindowProvider)?.window
            ?: (view.context as? Activity)?.window
        window?.let { WindowCompat.getInsetsController(it, view) }
    }
    // A SideEffect, not a DisposableEffect body: it runs after the host's own system-bar SideEffect on every recomposition,
    // so a theme change cannot hand the icons back to the light-theme default while the water is shown.
    SideEffect { controller?.isAppearanceLightStatusBars = !overWater && !dark }
    DisposableEffect(controller, dark) {
        onDispose { controller?.isAppearanceLightStatusBars = !dark }
    }
}
