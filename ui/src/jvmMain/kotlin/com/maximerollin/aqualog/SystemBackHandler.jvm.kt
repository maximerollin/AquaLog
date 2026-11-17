package com.maximerollin.aqualog

import androidx.compose.runtime.Composable

// Desktop has no system Back.
@Composable
internal actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
