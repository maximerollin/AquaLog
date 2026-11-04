package com.maximerollin.aqualog.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.TestAquaLogApplication
import com.maximerollin.aqualog.shared.ANNUAL_PRO_PRODUCT_ID
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.LIFETIME_PRO_PRODUCT_ID
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.ProEntitlement
import com.maximerollin.aqualog.shared.VolumeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals

/** The widget decides from the cached entitlement, never a live check (Review Focus 4 and 5). */
@RunWith(AndroidJUnit4::class)
class WidgetContentGatingTest {
    private val application get() =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TestAquaLogApplication
    private val now = 1_793_000_000_000L

    @Before
    fun reset() {
        application.resetRepository()
        application.clockMillis = now
        application.proEntitlementCache.write(null)
    }

    // The cache is SharedPreferences, which outlive the test process.
    @After
    fun clearCache() = application.proEntitlementCache.write(null)

    @Test
    fun noAquarium_isTheInvitationToOpenTheApp_evenWhenNotPro() = runBlocking {
        assertEquals(WidgetContent.NoAquarium, tankWidgetContent(application, null))
        application.proEntitlementCache.write(ProEntitlement(LIFETIME_PRO_PRODUCT_ID, null))
        assertEquals(WidgetContent.NoAquarium, tankWidgetContent(application, null))
    }

    @Test
    fun anAquarium_withNoCachedEntitlement_needsPro() = runBlocking {
        createAquarium()
        assertEquals(WidgetContent.NeedsPro, tankWidgetContent(application, null))
    }

    @Test
    fun anAquarium_withAnExpiredAnnualEntitlement_needsPro() = runBlocking {
        createAquarium()
        application.proEntitlementCache.write(ProEntitlement(ANNUAL_PRO_PRODUCT_ID, validUntilEpochMillis = now - 1))
        assertEquals(WidgetContent.NeedsPro, tankWidgetContent(application, null))
    }

    @Test
    fun anAquarium_withAnActiveEntitlement_showsTheTank() = runBlocking {
        createAquarium()
        application.proEntitlementCache.write(ProEntitlement(ANNUAL_PRO_PRODUCT_ID, validUntilEpochMillis = now + 86_400_000))
        assertEquals("Salon", (tankWidgetContent(application, null) as WidgetContent.Tank).text.name)
    }

    @Test
    fun tanks_followTheSameGating() = runBlocking {
        assertEquals(WidgetContent.NoAquarium, tanksWidgetContent(application))
        createAquarium()
        assertEquals(WidgetContent.NeedsPro, tanksWidgetContent(application))
        application.proEntitlementCache.write(ProEntitlement(LIFETIME_PRO_PRODUCT_ID, null))
        assertEquals(listOf("Salon"), (tanksWidgetContent(application) as WidgetContent.Tanks).rows.map { it.name })
    }

    private suspend fun createAquarium() {
        val repository = application.aquariumRepository
        val created = repository.createConfiguredAquarium(
            name = "Salon", volume = 120.0, volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.ESTABLISHED, parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
        )
        repository.selectAquarium(created.aquarium.id)
    }
}
