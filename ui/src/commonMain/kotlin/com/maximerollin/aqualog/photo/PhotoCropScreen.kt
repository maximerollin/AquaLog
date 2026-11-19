package com.maximerollin.aqualog.photo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.maximerollin.aqualog.StatusBarOverWater
import com.maximerollin.aqualog.resources.*
import com.maximerollin.aqualog.ui.icon.AquagendaIcons
import com.maximerollin.aqualog.ui.icon.X
import com.maximerollin.aqualog.ui.theme.CropBackdrop
import com.maximerollin.aqualog.ui.theme.GlassIconButton
import com.maximerollin.aqualog.ui.theme.frauncesFamily
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
        Box(Modifier.fillMaxSize().background(CropBackdrop)) {
            StatusBarOverWater(true)
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassIconButton(AquagendaIcons.X, stringResource(Res.string.cancel), onCancel)
                    Text(
                        stringResource(Res.string.photo_crop_title),
                        style = TextStyle(fontFamily = frauncesFamily(), fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val side = minOf(maxWidth, maxHeight)
                    val crop = cropFor(image.width, image.height, viewport.coerceAtLeast(1f), scale, offset)
                    val source = crop.sourceRect(image.width, image.height)
                    val preview = stringResource(Res.string.photo_crop_preview)
                    Box(Modifier.size(side)) {
                        Canvas(
                            Modifier.fillMaxSize().onSizeChanged { viewport = it.width.toFloat() }.transformable(transformable)
                                .semantics { contentDescription = preview },
                        ) {
                            drawImage(
                                image,
                                srcOffset = source.topLeft,
                                srcSize = source.size,
                                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                            )
                        }
                        Canvas(Modifier.fillMaxSize().testTag("crop-grid")) { drawCropGrid() }
                    }
                }
                Text(
                    stringResource(Res.string.photo_crop_hint),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = Color.White.copy(alpha = .85f),
                )
                Button(
                    onClick = { onConfirm(cropFor(image.width, image.height, viewport.coerceAtLeast(1f), scale, offset)) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) { Text(stringResource(Res.string.photo_crop_confirm)) }
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                ) { Text(stringResource(Res.string.cancel)) }
            }
        }
    }
}

/** Border, thirds and corner marks over the square viewport; draws only, takes no input. */
private fun DrawScope.drawCropGrid() {
    val border = 2.dp.toPx()
    val line = 1.dp.toPx()
    val faint = Color.White.copy(alpha = .4f)
    for (i in 1..2) {
        val x = size.width * i / 3f
        val y = size.height * i / 3f
        drawLine(faint, Offset(x, 0f), Offset(x, size.height), strokeWidth = line)
        drawLine(faint, Offset(0f, y), Offset(size.width, y), strokeWidth = line)
    }
    drawRect(Color.White, style = Stroke(border), topLeft = Offset(border / 2, border / 2), size = Size(size.width - border, size.height - border))
    val mark = 26.dp.toPx()
    val thick = 5.dp.toPx()
    val h = thick / 2
    val w = size.width
    val ht = size.height
    for ((cx, sx) in listOf(h to 1f, w - h to -1f)) {
        for ((cy, sy) in listOf(h to 1f, ht - h to -1f)) {
            drawLine(Color.White, Offset(cx - sx * h, cy), Offset(cx + sx * (mark - h), cy), strokeWidth = thick)
            drawLine(Color.White, Offset(cx, cy - sy * h), Offset(cx, cy + sy * (mark - h)), strokeWidth = thick)
        }
    }
}
