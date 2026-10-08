package com.maximerollin.aqualog

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.shared.AuthenticationAttemptStorage
import com.maximerollin.aqualog.shared.AuthenticationResult
import com.maximerollin.aqualog.shared.PendingAuthenticationAttempt
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SupabaseAuthGatewayTest {
    @Test
    fun cancellationAndExpiration_allowFreshAuthenticationAttempts() = runTest {
        val launchedUris = mutableListOf<Uri>()
        val attempts = InMemoryAuthenticationAttemptStorage()
        val gateway = SupabaseAuthGateway(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            configuration = SupabaseConfiguration("https://example.supabase.co", "public-anon-key"),
            attemptStorage = attempts,
            openBrowser = { launchedUris += it },
        )

        assertEquals(AuthenticationResult.AwaitingCallback, gateway.signInWithGoogle())
        val cancelledAttempt = requireNotNull(attempts.readAttempt())
        assertEquals(
            AuthenticationResult.Cancelled,
            gateway.completeMagicLink(callback(cancelledAttempt, "error=access_denied")),
        )
        assertNull(attempts.readAttempt())

        assertEquals(AuthenticationResult.AwaitingCallback, gateway.signInWithGoogle())
        val expiredAttempt = requireNotNull(attempts.readAttempt())
        assertNotEquals(cancelledAttempt, expiredAttempt)
        assertEquals(
            AuthenticationResult.ExpiredLink,
            gateway.completeMagicLink(callback(expiredAttempt, "error_code=otp_expired")),
        )
        assertNull(attempts.readAttempt())

        assertEquals(AuthenticationResult.AwaitingCallback, gateway.signInWithGoogle())
        val retryAttempt = requireNotNull(attempts.readAttempt())
        assertNotEquals(expiredAttempt, retryAttempt)
        assertEquals(3, launchedUris.size)
        launchedUris.forEach { uri ->
            assertNotNull(uri.getQueryParameter("code_challenge"))
            assertEquals("s256", uri.getQueryParameter("code_challenge_method"))
        }
    }

    private fun callback(attempt: PendingAuthenticationAttempt, result: String) =
        "aqualog://auth/callback?state=${attempt.state}&$result"

    private class InMemoryAuthenticationAttemptStorage : AuthenticationAttemptStorage {
        private var attempt: PendingAuthenticationAttempt? = null

        override suspend fun saveAttempt(attempt: PendingAuthenticationAttempt) {
            this.attempt = attempt
        }

        override suspend fun readAttempt(): PendingAuthenticationAttempt? = attempt

        override suspend fun clearAttempt() {
            attempt = null
        }
    }
}
