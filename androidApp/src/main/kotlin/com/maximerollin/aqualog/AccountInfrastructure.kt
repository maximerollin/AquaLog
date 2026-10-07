package com.maximerollin.aqualog

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.maximerollin.aqualog.shared.AuthGateway
import com.maximerollin.aqualog.shared.AuthenticationAttemptStorage
import com.maximerollin.aqualog.shared.AuthenticationCallback
import com.maximerollin.aqualog.shared.AuthenticationCallbackResult
import com.maximerollin.aqualog.shared.AuthTokens
import com.maximerollin.aqualog.shared.AuthenticatedAccount
import com.maximerollin.aqualog.shared.AuthenticationResult
import com.maximerollin.aqualog.shared.CloudRepository
import com.maximerollin.aqualog.shared.CloudAuthenticationExpiredException
import com.maximerollin.aqualog.shared.InitialAccountCopy
import com.maximerollin.aqualog.shared.MagicLinkRequestResult
import com.maximerollin.aqualog.shared.PendingAuthenticationAttempt
import com.maximerollin.aqualog.shared.SecureTokenStorage
import com.maximerollin.aqualog.shared.correlateAuthenticationCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SupabaseConfiguration(
    val projectUrl: String,
    val anonymousKey: String,
    val redirectUrl: String = "aqualog://auth/callback",
) {
    val isConfigured: Boolean
        get() = projectUrl.startsWith("https://") && anonymousKey.isNotBlank()

    companion object {
        fun fromBuildConfig() = SupabaseConfiguration(
            projectUrl = BuildConfig.SUPABASE_URL.trimEnd('/'),
            anonymousKey = BuildConfig.SUPABASE_ANON_KEY,
        )
    }
}

class AndroidSecureTokenStorage(context: Context) : SecureTokenStorage, AuthenticationAttemptStorage {
    private val preferences = context.getSharedPreferences("encrypted_account_tokens", Context.MODE_PRIVATE)

    override suspend fun save(tokens: AuthTokens) = withContext(Dispatchers.IO) {
        val plaintext = "${tokens.accessToken}\u0000${tokens.refreshToken}".toByteArray()
        saveEncrypted(TOKEN_IV_KEY, TOKEN_KEY, plaintext)
    }

