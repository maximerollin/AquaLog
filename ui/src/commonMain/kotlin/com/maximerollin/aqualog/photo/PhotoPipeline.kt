package com.maximerollin.aqualog.photo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.ImageFormat
import io.github.vinceglb.filekit.compressImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Fractions of the source: [left] of its width, [top] of its height, [side] of its shorter side. */
data class SquareCrop(val left: Float, val top: Float, val side: Float)

const val MAX_PHOTO_SCALE = 4f
private const val MAX_SIDE = 1200
private const val MAX_BYTES = 300_000

fun centredCrop(width: Int, height: Int): SquareCrop {
    val short = min(width, height)
    return SquareCrop((width - short) / 2f / width, (height - short) / 2f / height, 1f)
}

/** How far the image may slide, in viewport pixels, before the square leaves it. */
internal fun panLimit(imageWidth: Int, imageHeight: Int, viewport: Float, scale: Float): Offset {
    val displayed = viewport / min(imageWidth, imageHeight) * scale
    return Offset(max(0f, (imageWidth * displayed - viewport) / 2), max(0f, (imageHeight * displayed - viewport) / 2))
}

/**
 * The square the viewport shows when the image covers it, magnified by [scale] and slid by [offset]
 * (an offset moves the image, so a positive one shows more of the left/top), kept inside the image.
 */
fun cropFor(imageWidth: Int, imageHeight: Int, viewport: Float, scale: Float, offset: Offset): SquareCrop {
    val limit = panLimit(imageWidth, imageHeight, viewport, scale)
    val pixelsPerViewportPixel = min(imageWidth, imageHeight) / (viewport * scale)
    val sidePixels = viewport * pixelsPerViewportPixel
    fun start(length: Int, pan: Float, limit: Float) =
        ((length - sidePixels) / 2 - pan.coerceIn(-limit, limit) * pixelsPerViewportPixel)
            .coerceIn(0f, max(0f, length - sidePixels)) / length
    return SquareCrop(
        left = start(imageWidth, offset.x, limit.x),
        top = start(imageHeight, offset.y, limit.y),
        side = sidePixels / min(imageWidth, imageHeight),
    )
}

/**
 * EXIF rotation applied, at most 2400 px a side; null when the bytes are not an image.
 * A JPEG stays JPEG; anything else goes through PNG so transparent pixels reach [encodeAquariumPhoto]
 * and turn white (a JPEG intermediate would turn them black).
 */
@OptIn(ExperimentalResourceApi::class)
suspend fun normalizePhoto(source: ByteArray): ImageBitmap? = try {
    val isJpeg = source.size > 2 && source[0] == 0xFF.toByte() && source[1] == 0xD8.toByte()
    val normalized = FileKit.compressImage(
        bytes = source,
        imageFormat = if (isJpeg) ImageFormat.JPEG else ImageFormat.PNG,
        quality = if (isJpeg) 90 else 100,
        maxWidth = 2400,
        maxHeight = 2400,
    )
    withContext(Dispatchers.Default) { normalized.decodeToImageBitmap() }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
} catch (_: OutOfMemoryError) {
    null
}

/** The square of source pixels a crop selects: left, top and side, kept inside the image. */
internal fun SquareCrop.sourceRect(imageWidth: Int, imageHeight: Int): IntRect {
    val sidePixels = (side * min(imageWidth, imageHeight)).roundToInt().coerceIn(1, min(imageWidth, imageHeight))
    val x = (left * imageWidth).roundToInt().coerceIn(0, imageWidth - sidePixels)
    val y = (top * imageHeight).roundToInt().coerceIn(0, imageHeight - sidePixels)
    return IntRect(x, y, x + sidePixels, y + sidePixels)
}

/** The state after a pinch/drag step: the point under the viewport centre stays put while zooming. */
internal fun applyGesture(
    imageWidth: Int,
    imageHeight: Int,
    viewport: Float,
    scale: Float,
    offset: Offset,
    zoom: Float,
    pan: Offset,
): Pair<Float, Offset> {
    val newScale = (scale * zoom).coerceIn(1f, MAX_PHOTO_SCALE)
    val limit = panLimit(imageWidth, imageHeight, viewport, newScale)
    val moved = offset * (newScale / scale) + pan
    return newScale to Offset(moved.x.coerceIn(-limit.x, limit.x), moved.y.coerceIn(-limit.y, limit.y))
}

/** The JPEG kept for an Aquarium: the crop on white, at most 1200 px and about 300 KB. */
suspend fun encodeAquariumPhoto(image: ImageBitmap, crop: SquareCrop): ByteArray = withContext(Dispatchers.Default) {
    val source = crop.sourceRect(image.width, image.height)
    val side = min(MAX_SIDE, source.width)
    val square = ImageBitmap(side, side)
    Canvas(square).apply {
        drawRect(Rect(0f, 0f, side.toFloat(), side.toFloat()), Paint().apply { color = Color.White })
        drawImageRect(
            image,
            srcOffset = source.topLeft,
            srcSize = source.size,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(side, side),
            paint = Paint().apply { filterQuality = FilterQuality.High },
        )
    }
    var bytes = encodeJpeg(square, 80)
    for (quality in listOf(70, 60)) if (bytes.size > MAX_BYTES) bytes = encodeJpeg(square, quality)
    bytes
}

expect fun encodeJpeg(image: ImageBitmap, quality: Int): ByteArray
