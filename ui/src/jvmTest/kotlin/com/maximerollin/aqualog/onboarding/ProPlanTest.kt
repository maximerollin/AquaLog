package com.maximerollin.aqualog.onboarding

import com.maximerollin.aqualog.ProPurchaseStatus
import com.maximerollin.aqualog.ProUiState
import com.maximerollin.aqualog.shared.AnnualProOffer
import com.maximerollin.aqualog.shared.LifetimeProOffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProPlanTest {
    private val annual = AnnualProOffer("annual", "14,99 €", 14, 0L)
    private val lifetime = LifetimeProOffer("lifetime", "34,99 €")

    @Test
    fun `annual comes first when both exist`() {
        val pro = ProUiState(offer = annual, lifetimeOffer = lifetime)
        assertEquals(listOf(ProPlan.ANNUAL, ProPlan.LIFETIME), availablePlans(pro))
        assertEquals(ProPlan.ANNUAL, initialPlan(pro))
    }

    @Test
    fun `only lifetime selects lifetime`() {
        assertEquals(ProPlan.LIFETIME, initialPlan(ProUiState(lifetimeOffer = lifetime)))
    }

    @Test
    fun `no offer has no plan`() {
        assertNull(initialPlan(ProUiState()))
        assertTrue(availablePlans(ProUiState()).isEmpty())
    }

    @Test
    fun `busy statuses`() {
        val busy = setOf(
            ProPurchaseStatus.AWAITING_PLAY_CONFIRMATION, ProPurchaseStatus.PENDING,
            ProPurchaseStatus.VERIFYING, ProPurchaseStatus.ACTIVE,
        )
        ProPurchaseStatus.entries.forEach { status ->
            val result = ProUiState(purchaseStatus = status).purchaseBusy()
            if (status in busy) assertTrue(result, "$status") else assertFalse(result, "$status")
        }
    }
}
