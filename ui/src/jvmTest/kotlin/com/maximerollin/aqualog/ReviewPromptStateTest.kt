package com.maximerollin.aqualog

import androidx.lifecycle.viewModelScope
import com.maximerollin.aqualog.shared.AccountActivationCoordinator
import com.maximerollin.aqualog.shared.AuthGateway
import com.maximerollin.aqualog.shared.AuthTokens
import com.maximerollin.aqualog.shared.AuthenticationResult
import com.maximerollin.aqualog.shared.AquariumCreationAccess
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.CloudRepository
import com.maximerollin.aqualog.shared.InAppReviewGateway
import com.maximerollin.aqualog.shared.InitialAccountCopy
import com.maximerollin.aqualog.shared.MagicLinkRequestResult
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.ReviewCoordinator
import com.maximerollin.aqualog.shared.ReviewEligibilityPolicy
import com.maximerollin.aqualog.shared.ReviewHistory
import com.maximerollin.aqualog.shared.ReviewHistoryStore
import com.maximerollin.aqualog.shared.RoomAquariumRepository
import com.maximerollin.aqualog.shared.SecureTokenStorage
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.createAquaLogDatabase
import com.maximerollin.aqualog.shared.createJvmDatabaseBuilder
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewPromptStateTest {
    @BeforeTest
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private class Fixture(history: ReviewHistory, withAccountInvitation: Boolean = false) {
        var stored = history
        var requests = 0
        private val directory = Files.createTempDirectory("aqualog-review-vm")
        private val database = createAquaLogDatabase(createJvmDatabaseBuilder(directory.resolve("aqualog.db").toString()))
        val repository = RoomAquariumRepository(database, { UUID.randomUUID().toString() }, { 1_700_000_000_000L })
        private val reviewCoordinator = ReviewCoordinator(
            policy = ReviewEligibilityPolicy(),
            store = object : ReviewHistoryStore {
                override fun load() = stored
                override fun save(updated: ReviewHistory) { stored = updated }
            },
            gateway = InAppReviewGateway { requests += 1 },
            currentTimeMillis = { 1_700_000_000_000L },
        )
        private val accountCoordinator = AccountActivationCoordinator(
            authGateway = object : AuthGateway {
                override suspend fun signInWithGoogle() = AuthenticationResult.Cancelled
                override suspend fun requestMagicLink(email: String) = MagicLinkRequestResult.Sent
                override suspend fun completeMagicLink(callbackUrl: String) = AuthenticationResult.Cancelled
                override suspend fun refreshSession(refreshToken: String) = AuthenticationResult.Cancelled
            },
            cloudRepository = object : CloudRepository {
                override suspend fun upsertInitialCopy(accountId: String, copy: InitialAccountCopy, accessToken: String) = Unit
            },
            localData = repository,
            tokenStorage = object : SecureTokenStorage {
                override suspend fun save(tokens: AuthTokens) = Unit
                override suspend fun read(): AuthTokens? = null
                override suspend fun clear() = Unit
            },
        )
        lateinit var viewModel: HomeViewModel
        val state get() = viewModel.uiState.value
        private val invite = withAccountInvitation

        suspend fun start() {
            val setup = repository.createConfiguredAquarium(
                "Salon", 120.0, VolumeUnit.LITERS, AquariumProfile.ESTABLISHED,
                OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED), AquariumCreationAccess.PRO,
            )
            viewModel = HomeViewModel(
                repository,
                accountCoordinator = accountCoordinator.takeIf { invite },
                reviewCoordinator = reviewCoordinator,
            )
            viewModel.uiState.first { it.setup?.aquarium?.id == setup.aquarium.id && it.aquariums.isNotEmpty() }
        }

        /** Real time: the view model's work runs on Room's own dispatchers. */
        private suspend fun settle(timeoutMillis: Long = 5_000, condition: () -> Boolean) =
            withContext(Dispatchers.Default) {
                withTimeout(timeoutMillis) { while (!condition()) delay(10) }
                // What follows the awaited change in the same block (asking for the review) has run by now.
                delay(150)
            }

        suspend fun saveNewSession(type: MaintenanceActionType = MaintenanceActionType.WATER_CHANGE) {
            val before = stored.newSessionCount
            viewModel.openRapidSession()
            settle { state.rapidSession?.context != null }
            viewModel.toggleMaintenanceAction(type)
            viewModel.saveRapidSession()
            settle { stored.newSessionCount > before }
        }

        suspend fun editLatestSession() {
            viewModel.openSessionDetails()
            viewModel.editSession()
            settle { state.rapidSession?.let { it.editingSessionId != null && it.context != null } == true }
            viewModel.toggleMaintenanceAction(MaintenanceActionType.FILTER_MAINTENANCE)
            viewModel.saveRapidSession()
            settle { state.rapidSession == null }
        }

        suspend fun continueAfterSession() {
            viewModel.continueAfterSessionConfirmation()
            settle { state.account.step != AccountStep.HIDDEN }
        }

        suspend fun close() {
            viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
            database.close()
        }
    }

    @Test
    fun `a calm new Session that makes the prompt DISCOVERY shows the dialog`() = runTest {
        val fixture = Fixture(ReviewHistory(4, false, null, false))
        fixture.start()

        fixture.saveNewSession()

        assertEquals(5, fixture.stored.newSessionCount)
        assertTrue(fixture.state.reviewDiscoveryVisible)
        assertEquals(0, fixture.requests)
        fixture.close()
    }

    @Test
    fun `the dialog waits while the account invitation is open`() = runTest {
        val fixture = Fixture(ReviewHistory(4, false, null, false), withAccountInvitation = true)
        fixture.start()

        fixture.saveNewSession()
        assertEquals(AccountInvitationPrompt.AFTER_SESSION_CONFIRMATION, fixture.state.accountInvitationPrompt)
        assertFalse(fixture.state.reviewDiscoveryVisible)

        fixture.continueAfterSession()
        assertTrue(fixture.state.reviewDiscoveryVisible)
        fixture.close()
    }

    @Test
    fun `answering the dialog hides it, and Later asks nothing of Google`() = runTest {
        val fixture = Fixture(ReviewHistory(4, false, null, false))
        fixture.start()
        fixture.saveNewSession()

        fixture.viewModel.answerReviewDiscovery(leaveReview = false)

        assertFalse(fixture.state.reviewDiscoveryVisible)
        assertEquals(0, fixture.requests)
        assertTrue(fixture.stored.discoverySeen)
        fixture.close()
    }

    @Test
    fun `leaving a review asks Google`() = runTest {
        val fixture = Fixture(ReviewHistory(4, false, null, false))
        fixture.start()
        fixture.saveNewSession()

        fixture.viewModel.answerReviewDiscovery(leaveReview = true)

        assertFalse(fixture.state.reviewDiscoveryVisible)
        assertEquals(1, fixture.requests)
        fixture.close()
    }

    @Test
    fun `a DIRECT request calls the gateway and shows nothing`() = runTest {
        val fixture = Fixture(ReviewHistory(4, false, null, true))
        fixture.start()

        fixture.saveNewSession()

        assertEquals(1, fixture.requests)
        assertFalse(fixture.state.reviewDiscoveryVisible)
        fixture.close()
    }

    @Test
    fun `editing a Session never counts`() = runTest {
        val fixture = Fixture(ReviewHistory(3, false, null, false))
        fixture.start()
        fixture.saveNewSession()
        assertEquals(4, fixture.stored.newSessionCount)

        fixture.editLatestSession()

        assertEquals(4, fixture.stored.newSessionCount)
        assertFalse(fixture.state.reviewDiscoveryVisible)
        assertEquals(0, fixture.requests)
        fixture.close()
    }
}
