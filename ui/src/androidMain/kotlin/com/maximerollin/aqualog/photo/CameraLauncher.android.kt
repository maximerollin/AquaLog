package com.maximerollin.aqualog.photo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.dialogs.compose.rememberCameraPickerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
actual fun rememberCameraLauncher(onResult: (ByteArray?) -> Unit): (() -> Unit)? {
    val scope = rememberCoroutineScope()
    val launcher = rememberCameraPickerLauncher { file ->
        scope.launch(Dispatchers.IO) {
            onResult(file?.photoBytes())
            // The full-size shot sits in the cache; only the cropped JPEG is kept.
            runCatching { file?.delete() }
        }
    }
    return { launcher.launch() }
}
