package com.maximerollin.aqualog

import com.maximerollin.aqualog.shared.AccountDeletionPhase
import com.maximerollin.aqualog.shared.PendingAccountDeletion
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
}
