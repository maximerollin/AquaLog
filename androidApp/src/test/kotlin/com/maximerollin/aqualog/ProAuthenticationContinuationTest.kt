package com.maximerollin.aqualog

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ProAuthenticationContinuationTest {
    @Test
    fun `lifetime choice resumes only the lifetime purchase after authentication`() = runTest {
        val resumed = mutableListOf<String>()

        resumeProAuthenticationContinuation(
            continuation = ProAuthenticationContinuation.START_LIFETIME_PURCHASE,
            startAnnualPurchase = { resumed += "annual" },
            startLifetimePurchase = { resumed += "lifetime" },
            restorePurchase = { resumed += "restore" },
        )

        assertEquals(listOf("lifetime"), resumed)
    }
}
