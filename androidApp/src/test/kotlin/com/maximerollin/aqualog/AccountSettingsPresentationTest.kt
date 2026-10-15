package com.maximerollin.aqualog

import com.maximerollin.aqualog.shared.AccountDeletionPhase
import com.maximerollin.aqualog.shared.PendingAccountDeletion
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountSettingsPresentationTest {
    @Test
    fun `cloud pending deletion needing authentication exposes reauthentication action`() {
        val state = AccountSettingsUiState(
            pendingDeletion = PendingAccountDeletion("operation-1", AccountDeletionPhase.CLOUD_PENDING),
            requiresDeletionReauthentication = true,
        )

        assertTrue(state.showDeletionReauthenticationAction)
        assertFalse(
            state.copy(
                pendingDeletion = PendingAccountDeletion("operation-1", AccountDeletionPhase.CLOUD_DELETED),
            ).showDeletionReauthenticationAction,
        )
    }

    @Test
    fun `cold start callback routing reads persisted pending deletion instead of default UI state`() = runTest {
        val persisted = PendingAccountDeletion("operation-1", AccountDeletionPhase.CLOUD_PENDING)

        assertEquals(
            AuthenticationCallbackTarget.ACCOUNT_DELETION,
            resolveAuthenticationCallbackTarget { persisted },
        )
        assertEquals(
            AuthenticationCallbackTarget.ACCOUNT_ACTIVATION,
            resolveAuthenticationCallbackTarget { null },
        )
    }
}