    override suspend fun read(): AuthTokens? = withContext(Dispatchers.IO) {
        readEncrypted(TOKEN_IV_KEY, TOKEN_KEY)?.let { plaintext ->
            val parts = plaintext.toString(Charsets.UTF_8).split('\u0000', limit = 2)
            if (parts.size != 2) return@let null
            AuthTokens(parts[0], parts[1])
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        check(preferences.edit().remove(TOKEN_IV_KEY).remove(TOKEN_KEY).commit()) {
            "Secure account tokens could not be cleared"
        }
    }

    override suspend fun saveAttempt(attempt: PendingAuthenticationAttempt) = withContext(Dispatchers.IO) {
        saveEncrypted(
            ATTEMPT_IV_KEY,
            ATTEMPT_KEY,
            "${attempt.state}\u0000${attempt.codeVerifier}".toByteArray(),
        )
    }

    override suspend fun readAttempt(): PendingAuthenticationAttempt? = withContext(Dispatchers.IO) {
        readEncrypted(ATTEMPT_IV_KEY, ATTEMPT_KEY)?.let { plaintext ->
            val parts = plaintext.toString(Charsets.UTF_8).split('\u0000', limit = 2)
            if (parts.size != 2) return@let null
            PendingAuthenticationAttempt(parts[0], parts[1])
        }
    }

    override suspend fun clearAttempt() = withContext(Dispatchers.IO) {
        check(preferences.edit().remove(ATTEMPT_IV_KEY).remove(ATTEMPT_KEY).commit()) {
            "Pending authentication attempt could not be cleared"
        }
    }

    private fun saveEncrypted(ivKey: String, valueKey: String, plaintext: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plaintext)
        check(preferences.edit()
            .putString(ivKey, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(valueKey, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .commit()) { "Secure account state could not be persisted" }
    }

    private fun readEncrypted(ivKey: String, valueKey: String): ByteArray? = runCatching {
        val iv = preferences.getString(ivKey, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
            ?: return@runCatching null
        val encrypted = preferences.getString(valueKey, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
            ?: return@runCatching null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        cipher.doFinal(encrypted)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEY_ALIAS = "aqualog-account-token-key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TOKEN_IV_KEY = "token_iv"
        const val TOKEN_KEY = "tokens"
        const val ATTEMPT_IV_KEY = "attempt_iv"
        const val ATTEMPT_KEY = "attempt"
    }
}

class SupabaseAuthGateway(
    private val context: Context,
    private val configuration: SupabaseConfiguration,
    private val attemptStorage: AuthenticationAttemptStorage,
    private val secureRandom: SecureRandom = SecureRandom(),
    private val openBrowser: (Uri) -> Unit = { uri ->
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    },
) : AuthGateway {
    override suspend fun signInWithGoogle(): AuthenticationResult {
        if (!configuration.isConfigured) return AuthenticationResult.Failed(CONFIGURATION_ERROR)
        val attempt = createAttempt()
        val uri = Uri.parse("${configuration.projectUrl}/auth/v1/authorize").buildUpon()
            .appendQueryParameter("provider", "google")
            .appendQueryParameter("scopes", "email profile")
            .appendQueryParameter("redirect_to", callbackUrl(attempt.state))
            .appendQueryParameter("code_challenge", codeChallenge(attempt.codeVerifier))
            .appendQueryParameter("code_challenge_method", "s256")
            .build()
        return runCatching {
            openBrowser(uri)
            AuthenticationResult.AwaitingCallback
        }.getOrElse {
            attemptStorage.clearAttempt()
            AuthenticationResult.Failed(it.message ?: "Unable to open authentication")
        }
    }

    override suspend fun requestMagicLink(email: String): MagicLinkRequestResult {
        if (!configuration.isConfigured) return MagicLinkRequestResult.Failed(CONFIGURATION_ERROR)
        val attempt = createAttempt()
        return withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject()
                    .put("email", email)
                    .put("create_user", true)
                    .put("code_challenge", codeChallenge(attempt.codeVerifier))
                    .put("code_challenge_method", "s256")
                request(
                    url = "${configuration.projectUrl}/auth/v1/otp?redirect_to=${encode(callbackUrl(attempt.state))}",
                    method = "POST",
                    body = body.toString(),
                )
                MagicLinkRequestResult.Sent
            }.getOrElse {
                attemptStorage.clearAttempt()
                MagicLinkRequestResult.Failed(it.message ?: "Magic link request failed")
            }
        }
    }

    override suspend fun completeMagicLink(callbackUrl: String): AuthenticationResult {
        if (!configuration.isConfigured) return AuthenticationResult.Failed(CONFIGURATION_ERROR)
        val callbackUri = Uri.parse(callbackUrl)
        if (
            callbackUri.scheme != "aqualog" ||
            callbackUri.host != "auth" ||
            callbackUri.path != "/callback"
        ) {
            return AuthenticationResult.IgnoredCallback
        }
        val values = callbackValues(callbackUrl)
        val correlated = correlateAuthenticationCallback(
            pendingAttempt = attemptStorage.readAttempt(),
            callback = AuthenticationCallback(
                state = values["state"],
                authorizationCode = values["code"],
                accessToken = values["access_token"],
                errorCode = values["error_code"] ?: values["error"],
                errorDescription = values["error_description"],
            ),
        )
        return when (correlated) {
            AuthenticationCallbackResult.Cancelled -> {
                attemptStorage.clearAttempt()
                AuthenticationResult.Cancelled
            }
            AuthenticationCallbackResult.ExpiredLink -> {
                attemptStorage.clearAttempt()
                AuthenticationResult.ExpiredLink
            }
            is AuthenticationCallbackResult.Rejected -> AuthenticationResult.IgnoredCallback
            is AuthenticationCallbackResult.Failed -> AuthenticationResult.Failed(correlated.reason)
            is AuthenticationCallbackResult.AuthorizationCode -> {
                exchangeAuthorizationCode(correlated).also { result ->
                    if (result is AuthenticationResult.Authenticated) attemptStorage.clearAttempt()
                }
            }
        }
    }

    override suspend fun refreshSession(refreshToken: String): AuthenticationResult {
        if (!configuration.isConfigured) return AuthenticationResult.Failed(CONFIGURATION_ERROR)
        return withContext(Dispatchers.IO) {
            runCatching {
                val tokenResponse = JSONObject(
                    request(
                        url = "${configuration.projectUrl}/auth/v1/token?grant_type=refresh_token",
                        method = "POST",
                        body = JSONObject().put("refresh_token", refreshToken).toString(),
                    ),
                )
                val accessToken = tokenResponse.getString("access_token")
                val rotatedRefreshToken = tokenResponse.getString("refresh_token")
                val user = JSONObject(
                    request("${configuration.projectUrl}/auth/v1/user", bearerToken = accessToken),
                )
                AuthenticationResult.Authenticated(
                    AuthenticatedAccount(user.getString("id"), accessToken, rotatedRefreshToken),
                )
            }.getOrElse { AuthenticationResult.Failed(it.message ?: "Session refresh failed") }
        }
    }

    private suspend fun createAttempt(): PendingAuthenticationAttempt {
        val attempt = PendingAuthenticationAttempt(
            state = randomUrlSafeValue(32),
            codeVerifier = randomUrlSafeValue(64),
        )
        attemptStorage.saveAttempt(attempt)
        return attempt
    }

    private fun callbackUrl(state: String): String = Uri.parse(configuration.redirectUrl).buildUpon()
        .appendQueryParameter("state", state)
        .build()
        .toString()

    private fun codeChallenge(verifier: String): String = urlSafeBase64(
        MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII)),
    )

    private fun randomUrlSafeValue(size: Int): String = ByteArray(size)
        .also(secureRandom::nextBytes)
        .let(::urlSafeBase64)

