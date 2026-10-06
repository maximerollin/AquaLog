package com.maximerollin.aqualog.shared

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
)

data class AuthenticatedAccount(
    val accountId: String,
    val accessToken: String,
    val refreshToken: String,
)

sealed interface AuthenticationResult {
    data class Authenticated(val account: AuthenticatedAccount) : AuthenticationResult
    data object AwaitingCallback : AuthenticationResult
    data object Cancelled : AuthenticationResult
    data object ExpiredLink : AuthenticationResult
    data class Failed(val reason: String) : AuthenticationResult
}

sealed interface MagicLinkRequestResult {
    data object Sent : MagicLinkRequestResult
    data class Failed(val reason: String) : MagicLinkRequestResult
}

interface AuthGateway {
    suspend fun signInWithGoogle(): AuthenticationResult
    suspend fun requestMagicLink(email: String): MagicLinkRequestResult
    suspend fun completeMagicLink(callbackUrl: String): AuthenticationResult
}

interface CloudRepository {
    /** Upserts every entity by its existing local UUID. Replaying the same copy must be idempotent. */
    suspend fun upsertInitialCopy(accountId: String, copy: InitialAccountCopy)
}

interface SecureTokenStorage {
    suspend fun save(tokens: AuthTokens)
    suspend fun read(): AuthTokens?
    suspend fun clear()
}

data class InitialAccountCopy(
    val aquariums: List<Aquarium>,
    val parameterDefinitions: List<ParameterDefinition>,
    val sessions: List<Session>,
    val measurements: List<Measurement>,
    val maintenanceActions: List<MaintenanceAction>,
    val events: List<SessionEvent>,
)

enum class InitialMigrationStatus(val storageValue: String) {
    PENDING("pending"),
    COMPLETE("complete");

    companion object {
        fun fromStorageValue(value: String) = entries.first { it.storageValue == value }
    }
}

data class AccountMigrationState(
    val accountId: String,
    val status: InitialMigrationStatus,
)

interface AccountLocalDataSource {
    suspend fun hasRecordedSession(): Boolean
    suspend fun initialAccountCopy(): InitialAccountCopy
    suspend fun accountMigrationState(): AccountMigrationState?
    suspend fun beginAccountMigration(accountId: String)
    suspend fun completeAccountMigration(accountId: String)
}

sealed interface AccountActivationResult {
    data object Activated : AccountActivationResult
    data object AwaitingAuthentication : AccountActivationResult
    data object Cancelled : AccountActivationResult
    data object ExpiredLink : AccountActivationResult
    data object MagicLinkSent : AccountActivationResult
    data class Failed(val reason: String) : AccountActivationResult
    data class MigrationFailed(val reason: String) : AccountActivationResult
}

class AccountActivationCoordinator(
    private val authGateway: AuthGateway,
    private val cloudRepository: CloudRepository,
    private val localData: AccountLocalDataSource,
    private val tokenStorage: SecureTokenStorage,
) {
    suspend fun shouldInviteToAccount(): Boolean =
        localData.hasRecordedSession() && localData.accountMigrationState() == null

    suspend fun hasPendingMigration(): Boolean =
        localData.accountMigrationState()?.status == InitialMigrationStatus.PENDING

    suspend fun signInWithGoogle(): AccountActivationResult =
        activate(authGateway.signInWithGoogle())

    suspend fun requestMagicLink(email: String): AccountActivationResult {
        if (email.isBlank()) return AccountActivationResult.Failed("Enter an email address")
        return when (val result = authGateway.requestMagicLink(email.trim())) {
            MagicLinkRequestResult.Sent -> AccountActivationResult.MagicLinkSent
            is MagicLinkRequestResult.Failed -> AccountActivationResult.Failed(result.reason)
        }
    }

    suspend fun completeMagicLink(callbackUrl: String): AccountActivationResult =
        activate(authGateway.completeMagicLink(callbackUrl))

    suspend fun resumePendingMigration(): AccountActivationResult {
        val migration = localData.accountMigrationState()
            ?: return AccountActivationResult.Failed("No account migration is pending")
        if (migration.status == InitialMigrationStatus.COMPLETE) return AccountActivationResult.Activated
        return migrate(migration.accountId)
    }

    private suspend fun activate(result: AuthenticationResult): AccountActivationResult = when (result) {
        is AuthenticationResult.Authenticated -> {
            tokenStorage.save(AuthTokens(result.account.accessToken, result.account.refreshToken))
            localData.beginAccountMigration(result.account.accountId)
            migrate(result.account.accountId)
        }
        AuthenticationResult.AwaitingCallback -> AccountActivationResult.AwaitingAuthentication
        AuthenticationResult.Cancelled -> AccountActivationResult.Cancelled
        AuthenticationResult.ExpiredLink -> AccountActivationResult.ExpiredLink
        is AuthenticationResult.Failed -> AccountActivationResult.Failed(result.reason)
    }

    private suspend fun migrate(accountId: String): AccountActivationResult = runCatching {
        cloudRepository.upsertInitialCopy(accountId, localData.initialAccountCopy())
        localData.completeAccountMigration(accountId)
        AccountActivationResult.Activated
    }.getOrElse { error ->
        AccountActivationResult.MigrationFailed(error.message ?: "Initial migration failed")
    }
}
