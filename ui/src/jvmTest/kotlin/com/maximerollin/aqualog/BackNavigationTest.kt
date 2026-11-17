package com.maximerollin.aqualog

import com.maximerollin.aqualog.habitants.AddInhabitantUiState
import com.maximerollin.aqualog.shared.AccountDeletionPhase
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.PendingAccountDeletion
import com.maximerollin.aqualog.shared.RecordedSession
import com.maximerollin.aqualog.shared.Session
import com.maximerollin.aqualog.shared.VolumeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackNavigationTest {
    private val setup = AquariumSetup(Aquarium("a1", "Salon", 100.0, VolumeUnit.LITERS, 0L), emptyList())
    private val shell = HomeUiState(isLoading = false, setup = setup)
    private val onboarding = HomeUiState(isLoading = false)
    private val session = RapidSessionUiState(idempotencyKey = "k1", aquariumId = "a1", occurredAtEpochMillis = 0L)
    private val recorded = RecordedSession(Session("s1", "a1", 0L, 0L), emptyList(), emptyList(), emptyList())

    @Test
    fun `the paywall over the shell closes`() {
        assertEquals(BackStep.ClosePaywall, backStep(shell.copy(pro = ProUiState(showPaywall = true))))
        assertEquals(
            BackStep.ClosePaywall,
            backStep(shell.copy(destination = MainDestination.TRENDS, pro = ProUiState(showPaywall = true))),
        )
    }

    @Test
    fun `a tab other than Bac goes back to Bac`() {
        assertEquals(BackStep.ToTank, backStep(shell.copy(destination = MainDestination.HISTORY)))
        assertEquals(BackStep.ToTank, backStep(shell.copy(destination = MainDestination.TRENDS)))
        assertEquals(BackStep.ToTank, backStep(shell.copy(destination = MainDestination.MORE)))
    }

    @Test
    fun `Bac leaves Back to the system`() {
        assertNull(backStep(shell.copy(destination = MainDestination.TANK)))
    }

    @Test
    fun `an open Session closes`() {
        assertEquals(BackStep.CloseSession, backStep(shell.copy(rapidSession = session)))
        // The Session opened from a fiche is drawn over « Ajouter un habitant »: Back closes the Session first.
        assertEquals(
            BackStep.CloseSession,
            backStep(shell.copy(rapidSession = session, addInhabitant = AddInhabitantUiState("a1"))),
        )
    }

    @Test
    fun `the session details close`() {
        assertEquals(BackStep.CloseSessionDetails, backStep(shell.copy(sessionDetail = recorded)))
        assertEquals(
            BackStep.CloseSessionDetails,
            backStep(shell.copy(destination = MainDestination.HISTORY, sessionDetail = recorded, sessionDetailReturnsToTimeline = true)),
        )
    }

    @Test
    fun `the tasks close`() {
        assertEquals(BackStep.CloseTasks, backStep(shell.copy(tasks = TaskUiState(isOpen = true))))
    }

    @Test
    fun `the rhythm settings close`() {
        assertEquals(BackStep.CloseRhythm, backStep(shell.copy(showRhythmSettings = true)))
    }

    @Test
    fun `adding an inhabitant steps back`() {
        assertEquals(BackStep.BackInAddInhabitant, backStep(shell.copy(addInhabitant = AddInhabitantUiState("a1"))))
        assertEquals(
            BackStep.BackInAddInhabitant,
            backStep(shell.copy(addInhabitant = AddInhabitantUiState("a1", ficheSpeciesId = "neon"))),
        )
    }

    @Test
    fun `the account flow follows its own back control`() {
        val closing = listOf(
            AccountStep.INVITATION,
            AccountStep.METHODS,
            AccountStep.MAGIC_SENT,
            AccountStep.WAITING_BROWSER,
            AccountStep.ERROR,
        )
        closing.forEach { step ->
            assertEquals(BackStep.CloseAccount, backStep(shell.copy(account = AccountUiState(step = step))), "$step")
        }
        assertEquals(BackStep.ToAccountMethods, backStep(shell.copy(account = AccountUiState(step = AccountStep.MAGIC_EMAIL))))
        assertNull(backStep(shell.copy(account = AccountUiState(step = AccountStep.WORKING))))
    }

    @Test
    fun `the onboarding WELCOME step leaves Back to the system`() {
        assertNull(backStep(onboarding.copy(step = OnboardingStep.WELCOME)))
    }

    @Test
    fun `no onboarding step has a back control`() {
        OnboardingStep.entries.forEach { step ->
            assertNull(backStep(onboarding.copy(step = step)), "$step")
        }
    }

    @Test
    fun `screens without a close control leave Back to the system`() {
        assertNull(backStep(HomeUiState()))
        assertNull(
            backStep(
                shell.copy(
                    destination = MainDestination.HISTORY,
                    accountSettings = AccountSettingsUiState(
                        pendingDeletion = PendingAccountDeletion("op", AccountDeletionPhase.CLOUD_DELETED),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `the screens follow the order AquaLogContent draws them in`() {
        // The account screen is drawn over the tasks, the tasks over the Session, the details over the paywall.
        assertEquals(
            BackStep.CloseAccount,
            backStep(shell.copy(account = AccountUiState(step = AccountStep.INVITATION), tasks = TaskUiState(isOpen = true))),
        )
        assertEquals(BackStep.CloseTasks, backStep(shell.copy(tasks = TaskUiState(isOpen = true), rapidSession = session)))
        assertEquals(
            BackStep.CloseSessionDetails,
            backStep(shell.copy(sessionDetail = recorded, pro = ProUiState(showPaywall = true))),
        )
        // Without an Aquarium, the screens that need one are not drawn.
        assertNull(backStep(onboarding.copy(tasks = TaskUiState(isOpen = true), showRhythmSettings = true)))
    }
}
