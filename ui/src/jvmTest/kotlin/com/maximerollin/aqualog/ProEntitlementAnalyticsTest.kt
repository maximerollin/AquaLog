package com.maximerollin.aqualog

import com.maximerollin.aqualog.shared.ANNUAL_PRO_PRODUCT_ID
import com.maximerollin.aqualog.shared.LIFETIME_PRO_PRODUCT_ID
import com.maximerollin.aqualog.shared.ProEntitlement
import com.maximerollin.aqualog.shared.ProProduct
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProEntitlementAnalyticsTest {
    @Test
    fun `annual entitlement maps to annual analytics product`() {
        assertEquals(
            ProProduct.ANNUAL,
            ProEntitlement(ANNUAL_PRO_PRODUCT_ID, 1_800_000_000_000L).analyticsProductOrNull(),
        )
    }

    @Test
    fun `lifetime entitlement maps to lifetime analytics product`() {
        assertEquals(
            ProProduct.LIFETIME,
            ProEntitlement(LIFETIME_PRO_PRODUCT_ID, null).analyticsProductOrNull(),
        )
    }

    @Test
    fun `unknown entitlement never falls back to annual analytics`() {
        assertNull(ProEntitlement("unexpected-product", null).analyticsProductOrNull())
    }
}
