package com.maximerollin.aqualog.photo

import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.dialogs.compose.rememberCameraPickerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberCameraLauncher(onResult: (ByteArray?) -> Unit): (() -> Unit)? {
    val scope = rememberCoroutineScope()
    val launcher = rememberCameraPickerLauncher { file ->
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                file?.photoBytes().also {
                    // The full-size shot sits in the cache; only the cropped JPEG is kept.
                    runCatching { file?.delete() }
                }
            }
            onResult(bytes)
        }
    }
    if (!LocalContext.current.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) return null
    return {
        try {
            launcher.launch()
        } catch (_: ActivityNotFoundException) {
            onResult(null)
        }
    }
}
