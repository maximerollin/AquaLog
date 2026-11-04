package com.maximerollin.aqualog

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.photo.centredCrop
import com.maximerollin.aqualog.photo.encodeAquariumPhoto
import com.maximerollin.aqualog.photo.normalizePhoto
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoOrientationTest {
    @Test
    fun normalizePhoto_appliesExifRotation() = runBlocking {
        val bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val jpeg = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        val file = File.createTempFile("oriented", ".jpg", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
        try {
            file.writeBytes(jpeg)
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            val normalized = normalizePhoto(file.readBytes())!!
            assertEquals(20, normalized.width)
            assertEquals(40, normalized.height)
            val encoded = encodeAquariumPhoto(normalized, centredCrop(normalized.width, normalized.height))
            val decoded = BitmapFactory.decodeByteArray(encoded, 0, encoded.size)
            assertEquals(20, decoded.width)
            assertEquals(20, decoded.height)
        } finally {
            file.delete()
        }
    }
}
