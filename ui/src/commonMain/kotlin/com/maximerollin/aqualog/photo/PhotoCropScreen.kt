package com.maximerollin.aqualog.photo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.maximerollin.aqualog.resources.*
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/** Pinch to zoom and drag to frame the square kept for the Aquarium; the viewport shows exactly what is saved. */
@Composable
fun PhotoCropScreen(image: ImageBitmap, onConfirm: (SquareCrop) -> Unit, onCancel: () -> Unit) {
    var scale by remember(image) { mutableFloatStateOf(1f) }
    var offset by remember(image) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableFloatStateOf(0f) }
    val transformable = rememberTransformableState { zoom, pan, _ ->
        val next = applyGesture(image.width, image.height, viewport.coerceAtLeast(1f), scale, offset, zoom, pan)
        scale = next.first
        offset = next.second
    }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(Res.string.photo_crop_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val side = minOf(maxWidth, maxHeight)
                    val crop = cropFor(image.width, image.height, viewport.coerceAtLeast(1f), scale, offset)
                    val source = crop.sourceRect(image.width, image.height)
                    val preview = stringResource(Res.string.photo_crop_preview)
                    Canvas(
                        Modifier.size(side).onSizeChanged { viewport = it.width.toFloat() }.transformable(transformable)
                            .semantics { contentDescription = preview },
                    ) {
                        drawImage(
                            image,
                            srcOffset = source.topLeft,
                            srcSize = source.size,
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.cancel))
                    }
                    Button(
                        onClick = { onConfirm(cropFor(image.width, image.height, viewport.coerceAtLeast(1f), scale, offset)) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text(stringResource(Res.string.photo_crop_confirm)) }
                }
            }
        }
    }
}
