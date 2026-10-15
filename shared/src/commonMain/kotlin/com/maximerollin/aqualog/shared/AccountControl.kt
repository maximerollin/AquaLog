package com.maximerollin.aqualog.shared

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex

data class AccountIdentity(
    val accountId: String,
    val email: String?,
)

enum class LocalCopyDecision {
    KEEP,
    REMOVE,
}

enum class AccountDeletionPhase {
    CLOUD_PENDING,
    CLOUD_DELETED,
}

data class PendingAccountDeletion(
    val operationKey: String,
    val phase: AccountDeletionPhase,
)

sealed interface CloudAccountDeletionResult {
    data class Prepared(val recoveryCredential: String) : CloudAccountDeletionResult
    data object Completed : CloudAccountDeletionResult
    data class RetryableFailure(val safeDetail: String) : CloudAccountDeletionResult
    data object AuthenticationRequired : CloudAccountDeletionResult
    data object NotConfigured : CloudAccountDeletionResult
}

interface AccountDeletionGateway {
    suspend fun deleteAccount(
        accessToken: String,
        operationKey: String,
        recoveryCredential: String?,
    ): CloudAccountDeletionResult
}

interface DeletionRecoveryCredentialStorage {
    suspend fun saveDeletionCredential(operationKey: String, credential: String)
    suspend fun readDeletionCredential(operationKey: String): String?
    suspend fun clearDeletionCredential()
}

interface AccountAccessMarkerStore {
    suspend fun isAccountAccessRevoked(): Boolean
    suspend fun markAccountAccessRevoked()
}

class AccountOperationLease internal constructor(internal val generation: Long)

sealed interface AccountLeaseResult<out T> {
    data class Completed<T>(val value: T) : AccountLeaseResult<T>
    data object Revoked : AccountLeaseResult<Nothing>
}

class AccountOperationGate(private val markerStore: AccountAccessMarkerStore) {
    private val mutex = Mutex()
    private var generation = 0L

    suspend fun acquire(): AccountOperationLease? {
        mutex.lock()
        return try {
            if (markerStore.isAccountAccessRevoked()) null else AccountOperationLease(generation)
        } finally {
            mutex.unlock()
        }
    }

    suspend fun beginRevocation() {
        mutex.lock()
        try {
            generation += 1
            markerStore.markAccountAccessRevoked()
        } finally {
            mutex.unlock()
        }
    }

    suspend fun <T> runIfCurrent(
        lease: AccountOperationLease,
        operation: suspend () -> T,
    ): AccountLeaseResult<T> {
        mutex.lock()
        return try {
            if (lease.generation != generation || markerStore.isAccountAccessRevoked()) {
                AccountLeaseResult.Revoked
            } else {
                AccountLeaseResult.Completed(operation())
            }
        } finally {
            mutex.unlock()
        }
    }
}

interface AccountControlLocalData {
    suspend fun accountIdentity(): AccountIdentity?
    suspend fun pendingDeletion(): PendingAccountDeletion?
    suspend fun beginDeletion(operationKey: String)
    suspend fun markCloudDeletionComplete()
    suspend fun detachAccountKeepingLocalCopy()
    suspend fun removeAccountAndLocalCopy()
}

/** Prevents a pending deletion from racing with uploads that could recreate cloud data. */
class DeletionAwareSyncSessionProvider(
    private val localData: AccountControlLocalData,
    private val delegate: SyncSessionProvider,
) : SyncSessionProvider {
    override suspend fun currentSession(): SyncSession? =
        if (localData.pendingDeletion() == null) delegate.currentSession() else null

    override suspend fun refreshSession(expiredSession: SyncSession): SyncSession? =
        if (localData.pendingDeletion() == null) delegate.refreshSession(expiredSession) else null

    override suspend fun requireReauthentication() = delegate.requireReauthentication()
}

sealed interface AccountControlResult {
    data class SignedOut(val localCopyDecision: LocalCopyDecision) : AccountControlResult
    data class SignOutIncomplete(
        val failure: AccountControlFailure,
        val safeDetail: String,
    ) : AccountControlResult
    data object Deleted : AccountControlResult
    data class DeletionIncomplete(
        val failure: AccountControlFailure,
        val safeDetail: String,
    ) : AccountControlResult
}

