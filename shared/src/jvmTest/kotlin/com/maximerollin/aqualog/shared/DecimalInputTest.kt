package com.maximerollin.aqualog.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DecimalInputTest {
    @Test fun `decimal input accepts point and comma independently from interface locale`() {
        assertEquals(7.25, parseDecimalInput(" 7.25 "))
        assertEquals(7.25, parseDecimalInput("7,25"))
        assertNull(parseDecimalInput("7,2,5"))
    }
}
