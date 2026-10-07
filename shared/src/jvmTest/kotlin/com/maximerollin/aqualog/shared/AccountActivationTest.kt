package com.maximerollin.aqualog.shared

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AccountActivationTest {
    @Test
    fun `authentication callback is accepted only for the pending PKCE attempt`() {
        val pending = PendingAuthenticationAttempt(
            state = "expected-state",
            codeVerifier = "local-code-verifier",
        )

        assertIs<AuthenticationCallbackResult.Rejected>(
            correlateAuthenticationCallback(
                pendingAttempt = null,
                callback = AuthenticationCallback(state = "expected-state", authorizationCode = "injected-code"),
            ),
        )
        assertIs<AuthenticationCallbackResult.Rejected>(
            correlateAuthenticationCallback(
                pendingAttempt = pending,
                callback = AuthenticationCallback(state = "attacker-state", authorizationCode = "injected-code"),
            ),
        )
        assertIs<AuthenticationCallbackResult.Rejected>(
            correlateAuthenticationCallback(
                pendingAttempt = pending,
                callback = AuthenticationCallback(
                    state = "expected-state",
                    accessToken = "implicit-token-must-not-be-accepted",
                ),
            ),
        )

        val accepted = assertIs<AuthenticationCallbackResult.AuthorizationCode>(
            correlateAuthenticationCallback(
                pendingAttempt = pending,
                callback = AuthenticationCallback(state = "expected-state", authorizationCode = "one-time-code"),
            ),
        )
        assertEquals("one-time-code", accepted.code)
        assertEquals("local-code-verifier", accepted.codeVerifier)
    }

    @Test
    fun `unexpected authentication callback is ignored without touching local data`() = runTest {
        val fixture = populatedAccountFixture()
        val coordinator = AccountActivationCoordinator(
            authGateway = FakeAuthGateway(AuthenticationResult.IgnoredCallback),
            cloudRepository = RecordingCloudRepository(),
            localData = fixture.repository,
            tokenStorage = RecordingTokenStorage(),
        )

        assertEquals(
            AccountActivationResult.IgnoredCallback,
            coordinator.completeMagicLink("aqualog://auth/callback?code=injected"),
        )
        assertNull(fixture.repository.accountMigrationState())
        assertEquals(fixture.sessionId, fixture.repository.observeLatestSession(fixture.aquariumId).first()?.session?.id)
        fixture.close()
    }

    @Test
    fun `account invitation becomes available only after the first local Session`() = runTest {
        val fixture = accountFixture()
        val setup = fixture.repository.createConfiguredAquarium(
            name = "Amazonien",
            volume = 120.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.ESTABLISHED,
            parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
        )
        val coordinator = AccountActivationCoordinator(
            authGateway = FakeAuthGateway(AuthenticationResult.Cancelled),
            cloudRepository = RecordingCloudRepository(),
            localData = fixture.repository,
            tokenStorage = RecordingTokenStorage(),
        )

        assertEquals(false, coordinator.shouldInviteToAccount())
        fixture.repository.saveRapidSession(
            RapidSessionInput(
                aquariumId = setup.aquarium.id,
                occurredAtEpochMillis = 1_700_000_100_000L,
                idempotencyKey = "first-session",
                maintenanceActions = listOf(MaintenanceActionInput(MaintenanceActionType.WATER_CHANGE)),
            ),
        )

        assertEquals(true, coordinator.shouldInviteToAccount())
        val databasePath = fixture.databasePath
        fixture.close()

        val reopenedFixture = accountFixture(databasePath)
        val coordinatorAfterRestart = AccountActivationCoordinator(
            authGateway = FakeAuthGateway(AuthenticationResult.Cancelled),
            cloudRepository = RecordingCloudRepository(),
            localData = reopenedFixture.repository,
            tokenStorage = RecordingTokenStorage(),
        )
        assertEquals(true, coordinatorAfterRestart.shouldInviteToAccount())
        coordinatorAfterRestart.markInvitationOffered()
        reopenedFixture.close()

        val offeredFixture = accountFixture(databasePath)
        val coordinatorAfterOffer = AccountActivationCoordinator(
            authGateway = FakeAuthGateway(AuthenticationResult.Cancelled),
            cloudRepository = RecordingCloudRepository(),
            localData = offeredFixture.repository,
            tokenStorage = RecordingTokenStorage(),
        )
        assertEquals(false, coordinatorAfterOffer.shouldInviteToAccount())
        offeredFixture.close()
    }

    @Test
    fun `successful Google account activation uploads the local copy with unchanged UUIDs`() = runTest {
        val fixture = accountFixture()
        val setup = fixture.repository.createConfiguredAquarium(
            name = "Amazonien",
            volume = 120.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.ESTABLISHED,
            parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
        )
        val parameter = setup.parameters.first(ParameterDefinition::isActive)
        val recorded = fixture.repository.saveRapidSession(
            RapidSessionInput(
                aquariumId = setup.aquarium.id,
                occurredAtEpochMillis = 1_700_000_100_000L,
                idempotencyKey = "first-session",
                measurementInputs = mapOf(parameter.id to "24.5"),
            ),
        )
        val cloud = RecordingCloudRepository()
        val coordinator = AccountActivationCoordinator(
            authGateway = FakeAuthGateway(
                googleResult = AuthenticationResult.Authenticated(
                    AuthenticatedAccount("account-1", "access-token", "refresh-token"),
                ),
            ),
            cloudRepository = cloud,
            localData = fixture.repository,
            tokenStorage = RecordingTokenStorage(),
        )

        val result = coordinator.signInWithGoogle()

        assertEquals(AccountActivationResult.Activated, result)
        assertEquals(false, coordinator.shouldInviteToAccount())
        assertEquals(setup.aquarium.id, cloud.lastCopy?.aquariums?.single()?.id)
        assertEquals(recorded.session.id, cloud.lastCopy?.sessions?.single()?.id)
        assertEquals(recorded.session.id, fixture.repository.observeLatestSession(setup.aquarium.id).first()?.session?.id)
        fixture.close()
    }

    @Test
    fun `cancelled Google authentication leaves the local copy available`() = runTest {
        val fixture = populatedAccountFixture()
        val coordinator = AccountActivationCoordinator(
            authGateway = FakeAuthGateway(AuthenticationResult.Cancelled),
            cloudRepository = RecordingCloudRepository(),
            localData = fixture.repository,
            tokenStorage = RecordingTokenStorage(),
        )

        assertEquals(AccountActivationResult.Cancelled, coordinator.signInWithGoogle())
        assertNull(fixture.repository.accountMigrationState())
        assertEquals(fixture.sessionId, fixture.repository.observeLatestSession(fixture.aquariumId).first()?.session?.id)
        fixture.close()
    }

    @Test
    fun `expired magic link can be reported without changing local data`() = runTest {
        val fixture = populatedAccountFixture()
        val coordinator = AccountActivationCoordinator(
            authGateway = FakeAuthGateway(AuthenticationResult.ExpiredLink),
            cloudRepository = RecordingCloudRepository(),
            localData = fixture.repository,
            tokenStorage = RecordingTokenStorage(),
        )

        assertEquals(AccountActivationResult.ExpiredLink, coordinator.completeMagicLink("aqualog://auth#expired"))
        assertNull(fixture.repository.accountMigrationState())
        assertEquals(fixture.sessionId, fixture.repository.observeLatestSession(fixture.aquariumId).first()?.session?.id)
        fixture.close()
    }

    @Test
    fun `magic link request reports that the email was sent`() = runTest {
        val fixture = populatedAccountFixture()
        val coordinator = AccountActivationCoordinator(
            authGateway = FakeAuthGateway(AuthenticationResult.Cancelled),
            cloudRepository = RecordingCloudRepository(),
            localData = fixture.repository,
            tokenStorage = RecordingTokenStorage(),
        )

        assertEquals(AccountActivationResult.MagicLinkSent, coordinator.requestMagicLink("user@example.com"))
        assertNull(fixture.repository.accountMigrationState())
        fixture.close()
    }

    @Test
    fun `interrupted initial migration resumes by replaying the same UUIDs`() = runTest {
        val fixture = populatedAccountFixture()
        val cloud = RecordingCloudRepository(failuresRemaining = 1)
        val tokenStorage = RecordingTokenStorage()
        val firstCoordinator = AccountActivationCoordinator(
            authGateway = FakeAuthGateway(
                AuthenticationResult.Authenticated(
                    AuthenticatedAccount("account-1", "access-token", "refresh-token"),
                ),
            ),
            cloudRepository = cloud,
            localData = fixture.repository,
            tokenStorage = tokenStorage,
        )

        assertIs<AccountActivationResult.MigrationFailed>(firstCoordinator.signInWithGoogle())
        assertEquals(InitialMigrationStatus.PENDING, fixture.repository.accountMigrationState()?.status)

        val resumedCoordinator = AccountActivationCoordinator(
            authGateway = FakeAuthGateway(AuthenticationResult.Cancelled),
            cloudRepository = cloud,
            localData = fixture.repository,
            tokenStorage = tokenStorage,
        )
        assertEquals(AccountActivationResult.Activated, resumedCoordinator.resumePendingMigration())
        assertEquals(listOf(fixture.sessionId, fixture.sessionId), cloud.attemptedSessionIds)
        assertEquals(InitialMigrationStatus.COMPLETE, fixture.repository.accountMigrationState()?.status)
        fixture.close()
    }

    @Test
    fun `expired access token is refreshed once before replaying the pending migration`() = runTest {
        val fixture = populatedAccountFixture()
        fixture.repository.beginAccountMigration("account-1")
        val cloud = RecordingCloudRepository(authenticationFailuresRemaining = 1)
        val tokenStorage = RecordingTokenStorage(AuthTokens("expired-access", "refresh-token"))
        val gateway = FakeAuthGateway(
            googleResult = AuthenticationResult.Cancelled,
            refreshResults = ArrayDeque(
                listOf(
                    AuthenticationResult.Authenticated(
                        AuthenticatedAccount("account-1", "renewed-access", "rotated-refresh"),
                    ),
                ),
            ),
        )
        val coordinator = AccountActivationCoordinator(gateway, cloud, fixture.repository, tokenStorage)

        assertEquals(AccountActivationResult.Activated, coordinator.resumePendingMigration())
        assertEquals(listOf("expired-access", "renewed-access"), cloud.attemptedAccessTokens)
        assertEquals(AuthTokens("renewed-access", "rotated-refresh"), tokenStorage.read())
        assertEquals(1, gateway.refreshAttempts)
        fixture.close()
    }

    @Test
    fun `failed token refresh asks for authentication without retrying forever`() = runTest {
        val fixture = populatedAccountFixture()
        fixture.repository.beginAccountMigration("account-1")
        val cloud = RecordingCloudRepository(authenticationFailuresRemaining = 2)
        val tokenStorage = RecordingTokenStorage(AuthTokens("expired-access", "invalid-refresh"))
        val gateway = FakeAuthGateway(
            googleResult = AuthenticationResult.Cancelled,
            refreshResults = ArrayDeque(listOf(AuthenticationResult.Failed("refresh rejected"))),
        )
        val coordinator = AccountActivationCoordinator(gateway, cloud, fixture.repository, tokenStorage)

        assertEquals(AccountActivationResult.ReauthenticationRequired, coordinator.resumePendingMigration())
        assertEquals(listOf("expired-access"), cloud.attemptedAccessTokens)
        assertNull(tokenStorage.read())
        assertEquals(1, gateway.refreshAttempts)
        fixture.close()
    }

    private fun accountFixture(
        databasePath: String = Files.createTempDirectory("aqualog-account")
            .resolve("aqualog.db")
            .toAbsolutePath()
            .toString(),
    ): AccountFixture {
        val database = createAquaLogDatabase(createJvmDatabaseBuilder(databasePath))
        val ids = generateSequence(1) { it + 1 }.map { "uuid-$it" }.iterator()
        return AccountFixture(
            database,
            RoomAquariumRepository(database, ids::next, { 1_700_000_000_000L }),
            databasePath,
        )
    }

    private suspend fun populatedAccountFixture(): PopulatedAccountFixture {
        val fixture = accountFixture()
        val setup = fixture.repository.createConfiguredAquarium(
            name = "Amazonien",
            volume = 120.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.ESTABLISHED,
            parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
        )
        val session = fixture.repository.saveRapidSession(
            RapidSessionInput(
                aquariumId = setup.aquarium.id,
                occurredAtEpochMillis = 1_700_000_100_000L,
                idempotencyKey = "first-session",
                maintenanceActions = listOf(MaintenanceActionInput(MaintenanceActionType.WATER_CHANGE)),
            ),
        )
        return PopulatedAccountFixture(fixture, setup.aquarium.id, session.session.id)
    }

    private class AccountFixture(
        private val database: AquaLogDatabase,
        val repository: RoomAquariumRepository,
        val databasePath: String,
    ) {
        fun close() = database.close()
    }

    private class PopulatedAccountFixture(
        private val delegate: AccountFixture,
        val aquariumId: String,
        val sessionId: String,
    ) {
        val repository: RoomAquariumRepository get() = delegate.repository
        fun close() = delegate.close()
    }

    private class FakeAuthGateway(
        private val googleResult: AuthenticationResult,
        private val refreshResults: ArrayDeque<AuthenticationResult> = ArrayDeque(),
    ) : AuthGateway {
        var refreshAttempts = 0

        override suspend fun signInWithGoogle() = googleResult
        override suspend fun requestMagicLink(email: String) = MagicLinkRequestResult.Sent
        override suspend fun completeMagicLink(callbackUrl: String) = googleResult
        override suspend fun refreshSession(refreshToken: String): AuthenticationResult {
            refreshAttempts += 1
            return refreshResults.removeFirstOrNull() ?: AuthenticationResult.Failed("refresh unavailable")
        }
    }

    private class RecordingCloudRepository(
        private var failuresRemaining: Int = 0,
        private var authenticationFailuresRemaining: Int = 0,
    ) : CloudRepository {
        var lastCopy: InitialAccountCopy? = null
        val attemptedSessionIds = mutableListOf<String>()
        val attemptedAccessTokens = mutableListOf<String>()

        override suspend fun upsertInitialCopy(accountId: String, copy: InitialAccountCopy, accessToken: String) {
            lastCopy = copy
            attemptedSessionIds += copy.sessions.single().id
            attemptedAccessTokens += accessToken
            if (authenticationFailuresRemaining-- > 0) throw CloudAuthenticationExpiredException()
            if (failuresRemaining-- > 0) error("offline")
        }
    }

    private class RecordingTokenStorage(initialTokens: AuthTokens? = null) : SecureTokenStorage {
        private var tokens = initialTokens
        override suspend fun save(tokens: AuthTokens) {
            this.tokens = tokens
        }
        override suspend fun read(): AuthTokens? = tokens
        override suspend fun clear() {
            tokens = null
        }
    }
}