    private fun urlSafeBase64(value: ByteArray): String =
        Base64.encodeToString(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private suspend fun exchangeAuthorizationCode(
        callback: AuthenticationCallbackResult.AuthorizationCode,
    ): AuthenticationResult = withContext(Dispatchers.IO) {
        runCatching {
            val tokenResponse = JSONObject(
                request(
                    url = "${configuration.projectUrl}/auth/v1/token?grant_type=pkce",
                    method = "POST",
                    body = JSONObject()
                        .put("auth_code", callback.code)
                        .put("code_verifier", callback.codeVerifier)
                        .toString(),
                ),
            )
            val accessToken = tokenResponse.getString("access_token")
            val refreshToken = tokenResponse.getString("refresh_token")
            val user = JSONObject(
                request("${configuration.projectUrl}/auth/v1/user", bearerToken = accessToken),
            )
            AuthenticationResult.Authenticated(
                AuthenticatedAccount(user.getString("id"), accessToken, refreshToken),
            )
        }.getOrElse { AuthenticationResult.Failed(it.message ?: "Authentication validation failed") }
    }

    private fun request(
        url: String,
        method: String = "GET",
        body: String? = null,
        bearerToken: String? = null,
    ): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return connection.run {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("apikey", configuration.anonymousKey)
            setRequestProperty("Content-Type", "application/json")
            bearerToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray()) }
            }
            val response = (if (responseCode in 200..299) inputStream else errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (responseCode !in 200..299) error("Supabase Auth returned HTTP $responseCode")
            response
        }.also { connection.disconnect() }
    }

    private fun callbackValues(callbackUrl: String): Map<String, String> {
        val uri = Uri.parse(callbackUrl)
        val pairs = buildList {
            uri.queryParameterNames.forEach { name -> uri.getQueryParameter(name)?.let { add(name to it) } }
            uri.fragment?.split('&')?.forEach { part ->
                val pieces = part.split('=', limit = 2)
                if (pieces.size == 2) add(decode(pieces[0]) to decode(pieces[1]))
            }
        }
        return pairs.toMap()
    }

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    private fun decode(value: String) = URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    private companion object {
        const val CONFIGURATION_ERROR = "Supabase is not configured for this build"
    }
}

class SupabaseInitialMigrationRepository(
    private val configuration: SupabaseConfiguration,
) : CloudRepository {
    override suspend fun upsertInitialCopy(
        accountId: String,
        copy: InitialAccountCopy,
        accessToken: String,
    ) = withContext(Dispatchers.IO) {
        require(configuration.isConfigured) { "Supabase is not configured for this build" }
        val connection = URL("${configuration.projectUrl}/functions/v1/migrate-initial-copy").openConnection() as HttpURLConnection
        try {
            connection.run {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("apikey", configuration.anonymousKey)
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(copy.toJson(accountId).toString().toByteArray()) }
                val status = responseCode
                val responseBody = (if (status in 200..299) inputStream else errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status == HttpURLConnection.HTTP_UNAUTHORIZED) throw CloudAuthenticationExpiredException()
                if (status !in 200..299) error("Initial migration failed with HTTP $status: $responseBody")
            }
        } finally {
            connection.disconnect()
        }
    }
}

private fun InitialAccountCopy.toJson(accountId: String) = JSONObject()
    .put("accountId", accountId)
    .put("aquariums", JSONArray(aquariums.map { JSONObject().put("id", it.id).put("name", it.name).put("volume", it.volume).put("volumeUnit", it.volumeUnit.storageValue).put("createdAtEpochMillis", it.createdAtEpochMillis).put("profile", it.profile.storageValue) }))
    .put("parameterDefinitions", JSONArray(parameterDefinitions.map { JSONObject().put("id", it.id).put("aquariumId", it.aquariumId).put("parameter", it.parameter.storageValue).put("isActive", it.isActive).put("position", it.position).put("unit", it.unit).put("precision", it.precision).putNullable("indicativeMinimum", it.indicativeMinimum).putNullable("indicativeMaximum", it.indicativeMaximum) }))
    .put("sessions", JSONArray(sessions.map { JSONObject().put("id", it.id).put("aquariumId", it.aquariumId).put("occurredAtEpochMillis", it.occurredAtEpochMillis).put("createdAtEpochMillis", it.createdAtEpochMillis) }))
    .put("measurements", JSONArray(measurements.map { JSONObject().put("id", it.id).put("sessionId", it.sessionId).put("parameterDefinitionId", it.parameterDefinitionId).put("value", it.value) }))
    .put("maintenanceActions", JSONArray(maintenanceActions.map { JSONObject().put("id", it.id).put("sessionId", it.sessionId).put("type", it.type.storageValue).putNullable("quantity", it.quantity).putNullable("unit", it.unit).putNullable("product", it.product) }))
    .put("events", JSONArray(events.map { JSONObject().put("id", it.id).put("sessionId", it.sessionId).put("type", it.type.storageValue).put("note", it.note) }))

private fun JSONObject.putNullable(name: String, value: Any?): JSONObject = put(name, value ?: JSONObject.NULL)
