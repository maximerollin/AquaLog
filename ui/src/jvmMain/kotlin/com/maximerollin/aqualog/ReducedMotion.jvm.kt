package com.maximerollin.aqualog

import androidx.compose.runtime.Composable

// Desktop exposes no reduced-motion setting.
@Composable
actual fun rememberReducedMotion(): Boolean = false
