package com.maximerollin.aqualog.widget

import kotlin.test.Test
import kotlin.test.assertEquals

class TankPhotoSampleSizeTest {
    @Test
    fun `the shorter side lands at about 96 px, never below`() {
        assertEquals(8, tankPhotoSampleSize(1200, 1600)) // 150 px
        assertEquals(8, tankPhotoSampleSize(1600, 1200))
        assertEquals(4, tankPhotoSampleSize(500, 900)) // 125 px; 8 would give 62
        assertEquals(1, tankPhotoSampleSize(100, 300))
        assertEquals(1, tankPhotoSampleSize(0, 0))
    }
}