sealed interface AccountDeletionReauthenticationResult {
    data object AwaitingAuthentication : AccountDeletionReauthenticationResult
    data object MagicLinkSent : AccountDeletionReauthenticationResult
    data object Cancelled : AccountDeletionReauthenticationResult
    data object ExpiredLink : AccountDeletionReauthenticationResult
    data object IgnoredCallback : AccountDeletionReauthenticationResult
    data class WrongAccount(val expectedEmail: String?) : AccountDeletionReauthenticationResult
    data class Failed(val safeDetail: String) : AccountDeletionReauthenticationResult
    data class Resumed(val deletionResult: AccountControlResult) : AccountDeletionReauthenticationResult
}

enum class AccountControlFailure {
    CLOUD,
    AUTHENTICATION,
    TOKEN_INVALIDATION,
    LOCAL_DATA,
    CONFIGURATION,
}

class AccountControlCoordinator(
    private val localData: AccountControlLocalData,
    private val tokenStorage: SecureTokenStorage,
    private val deletionGateway: AccountDeletionGateway,
    private val operationGate: AccountOperationGate? = null,
    private val deletionCredentialStorage: DeletionRecoveryCredentialStorage? = null,
    private val reauthenticationGateway: AuthGateway? = null,
    private val generateOperationKey: () -> String = { error("Deletion operation key generator is not configured") },
) {
    suspend fun identity(): AccountIdentity? = localData.accountIdentity()

    suspend fun pendingDeletion(): PendingAccountDeletion? = localData.pendingDeletion()

    suspend fun deletionRequiresReauthentication(): Boolean =
        localData.pendingDeletion()?.phase == AccountDeletionPhase.CLOUD_PENDING && tokenStorage.read() == null

    suspend fun reauthenticateDeletionWithGoogle(): AccountDeletionReauthenticationResult =
        reauthenticateDeletion(reauthenticationGateway?.signInWithGoogle())

    suspend fun requestDeletionMagicLink(): AccountDeletionReauthenticationResult {
        val gateway = reauthenticationGateway
            ?: return AccountDeletionReauthenticationResult.Failed("Account authentication is not configured for this build.")
        val expected = expectedDeletionAccount()
            ?: return AccountDeletionReauthenticationResult.Failed("No pending account deletion requires authentication.")
        val email = expected.email
            ?: return AccountDeletionReauthenticationResult.Failed("The account email is unavailable. Use Google sign-in instead.")
        return when (val result = gateway.requestMagicLink(email)) {
            MagicLinkRequestResult.Sent -> AccountDeletionReauthenticationResult.MagicLinkSent
            is MagicLinkRequestResult.Failed -> AccountDeletionReauthenticationResult.Failed(result.reason)
        }
    }

    suspend fun completeDeletionReauthentication(callbackUrl: String): AccountDeletionReauthenticationResult =
        reauthenticateDeletion(reauthenticationGateway?.completeMagicLink(callbackUrl))

    suspend fun logout(localCopyDecision: LocalCopyDecision): AccountControlResult {
        operationGate?.beginRevocation()
        try {
            tokenStorage.clear()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return AccountControlResult.SignOutIncomplete(
                AccountControlFailure.TOKEN_INVALIDATION,
                "Sign out could not clear secure account tokens. Your local data was not changed.",
            )
        }
        try {
            when (localCopyDecision) {
                LocalCopyDecision.KEEP -> localData.detachAccountKeepingLocalCopy()
                LocalCopyDecision.REMOVE -> localData.removeAccountAndLocalCopy()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return AccountControlResult.SignOutIncomplete(
                AccountControlFailure.LOCAL_DATA,
                "Account access was cleared, but the local sign-out choice still needs to be completed.",
            )
        }
        return AccountControlResult.SignedOut(localCopyDecision)
    }

    suspend fun deleteAccount(): AccountControlResult {
        val pending = localData.pendingDeletion() ?: PendingAccountDeletion(
            operationKey = generateOperationKey(),
            phase = AccountDeletionPhase.CLOUD_PENDING,
        ).also { localData.beginDeletion(it.operationKey) }
        operationGate?.beginRevocation()
        if (pending.phase == AccountDeletionPhase.CLOUD_PENDING) {
            val tokens = tokenStorage.read() ?: return AccountControlResult.DeletionIncomplete(
                AccountControlFailure.AUTHENTICATION,
                "Sign in again to verify and finish account deletion.",
            )
            var recoveryCredential = deletionCredentialStorage?.readDeletionCredential(pending.operationKey)
            var result = deletionGateway.deleteAccount(tokens.accessToken, pending.operationKey, recoveryCredential)
            if (result is CloudAccountDeletionResult.Prepared) {
                recoveryCredential = result.recoveryCredential
                val storage = deletionCredentialStorage ?: return AccountControlResult.DeletionIncomplete(
                    AccountControlFailure.CONFIGURATION,
                    "Secure deletion recovery is not configured for this build.",
                )
                storage.saveDeletionCredential(pending.operationKey, recoveryCredential)
                result = deletionGateway.deleteAccount(tokens.accessToken, pending.operationKey, recoveryCredential)
            }
            when (result) {
                is CloudAccountDeletionResult.Prepared -> {
                    return AccountControlResult.DeletionIncomplete(
                        AccountControlFailure.CLOUD,
                        "Account deletion preparation could not be completed. Try again.",
                    )
                }
                CloudAccountDeletionResult.Completed -> {
                    localData.markCloudDeletionComplete()
                }
                is CloudAccountDeletionResult.RetryableFailure -> {
                    return AccountControlResult.DeletionIncomplete(
                        AccountControlFailure.CLOUD,
                        result.safeDetail,
                    )
                }
                CloudAccountDeletionResult.AuthenticationRequired -> {
                    runCatching { tokenStorage.clear() }
                    return AccountControlResult.DeletionIncomplete(
                        AccountControlFailure.AUTHENTICATION,
                        "Sign in again to verify and finish account deletion.",
                    )
                }
                CloudAccountDeletionResult.NotConfigured -> {
                    return AccountControlResult.DeletionIncomplete(
                        AccountControlFailure.CONFIGURATION,
                        "Cloud account deletion is not configured for this build.",
                    )
                }
            }
        }
        try {
            deletionCredentialStorage?.clearDeletionCredential()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return AccountControlResult.DeletionIncomplete(
                AccountControlFailure.TOKEN_INVALIDATION,
                "Secure deletion recovery state could not be cleared.",
            )
        }
        try {
            tokenStorage.clear()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return AccountControlResult.DeletionIncomplete(
                AccountControlFailure.TOKEN_INVALIDATION,
                "Secure account tokens could not be cleared.",
            )
        }
        try {
            localData.removeAccountAndLocalCopy()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return AccountControlResult.DeletionIncomplete(
                AccountControlFailure.LOCAL_DATA,
                "Cloud deletion finished, but local data still needs to be removed.",
            )
        }
        return AccountControlResult.Deleted
    }

    private suspend fun reauthenticateDeletion(
        result: AuthenticationResult?,
    ): AccountDeletionReauthenticationResult {
        val expected = expectedDeletionAccount()
            ?: return AccountDeletionReauthenticationResult.Failed("No pending account deletion requires authentication.")
        return when (result) {
            null -> AccountDeletionReauthenticationResult.Failed("Account authentication is not configured for this build.")
            is AuthenticationResult.Authenticated -> {
                if (result.account.accountId != expected.accountId) {
                    AccountDeletionReauthenticationResult.WrongAccount(expected.email)
                } else {
                    tokenStorage.save(AuthTokens(result.account.accessToken, result.account.refreshToken))
                    AccountDeletionReauthenticationResult.Resumed(deleteAccount())
                }
            }
            AuthenticationResult.AwaitingCallback -> AccountDeletionReauthenticationResult.AwaitingAuthentication
            AuthenticationResult.Cancelled -> AccountDeletionReauthenticationResult.Cancelled
            AuthenticationResult.ExpiredLink -> AccountDeletionReauthenticationResult.ExpiredLink
            AuthenticationResult.IgnoredCallback -> AccountDeletionReauthenticationResult.IgnoredCallback
            is AuthenticationResult.Failed -> AccountDeletionReauthenticationResult.Failed(result.reason)
        }
    }

    private suspend fun expectedDeletionAccount(): AccountIdentity? =
        localData.accountIdentity()?.takeIf {
            localData.pendingDeletion()?.phase == AccountDeletionPhase.CLOUD_PENDING
        }
}
