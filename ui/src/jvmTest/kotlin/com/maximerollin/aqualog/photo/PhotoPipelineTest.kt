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

    private fun noisyJpeg(width: Int, height: Int): ByteArray {
        val random = Random(42)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) for (x in 0 until width) image.setRGB(x, y, random.nextInt(0x1000000))
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
    fun `undecodable bytes give no image`() {
        assertEquals(null, runBlocking { normalizePhoto(byteArrayOf(1, 2, 3)) })
    }
}
