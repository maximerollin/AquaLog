package com.maximerollin.aqualog

import kotlin.test.Test
import kotlin.test.assertEquals

class EnvironmentIsolationTest {
    @Test
    fun `all builds use the registered Play package and shared callback`() {
        assertEquals("com.maximerollin.aqualog", BuildConfig.APPLICATION_ID)
        assertEquals("aqualog://auth/callback", SupabaseConfiguration.fromBuildConfig().redirectUrl)
    }
}
