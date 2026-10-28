package com.maximerollin.aqualog

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@SdkSuppress(minSdkVersion = 33)
class LauncherIconTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun sceneAndMonochromeRenderUnderLauncherMasks() {
        val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val roundIcon = context.getDrawable(R.mipmap.ic_launcher_round)
        assertTrue(roundIcon is AdaptiveIconDrawable)
        val monochrome = requireNotNull(icon.monochrome)
        val size = 256
        val montage = Bitmap.createBitmap(size * 3, size * 2 + 64, Bitmap.Config.ARGB_8888)
        val output = Canvas(montage)
        output.drawColor(Color.rgb(18, 38, 34))
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 16f
        }
        listOf("Circle", "Squircle", "Rounded square").forEachIndexed { index, name ->
            listOf(false, true).forEach { themed ->
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val mask = mask(name, size.toFloat())
                canvas.clipPath(mask)
                if (themed) canvas.drawColor(Color.rgb(213, 235, 219))
                canvas.scale(size / 72f, size / 72f)
                canvas.translate(-18f, -18f)
                val layers = if (themed) listOf(monochrome) else listOf(icon.background, icon.foreground)
                layers.forEach { layer ->
                    layer.setBounds(0, 0, 108, 108)
                    if (themed) layer.setTint(Color.rgb(26, 70, 51))
                    layer.draw(canvas)
                }
                assertEquals("$name corners remain outside the mask", 0, Color.alpha(bitmap.getPixel(0, 0)))
                assertEquals("$name center is opaque", 255, Color.alpha(bitmap.getPixel(size / 2, size / 2)))
                val pixels = IntArray(size * size)
                bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
                if (!themed) {
                    assertTrue("$name retains the red plant", pixels.count {
                        Color.alpha(it) > 240 && Color.red(it) > 150 && Color.red(it) > Color.green(it) * 1.3
                    } > 100)
                    assertTrue("$name retains the ivory fish", pixels.count {
                        Color.alpha(it) > 240 && Color.red(it) > 230 && Color.green(it) > 225 && Color.blue(it) > 200
                    } > 1000)
                } else {
                    assertTrue("$name monochrome silhouette is visible", pixels.count {
                        it == Color.rgb(26, 70, 51)
                    } > 1000)
                }
                val row = if (themed) 1 else 0
                output.drawBitmap(bitmap, index * size.toFloat(), row * (size + 32f), null)
                output.drawText("$name / ${if (themed) "themed" else "color"}", index * size + 8f, row * (size + 32f) + size + 22f, label)
                bitmap.recycle()
            }
        }
        TestStorage().openOutputFile("launcher-icon/masks.png").use {
            assertTrue(montage.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        montage.recycle()
    }

    @Test
    fun installedLauncherAndNotificationResourcesRender() {
        val icon = requireNotNull(context.packageManager.getApplicationIcon(context.packageName))
        val bitmap = Bitmap.createBitmap(216, 216, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 216, 216)
        icon.draw(Canvas(bitmap))
        assertEquals(255, Color.alpha(bitmap.getPixel(108, 108)))
        val notification = requireNotNull(context.getDrawable(R.drawable.ic_notification))
        bitmap.eraseColor(Color.TRANSPARENT)
        notification.setBounds(0, 0, 216, 216)
        notification.draw(Canvas(bitmap))
        assertEquals(0, Color.alpha(bitmap.getPixel(108, 16)))
        assertEquals(255, Color.alpha(bitmap.getPixel(108, 108)))
        bitmap.recycle()
    }

    private fun mask(name: String, size: Float): Path = Path().apply {
        when (name) {
            "Circle" -> addCircle(size / 2, size / 2, size / 2, Path.Direction.CW)
            "Rounded square" -> addRoundRect(RectF(0f, 0f, size, size), size * .22f, size * .22f, Path.Direction.CW)
            else -> {
                moveTo(size / 2, 0f)
                cubicTo(size * .94f, 0f, size, size * .06f, size, size / 2)
                cubicTo(size, size * .94f, size * .94f, size, size / 2, size)
                cubicTo(size * .06f, size, 0f, size * .94f, 0f, size / 2)
                cubicTo(0f, size * .06f, size * .06f, 0f, size / 2, 0f)
                close()
            }
        }
    }
}
