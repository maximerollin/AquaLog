package com.maximerollin.aqualog

import androidx.compose.runtime.Composable

/** Returns the action asking for the task reminder permission, or null when nothing has to be asked. */
@Composable
expect fun rememberNotificationPermissionRequest(): (() -> Unit)?
