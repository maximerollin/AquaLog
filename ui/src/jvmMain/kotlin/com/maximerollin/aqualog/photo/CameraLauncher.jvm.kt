package com.maximerollin.aqualog.photo

import androidx.compose.runtime.Composable

@Composable
actual fun rememberCameraLauncher(onResult: (ByteArray?) -> Unit): (() -> Unit)? = null
