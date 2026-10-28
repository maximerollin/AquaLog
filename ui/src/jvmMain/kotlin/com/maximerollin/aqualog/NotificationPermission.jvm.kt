package com.maximerollin.aqualog

import androidx.compose.runtime.Composable

// Desktop has no reminder notifications to allow.
@Composable
actual fun rememberNotificationPermissionRequest(): (() -> Unit)? = null
