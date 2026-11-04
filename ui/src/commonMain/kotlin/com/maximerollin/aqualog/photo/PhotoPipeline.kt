package com.maximerollin.aqualog.photo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntOffset
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
 * PNG, not JPEG, so transparent pixels reach [encodeAquariumPhoto] and turn white instead of black.
 */
@OptIn(ExperimentalResourceApi::class)
suspend fun normalizePhoto(source: ByteArray): ImageBitmap? = try {
    FileKit.compressImage(bytes = source, imageFormat = ImageFormat.PNG, quality = 100, maxWidth = 2400, maxHeight = 2400)
        .decodeToImageBitmap()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}

/** The JPEG kept for an Aquarium: the crop on white, at most 1200 px and about 300 KB. */
suspend fun encodeAquariumPhoto(image: ImageBitmap, crop: SquareCrop): ByteArray = withContext(Dispatchers.Default) {
    val short = min(image.width, image.height)
    val sidePixels = (crop.side * short).roundToInt().coerceIn(1, short)
    val left = (crop.left * image.width).roundToInt().coerceIn(0, image.width - sidePixels)
    val top = (crop.top * image.height).roundToInt().coerceIn(0, image.height - sidePixels)
    val side = min(MAX_SIDE, sidePixels)
    val square = ImageBitmap(side, side)
    Canvas(square).apply {
        drawRect(Rect(0f, 0f, side.toFloat(), side.toFloat()), Paint().apply { color = Color.White })
        drawImageRect(
            image,
            srcOffset = IntOffset(left, top),
            srcSize = IntSize(sidePixels, sidePixels),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(side, side),
            paint = Paint().apply { filterQuality = FilterQuality.High },
        )
    }
    var bytes = encodeJpeg(square, 80)
    for (quality in listOf(70, 60, 50, 40)) if (bytes.size > MAX_BYTES) bytes = encodeJpeg(square, quality)
    bytes
}

expect fun encodeJpeg(image: ImageBitmap, quality: Int): ByteArray
