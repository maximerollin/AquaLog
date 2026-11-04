package com.maximerollin.aqualog.photo

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import kotlin.coroutines.cancellation.CancellationException

/** Opens the camera and hands back the photo's bytes (null when cancelled); null where there is no camera. */
@Composable
expect fun rememberCameraLauncher(onResult: (ByteArray?) -> Unit): (() -> Unit)?

/** The picked file's bytes; a file that cannot be read gives empty bytes, which [normalizePhoto] reports as unreadable. */
internal suspend fun PlatformFile.photoBytes(): ByteArray = try {
    readBytes()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    ByteArray(0)
}
