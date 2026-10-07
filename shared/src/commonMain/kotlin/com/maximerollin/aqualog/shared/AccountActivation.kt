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

data class PendingAuthenticationAttempt(
    val state: String,
    val codeVerifier: String,
)

data class AuthenticationCallback(
    val state: String?,
    val authorizationCode: String? = null,
    val accessToken: String? = null,
    val errorCode: String? = null,
    val errorDescription: String? = null,
)

sealed interface AuthenticationCallbackResult {
    data class AuthorizationCode(val code: String, val codeVerifier: String) : AuthenticationCallbackResult
    data object Cancelled : AuthenticationCallbackResult
    data object ExpiredLink : AuthenticationCallbackResult
    data class Rejected(val reason: String) : AuthenticationCallbackResult
    data class Failed(val reason: String) : AuthenticationCallbackResult
}

fun correlateAuthenticationCallback(
    pendingAttempt: PendingAuthenticationAttempt?,
    callback: AuthenticationCallback,
): AuthenticationCallbackResult {
    if (pendingAttempt == null) {
        return AuthenticationCallbackResult.Rejected("No authentication attempt is pending")
    }
    if (callback.state == null || callback.state != pendingAttempt.state) {
        return AuthenticationCallbackResult.Rejected("Authentication callback does not match the pending attempt")
    }
    if (callback.accessToken != null) {
        return AuthenticationCallbackResult.Rejected("Implicit authentication tokens are not accepted")
    }
    if (callback.errorCode == "otp_expired") return AuthenticationCallbackResult.ExpiredLink
    if (callback.errorCode == "access_denied") return AuthenticationCallbackResult.Cancelled
    callback.errorDescription?.let { return AuthenticationCallbackResult.Failed(it) }
    val code = callback.authorizationCode
        ?: return AuthenticationCallbackResult.Rejected("Authentication callback contains no authorization code")
    return AuthenticationCallbackResult.AuthorizationCode(code, pendingAttempt.codeVerifier)
}

sealed interface AuthenticationResult {
    data class Authenticated(val account: AuthenticatedAccount) : AuthenticationResult
    data object AwaitingCallback : AuthenticationResult
    data object Cancelled : AuthenticationResult
    data object ExpiredLink : AuthenticationResult
    data object IgnoredCallback : AuthenticationResult
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
    suspend fun refreshSession(refreshToken: String): AuthenticationResult
}

interface CloudRepository {
    /** Upserts every entity by its existing local UUID. Replaying the same copy must be idempotent. */
    suspend fun upsertInitialCopy(accountId: String, copy: InitialAccountCopy, accessToken: String)
}

class CloudAuthenticationExpiredException : Exception("Cloud authentication expired")

interface SecureTokenStorage {
    suspend fun save(tokens: AuthTokens)
    suspend fun read(): AuthTokens?
    suspend fun clear()
}

interface AuthenticationAttemptStorage {
    suspend fun saveAttempt(attempt: PendingAuthenticationAttempt)
    suspend fun readAttempt(): PendingAuthenticationAttempt?
    suspend fun clearAttempt()
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
    suspend fun wasAccountInvitationOffered(): Boolean
    suspend fun markAccountInvitationOffered()
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
    data object IgnoredCallback : AccountActivationResult
    data object ReauthenticationRequired : AccountActivationResult
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
        localData.hasRecordedSession() &&
            !localData.wasAccountInvitationOffered() &&
            localData.accountMigrationState() == null

    suspend fun markInvitationOffered() = localData.markAccountInvitationOffered()

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
        val tokens = tokenStorage.read() ?: return AccountActivationResult.ReauthenticationRequired
        return migrate(migration.accountId, tokens)
    }

    private suspend fun activate(result: AuthenticationResult): AccountActivationResult = when (result) {
        is AuthenticationResult.Authenticated -> {
            tokenStorage.save(AuthTokens(result.account.accessToken, result.account.refreshToken))
            localData.beginAccountMigration(result.account.accountId)
            migrate(
                accountId = result.account.accountId,
                tokens = AuthTokens(result.account.accessToken, result.account.refreshToken),
            )
        }
        AuthenticationResult.AwaitingCallback -> AccountActivationResult.AwaitingAuthentication
        AuthenticationResult.Cancelled -> AccountActivationResult.Cancelled
        AuthenticationResult.ExpiredLink -> AccountActivationResult.ExpiredLink
        AuthenticationResult.IgnoredCallback -> AccountActivationResult.IgnoredCallback
        is AuthenticationResult.Failed -> AccountActivationResult.Failed(result.reason)
    }

    private suspend fun migrate(
        accountId: String,
        tokens: AuthTokens,
        canRefresh: Boolean = true,
    ): AccountActivationResult = try {
        cloudRepository.upsertInitialCopy(accountId, localData.initialAccountCopy(), tokens.accessToken)
        localData.completeAccountMigration(accountId)
        AccountActivationResult.Activated
    } catch (error: CloudAuthenticationExpiredException) {
        if (!canRefresh) {
            tokenStorage.clear()
            AccountActivationResult.ReauthenticationRequired
        } else {
            refreshAndResume(accountId, tokens.refreshToken)
        }
    } catch (error: Exception) {
        AccountActivationResult.MigrationFailed(error.message ?: "Initial migration failed")
    }

    private suspend fun refreshAndResume(accountId: String, refreshToken: String): AccountActivationResult {
        val refreshed = authGateway.refreshSession(refreshToken)
        if (refreshed !is AuthenticationResult.Authenticated || refreshed.account.accountId != accountId) {
            tokenStorage.clear()
            return AccountActivationResult.ReauthenticationRequired
        }
        val tokens = AuthTokens(refreshed.account.accessToken, refreshed.account.refreshToken)
        tokenStorage.save(tokens)
        return migrate(accountId, tokens, canRefresh = false)
    }
}
