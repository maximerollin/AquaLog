package com.maximerollin.aqualog.photo

import androidx.compose.ui.geometry.Offset
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class PhotoPipelineTest {
    private fun encode(image: BufferedImage, format: String): ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray()

    /** A photo-like fixture: diagonal gradient, a few shapes and low-amplitude sensor noise. */
    private fun noisyJpeg(width: Int, height: Int): ByteArray {
        val random = Random(42)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        fun channel(value: Float) = (value + random.nextInt(-4, 5)).toInt().coerceIn(0, 255)
        for (y in 0 until height) for (x in 0 until width) {
            val t = (x + y) / (width + height).toFloat()
            image.setRGB(x, y, (channel(40 + 160 * t) shl 16) or (channel(120 + 80 * (1 - t)) shl 8) or channel(200 - 120 * t))
        }
        image.createGraphics().apply {
            color = java.awt.Color(230, 190, 40)
            fillOval(width / 4, height / 4, width / 3, height / 2)
            color = java.awt.Color(30, 60, 90)
            fillRect(width / 2, height / 3, width / 3, height / 3)
            dispose()
        }
        return encode(image, "jpg")
    }

    private fun process(source: ByteArray): ByteArray = runBlocking {
        val normalized = assertNotNull(normalizePhoto(source))
        encodeAquariumPhoto(normalized, centredCrop(normalized.width, normalized.height))
    }

    @Test
    fun `centredCrop of a landscape image`() {
        assertEquals(SquareCrop(.25f, 0f, 1f), centredCrop(400, 200))
    }

    @Test
    fun `cropFor clamps the pan`() {
        val pannedFar = cropFor(400, 200, viewport = 100f, scale = 1f, offset = Offset(-100_000f, 100_000f))
        assertEquals(1f, pannedFar.side)
        assertTrue(pannedFar.left + pannedFar.side * 200 / 400 <= 1f + 1e-6f)
        assertEquals(.5f, pannedFar.left, 1e-6f)
        val zoomed = cropFor(400, 200, viewport = 100f, scale = 2f, offset = Offset(100_000f, 100_000f))
        assertEquals(.5f, zoomed.side)
        assertEquals(0f, zoomed.left)
        assertEquals(0f, zoomed.top)
        val zoomedFar = cropFor(400, 200, viewport = 100f, scale = 2f, offset = Offset(-100_000f, -100_000f))
        assertEquals(1f - .5f * 200 / 400, zoomedFar.left, 1e-6f)
        assertEquals(.5f, zoomedFar.top, 1e-6f)
    }

    @Test
    fun `a large photo becomes 1200 px and under 300 KB`() {
        val result = process(noisyJpeg(3000, 2000))
        val decoded = ImageIO.read(ByteArrayInputStream(result))
        assertEquals(1200, decoded.width)
        assertEquals(1200, decoded.height)
        assertTrue(result.size <= 300_000, "was ${result.size} bytes")
    }

    @Test
    fun `a small image is not upscaled`() {
        val decoded = ImageIO.read(ByteArrayInputStream(process(noisyJpeg(300, 300))))
        assertEquals(300, decoded.width)
        assertEquals(300, decoded.height)
    }

    @Test
    fun `transparent pixels become white`() {
        val transparent = encode(BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB), "png")
        val decoded = ImageIO.read(ByteArrayInputStream(process(transparent)))
        val centre = decoded.getRGB(32, 32)
        listOf(16, 8, 0).forEach { shift -> assertTrue(255 - ((centre shr shift) and 0xFF) <= 8, "channel at $shift") }
    }

    @Test
    fun `a crop that is not centred keeps its own pixels`() {
        val halves = BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB).apply {
            for (y in 0 until 200) for (x in 0 until 400) setRGB(x, y, if (x < 200) 0xFF0000 else 0x0000FF)
        }
        val image = assertNotNull(runBlocking { normalizePhoto(encode(halves, "png")) })
        val decoded = ImageIO.read(ByteArrayInputStream(runBlocking { encodeAquariumPhoto(image, SquareCrop(0f, 0f, 1f)) }))
        val centre = decoded.getRGB(100, 100)
        assertTrue((centre shr 16) and 0xFF > 200 && centre and 0xFF < 60, "expected red, was ${centre.toString(16)}")
    }

    @Test
    fun `zoom keeps the framed centre fixed`() {
        val (w, h, viewport) = Triple(4000, 3000, 300f)
        fun centre(crop: SquareCrop) = crop.left * w + crop.side * minOf(w, h) / 2 to crop.top * h + crop.side * minOf(w, h) / 2
        val (scale, offset) = applyGesture(w, h, viewport, 1f, Offset.Zero, 1f, Offset(-120f, 40f))
        val before = centre(cropFor(w, h, viewport, scale, offset))
        val (zoomed, zoomedOffset) = applyGesture(w, h, viewport, scale, offset, 2f, Offset.Zero)
        val after = centre(cropFor(w, h, viewport, zoomed, zoomedOffset))
        assertEquals(2f, zoomed)
        assertEquals(before.first, after.first, 1f)
        assertEquals(before.second, after.second, 1f)
    }

    @Test
    fun `undecodable bytes give no image`() {
        assertEquals(null, runBlocking { normalizePhoto(byteArrayOf(1, 2, 3)) })
    }
}
