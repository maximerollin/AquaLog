package com.maximerollin.aqualog

import androidx.compose.runtime.Composable

/** True when the system asks for no decorative animation. */
@Composable
expect fun rememberReducedMotion(): Boolean
