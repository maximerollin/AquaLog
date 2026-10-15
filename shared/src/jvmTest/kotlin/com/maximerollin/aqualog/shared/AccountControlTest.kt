package com.maximerollin.aqualog.shared

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountControlTest {
    @Test
    fun `logout keeping the local copy detaches account ownership and invalidates tokens`() = runTest {
        val local = FakeAccountControlLocalData(
            account = AccountIdentity("account-1", "owner@example.com"),
            hasLocalData = true,
            hasPendingSync = true,
        )
        val tokens = FakeTokenStorage(AuthTokens("access", "refresh"))
        val coordinator = AccountControlCoordinator(local, tokens, FakeDeletionGateway())

        val result = coordinator.logout(LocalCopyDecision.KEEP)

        assertEquals(AccountControlResult.SignedOut(LocalCopyDecision.KEEP), result)
        assertTrue(local.hasLocalData)
        assertNull(local.account)
        assertEquals(false, local.hasPendingSync)
        assertNull(tokens.read())
    }

    @Test
    fun `a cloud deletion failure stays pending and keeps local data and tokens for retry`() = runTest {
        val local = FakeAccountControlLocalData(
            account = AccountIdentity("account-1", "owner@example.com"),
            hasLocalData = true,
            hasPendingSync = false,
        )
        val tokens = FakeTokenStorage(AuthTokens("access", "refresh"))
        val gateway = FakeDeletionGateway(
            CloudAccountDeletionResult.RetryableFailure("Service temporarily unavailable"),
        )
        val coordinator = AccountControlCoordinator(local, tokens, gateway) { "deletion-1" }

        val result = coordinator.deleteAccount()

        assertEquals(
            AccountControlResult.DeletionIncomplete(
                AccountControlFailure.CLOUD,
                "Service temporarily unavailable",
            ),
            result,
        )
        assertEquals(
            PendingAccountDeletion("deletion-1", AccountDeletionPhase.CLOUD_PENDING),
            local.pendingDeletion(),
        )
        assertTrue(local.hasLocalData)
        assertEquals(AuthTokens("access", "refresh"), tokens.read())
    }

    @Test
    fun `retry after cloud deletion resumes token invalidation without repeating cloud deletion`() = runTest {
        val local = FakeAccountControlLocalData(
            account = AccountIdentity("account-1", "owner@example.com"),
            hasLocalData = true,
            hasPendingSync = false,
        )
        val tokens = FakeTokenStorage(AuthTokens("access", "refresh"), failNextClear = true)
        val gateway = SequenceDeletionGateway(
            mutableListOf(CloudAccountDeletionResult.Completed),
        )
        val coordinator = AccountControlCoordinator(local, tokens, gateway) { "deletion-1" }

        assertEquals(
            AccountControlResult.DeletionIncomplete(
                AccountControlFailure.TOKEN_INVALIDATION,
                "Secure account tokens could not be cleared.",
            ),
            coordinator.deleteAccount(),
        )
        assertEquals(
            PendingAccountDeletion("deletion-1", AccountDeletionPhase.CLOUD_DELETED),
            local.pendingDeletion(),
        )
        assertTrue(local.hasLocalData)

        assertEquals(AccountControlResult.Deleted, coordinator.deleteAccount())
        assertNull(tokens.read())
        assertEquals(false, local.hasLocalData)
    }

    @Test
    fun `Room logout keeps the portable copy but removes account ownership and its outbox`() = runTest {
        val database = createAquaLogDatabase(
            createJvmDatabaseBuilder(
                Files.createTempDirectory("aqualog-account-logout")
                    .resolve("aqualog.db")
                    .toString(),
            ),
        )
        val ids = generateSequence(1) { it + 1 }.map { "account-id-$it" }.iterator()
        val repository = RoomAquariumRepository(database, ids::next, { 100L })
        repository.createConfiguredAquarium(
            "Kept Aquarium",
            80.0,
            VolumeUnit.LITERS,
            AquariumProfile.ESTABLISHED,
            OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
        )
        repository.beginAccountMigration("account-1", "owner@example.com")
        repository.completeAccountMigration("account-1")
        assertEquals(AccountIdentity("account-1", "owner@example.com"), repository.accountIdentity())
        val portableData = PortableDataService(database)
        val export = portableData.export()
        val coordinator = AccountControlCoordinator(
            repository,
            FakeTokenStorage(AuthTokens("access", "refresh")),
            FakeDeletionGateway(),
        )

        assertEquals(
            AccountControlResult.SignedOut(LocalCopyDecision.KEEP),
            coordinator.logout(LocalCopyDecision.KEEP),
        )

        assertEquals("Kept Aquarium", repository.observeCurrentAquarium().first()?.name)
        assertNull(repository.accountIdentity())
        assertTrue(repository.pendingSyncBatch().operations.isEmpty())
        assertIs<PortableImportResult.Imported>(portableData.importData(export))
        database.close()
    }

    @Test
    fun `local cleanup failure after cloud deletion is recoverable without account tokens`() = runTest {
        val local = FakeAccountControlLocalData(
            account = AccountIdentity("account-1", "owner@example.com"),
            hasLocalData = true,
            hasPendingSync = false,
            failNextRemoval = true,
        )
        val tokens = FakeTokenStorage(AuthTokens("access", "refresh"))
        val gateway = SequenceDeletionGateway(
            mutableListOf(CloudAccountDeletionResult.Completed),
        )
        val coordinator = AccountControlCoordinator(local, tokens, gateway) { "deletion-1" }

        assertEquals(
            AccountControlResult.DeletionIncomplete(
                AccountControlFailure.LOCAL_DATA,
                "Cloud deletion finished, but local data still needs to be removed.",
            ),
            coordinator.deleteAccount(),
        )
        assertNull(tokens.read())
        assertTrue(local.hasLocalData)
        assertEquals(AccountDeletionPhase.CLOUD_DELETED, local.pendingDeletion()?.phase)

        assertEquals(AccountControlResult.Deleted, coordinator.deleteAccount())
        assertEquals(false, local.hasLocalData)
    }

    @Test
    fun `pending account deletion blocks synchronization without consuming local changes`() = runTest {
        val local = FakeAccountControlLocalData(
            account = AccountIdentity("account-1", "owner@example.com"),
            hasLocalData = true,
            hasPendingSync = true,
        ).also { it.beginDeletion("deletion-1") }
        var delegateCalls = 0
        val provider = DeletionAwareSyncSessionProvider(
            local,
            object : SyncSessionProvider {
                override suspend fun currentSession(): SyncSession {
                    delegateCalls += 1
                    return SyncSession("account-1", "access")
                }
            },
        )

        assertNull(provider.currentSession())
        assertEquals(0, delegateCalls)
        assertTrue(local.hasPendingSync)
        assertTrue(local.hasLocalData)
    }

    @Test
    fun `Room logout removing the local copy clears account and aquarium data`() = runTest {
        val database = createAquaLogDatabase(
            createJvmDatabaseBuilder(
                Files.createTempDirectory("aqualog-account-remove")
                    .resolve("aqualog.db")
                    .toString(),
            ),
        )
        val ids = generateSequence(1) { it + 1 }.map { "remove-id-$it" }.iterator()
        val repository = RoomAquariumRepository(database, ids::next, { 100L })
        repository.createConfiguredAquarium(
            "Removed Aquarium",
            80.0,
            VolumeUnit.LITERS,
            AquariumProfile.ESTABLISHED,
            OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
        )
        repository.beginAccountMigration("account-1", "owner@example.com")
        repository.completeAccountMigration("account-1")
        val coordinator = AccountControlCoordinator(
            repository,
            FakeTokenStorage(AuthTokens("access", "refresh")),
            FakeDeletionGateway(),
        )

        assertEquals(
            AccountControlResult.SignedOut(LocalCopyDecision.REMOVE),
            coordinator.logout(LocalCopyDecision.REMOVE),
        )
        assertNull(repository.observeCurrentAquarium().first())
        assertNull(repository.accountIdentity())
        database.close()
    }

    @Test
    fun `failed token invalidation never reports sign out as complete or removes local data`() = runTest {
        val local = FakeAccountControlLocalData(
            account = AccountIdentity("account-1", "owner@example.com"),
            hasLocalData = true,
            hasPendingSync = true,
        )
        val tokens = FakeTokenStorage(AuthTokens("access", "refresh"), failNextClear = true)
        val coordinator = AccountControlCoordinator(local, tokens, FakeDeletionGateway())

        assertEquals(
            AccountControlResult.SignOutIncomplete(
                AccountControlFailure.TOKEN_INVALIDATION,
                "Sign out could not clear secure account tokens. Your local data was not changed.",
            ),
            coordinator.logout(LocalCopyDecision.REMOVE),
        )
        assertTrue(local.hasLocalData)
        assertEquals("account-1", local.account?.accountId)
    }

    @Test
    fun `logout persists revocation marker before clearing secure tokens`() = runTest {
        val local = FakeAccountControlLocalData(
            account = AccountIdentity("account-1", "owner@example.com"),
            hasLocalData = true,
            hasPendingSync = true,
        )
        val gate = AccountOperationGate(local)
        val tokens = object : SecureTokenStorage {
            override suspend fun save(tokens: AuthTokens) = Unit
            override suspend fun read() = AuthTokens("access", "refresh")
            override suspend fun clear() {
                assertTrue(local.accessRevoked, "revocation must be durable before token clearing starts")
            }
        }
        val coordinator = AccountControlCoordinator(
            localData = local,
            tokenStorage = tokens,
            deletionGateway = FakeDeletionGateway(),
            operationGate = gate,
        )

        assertIs<AccountControlResult.SignedOut>(coordinator.logout(LocalCopyDecision.KEEP))
    }

    @Test
    fun `refresh result cannot save tokens after concurrent revocation`() = runTest {
        val local = FakeAccountControlLocalData(
            account = AccountIdentity("account-1", "owner@example.com"),
            hasLocalData = true,
            hasPendingSync = true,
        )
        val gate = AccountOperationGate(local)
        val lease = requireNotNull(gate.acquire())
        val response = CompletableDeferred<AuthTokens>()
        var saved: AuthTokens? = null
        val refresh = async {
            val refreshed = response.await()
            gate.runIfCurrent(lease) { saved = refreshed }
        }

        gate.beginRevocation()
        response.complete(AuthTokens("new-access", "new-refresh"))

        assertEquals(AccountLeaseResult.Revoked, refresh.await())
        assertNull(saved)
    }

    @Test
    fun `server-issued deletion credential is persisted before destructive retry and then cleared`() = runTest {
        val local = FakeAccountControlLocalData(
            account = AccountIdentity("account-1", "owner@example.com"),
            hasLocalData = true,
            hasPendingSync = false,
        )
        val gateway = SequenceDeletionGateway(
            mutableListOf(
                CloudAccountDeletionResult.Prepared("signed-account-1-operation-1"),
                CloudAccountDeletionResult.Completed,
            ),
        )
        val credentialStorage = FakeDeletionCredentialStorage()
        val coordinator = AccountControlCoordinator(
            localData = local,
            tokenStorage = FakeTokenStorage(AuthTokens("access", "refresh")),
            deletionGateway = gateway,
            deletionCredentialStorage = credentialStorage,
            generateOperationKey = { "operation-1" },
        )

        assertEquals(AccountControlResult.Deleted, coordinator.deleteAccount())
        assertEquals(listOf(null, "signed-account-1-operation-1"), gateway.credentials)
        assertNull(credentialStorage.credential)
    }

    @Test
    fun `expired deletion authentication can reauthenticate the expected account and resume the same operation`() = runTest {
        val local = FakeAccountControlLocalData(
            account = AccountIdentity("account-1", "owner@example.com"),
            hasLocalData = true,
            hasPendingSync = false,
        )
        val tokens = FakeTokenStorage(AuthTokens("expired-access", "expired-refresh"))
        val gateway = SequenceDeletionGateway(
            mutableListOf(
                CloudAccountDeletionResult.AuthenticationRequired,
                CloudAccountDeletionResult.Completed,
            ),
        )
        val coordinator = AccountControlCoordinator(
            localData = local,
            tokenStorage = tokens,
            deletionGateway = gateway,
            reauthenticationGateway = FakeReauthenticationGateway(
                AuthenticationResult.Authenticated(
                    AuthenticatedAccount("account-1", "new-access", "new-refresh", "owner@example.com"),
                ),
            ),
            generateOperationKey = { "operation-1" },
        )

        assertIs<AccountControlResult.DeletionIncomplete>(coordinator.deleteAccount())
        assertEquals(
            AccountDeletionReauthenticationResult.Resumed(AccountControlResult.Deleted),
            coordinator.reauthenticateDeletionWithGoogle(),
        )
        assertEquals(listOf("operation-1", "operation-1"), gateway.operationKeys)
        assertEquals(listOf("expired-access", "new-access"), gateway.accessTokens)
        assertEquals(false, local.hasLocalData)
        assertNull(tokens.read())
    }

    @Test
    fun `reauthenticating another account refuses deletion without replacing tokens or pending operation`() = runTest {
        val local = FakeAccountControlLocalData(
            account = AccountIdentity("account-1", "owner@example.com"),
            hasLocalData = true,
            hasPendingSync = false,
        )
        val tokens = FakeTokenStorage(AuthTokens("expired-access", "expired-refresh"))
        val gateway = SequenceDeletionGateway(
            mutableListOf(CloudAccountDeletionResult.AuthenticationRequired),
        )
        val coordinator = AccountControlCoordinator(
            localData = local,
            tokenStorage = tokens,
            deletionGateway = gateway,
            reauthenticationGateway = FakeReauthenticationGateway(
                AuthenticationResult.Authenticated(
                    AuthenticatedAccount("account-2", "other-access", "other-refresh", "other@example.com"),
                ),
            ),
            generateOperationKey = { "operation-1" },
        )

        assertIs<AccountControlResult.DeletionIncomplete>(coordinator.deleteAccount())
        assertEquals(
            AccountDeletionReauthenticationResult.WrongAccount("owner@example.com"),
            coordinator.reauthenticateDeletionWithGoogle(),
        )
        assertEquals(PendingAccountDeletion("operation-1", AccountDeletionPhase.CLOUD_PENDING), local.pendingDeletion())
        assertEquals(1, gateway.operationKeys.size)
        assertNull(tokens.read())
        assertTrue(local.hasLocalData)
    }

    private class FakeTokenStorage(
        initial: AuthTokens?,
        private var failNextClear: Boolean = false,
    ) : SecureTokenStorage {
        private var tokens = initial

        override suspend fun save(tokens: AuthTokens) {
            this.tokens = tokens
        }

        override suspend fun read() = tokens

        override suspend fun clear() {
            if (failNextClear) {
                failNextClear = false
                error("Secure account tokens could not be cleared")
            }
            tokens = null
        }
    }

    private class FakeDeletionGateway(
        private val result: CloudAccountDeletionResult = CloudAccountDeletionResult.Completed,
    ) : AccountDeletionGateway {
        override suspend fun deleteAccount(accessToken: String, operationKey: String, recoveryCredential: String?) =
            result
    }

    private class SequenceDeletionGateway(
        private val results: MutableList<CloudAccountDeletionResult>,
    ) : AccountDeletionGateway {
        val credentials = mutableListOf<String?>()
        val operationKeys = mutableListOf<String>()
        val accessTokens = mutableListOf<String>()
        override suspend fun deleteAccount(
            accessToken: String,
            operationKey: String,
            recoveryCredential: String?,
        ): CloudAccountDeletionResult {
            credentials += recoveryCredential
            operationKeys += operationKey
            accessTokens += accessToken
            return results.removeFirstOrNull() ?: error("Cloud deletion must not repeat after completion")
        }
    }

    private class FakeReauthenticationGateway(
        private val googleResult: AuthenticationResult,
    ) : AuthGateway {
        override suspend fun signInWithGoogle() = googleResult
        override suspend fun requestMagicLink(email: String) = MagicLinkRequestResult.Sent
        override suspend fun completeMagicLink(callbackUrl: String) = googleResult
        override suspend fun refreshSession(refreshToken: String) = AuthenticationResult.Failed("unused")
    }

    private class FakeDeletionCredentialStorage : DeletionRecoveryCredentialStorage {
        var credential: String? = null
        private var operationKey: String? = null
        override suspend fun saveDeletionCredential(operationKey: String, credential: String) {
            this.operationKey = operationKey
            this.credential = credential
        }
        override suspend fun readDeletionCredential(operationKey: String): String? =
            credential.takeIf { this.operationKey == operationKey }
        override suspend fun clearDeletionCredential() {
            operationKey = null
            credential = null
        }
    }

    private class FakeAccountControlLocalData(
        var account: AccountIdentity?,
        var hasLocalData: Boolean,
        var hasPendingSync: Boolean,
        private var failNextRemoval: Boolean = false,
    ) : AccountControlLocalData, AccountAccessMarkerStore {
        private var deletion: PendingAccountDeletion? = null
        var accessRevoked = false

        override suspend fun accountIdentity() = account

        override suspend fun pendingDeletion() = deletion

        override suspend fun beginDeletion(operationKey: String) {
            deletion = PendingAccountDeletion(operationKey, AccountDeletionPhase.CLOUD_PENDING)
        }

        override suspend fun isAccountAccessRevoked() = accessRevoked

        override suspend fun markAccountAccessRevoked() {
            accessRevoked = true
        }

        override suspend fun markCloudDeletionComplete() {
            deletion = requireNotNull(deletion).copy(phase = AccountDeletionPhase.CLOUD_DELETED)
        }

        override suspend fun detachAccountKeepingLocalCopy() {
            account = null
            hasPendingSync = false
            deletion = null
            accessRevoked = false
        }

        override suspend fun removeAccountAndLocalCopy() {
            if (failNextRemoval) {
                failNextRemoval = false
                error("local database unavailable")
            }
            account = null
            hasPendingSync = false
            hasLocalData = false
            deletion = null
            accessRevoked = false
        }
    }
}
