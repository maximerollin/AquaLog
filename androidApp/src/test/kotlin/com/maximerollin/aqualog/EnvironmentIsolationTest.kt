package com.maximerollin.aqualog

import kotlin.test.Test
import kotlin.test.assertEquals

class EnvironmentIsolationTest {
    @Test
    fun `installation identity and callback belong to its environment`() {
        val expected = when (BuildConfig.BUILD_TYPE) {
            "debug" -> "com.maximerollin.aqualog.dev" to "aqualog-dev://auth/callback"
            "beta" -> "com.maximerollin.aqualog.beta" to "aqualog-beta://auth/callback"
            "release" -> "com.maximerollin.aqualog" to "aqualog://auth/callback"
            else -> error("Unqualified build type")
        }
        assertEquals(expected.first, BuildConfig.APPLICATION_ID)
        assertEquals(expected.second, SupabaseConfiguration.fromBuildConfig().redirectUrl)
    }
}
