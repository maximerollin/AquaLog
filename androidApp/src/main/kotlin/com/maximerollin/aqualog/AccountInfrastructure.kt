package com.maximerollin.aqualog

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.maximerollin.aqualog.shared.AuthGateway
import com.maximerollin.aqualog.shared.AuthTokens
import com.maximerollin.aqualog.shared.AuthenticatedAccount
import com.maximerollin.aqualog.shared.AuthenticationResult
import com.maximerollin.aqualog.shared.CloudRepository
import com.maximerollin.aqualog.shared.InitialAccountCopy
import com.maximerollin.aqualog.shared.MagicLinkRequestResult
import com.maximerollin.aqualog.shared.SecureTokenStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyStore
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

class AndroidSecureTokenStorage(context: Context) : SecureTokenStorage {
    private val preferences = context.getSharedPreferences("encrypted_account_tokens", Context.MODE_PRIVATE)

    override suspend fun save(tokens: AuthTokens) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val plaintext = "${tokens.accessToken}\u0000${tokens.refreshToken}".toByteArray()
        val ciphertext = cipher.doFinal(plaintext)
        preferences.edit()
            .putString(IV_KEY, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(TOKEN_KEY, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .apply()
    }

    override suspend fun read(): AuthTokens? = withContext(Dispatchers.IO) {
        val iv = preferences.getString(IV_KEY, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return@withContext null
        val encrypted = preferences.getString(TOKEN_KEY, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
            ?: return@withContext null
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            val parts = cipher.doFinal(encrypted).toString(Charsets.UTF_8).split('\u0000', limit = 2)
            AuthTokens(parts[0], parts[1])
        }.getOrNull()
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        preferences.edit().clear().apply()
    }

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
        const val IV_KEY = "iv"
        const val TOKEN_KEY = "tokens"
    }
}

class SupabaseAuthGateway(
    private val context: Context,
    private val configuration: SupabaseConfiguration,
) : AuthGateway {
    override suspend fun signInWithGoogle(): AuthenticationResult {
        if (!configuration.isConfigured) return AuthenticationResult.Failed(CONFIGURATION_ERROR)
        val uri = Uri.parse("${configuration.projectUrl}/auth/v1/authorize").buildUpon()
            .appendQueryParameter("provider", "google")
            .appendQueryParameter("redirect_to", configuration.redirectUrl)
            .build()
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return AuthenticationResult.AwaitingCallback
    }

    override suspend fun requestMagicLink(email: String): MagicLinkRequestResult {
        if (!configuration.isConfigured) return MagicLinkRequestResult.Failed(CONFIGURATION_ERROR)
        return withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject()
                    .put("email", email)
                    .put("create_user", true)
                request(
                    url = "${configuration.projectUrl}/auth/v1/otp?redirect_to=${encode(configuration.redirectUrl)}",
                    method = "POST",
                    body = body.toString(),
                )
                MagicLinkRequestResult.Sent
            }.getOrElse { MagicLinkRequestResult.Failed(it.message ?: "Magic link request failed") }
        }
    }

    override suspend fun completeMagicLink(callbackUrl: String): AuthenticationResult {
        if (!configuration.isConfigured) return AuthenticationResult.Failed(CONFIGURATION_ERROR)
        val values = callbackValues(callbackUrl)
        return when {
            values["error_code"] == "otp_expired" -> AuthenticationResult.ExpiredLink
            values["error"] == "access_denied" -> AuthenticationResult.Cancelled
            values["error_description"] != null -> AuthenticationResult.Failed(values.getValue("error_description"))
            else -> {
                val accessToken = values["access_token"]
                    ?: return AuthenticationResult.Failed("The authentication callback contains no access token")
                val refreshToken = values["refresh_token"]
                    ?: return AuthenticationResult.Failed("The authentication callback contains no refresh token")
                withContext(Dispatchers.IO) {
                    runCatching {
                        val user = JSONObject(
                            request("${configuration.projectUrl}/auth/v1/user", bearerToken = accessToken),
                        )
                        AuthenticationResult.Authenticated(
                            AuthenticatedAccount(user.getString("id"), accessToken, refreshToken),
                        )
                    }.getOrElse { AuthenticationResult.Failed(it.message ?: "Authentication validation failed") }
                }
            }
        }
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
    private val tokenStorage: SecureTokenStorage,
) : CloudRepository {
    override suspend fun upsertInitialCopy(accountId: String, copy: InitialAccountCopy) = withContext(Dispatchers.IO) {
        require(configuration.isConfigured) { "Supabase is not configured for this build" }
        val accessToken = requireNotNull(tokenStorage.read()?.accessToken) { "No authenticated Supabase session" }
        val connection = URL("${configuration.projectUrl}/functions/v1/migrate-initial-copy").openConnection() as HttpURLConnection
        connection.run {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("apikey", configuration.anonymousKey)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
            outputStream.use { it.write(copy.toJson(accountId).toString().toByteArray()) }
            val responseBody = (if (responseCode in 200..299) inputStream else errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (responseCode !in 200..299) error("Initial migration failed with HTTP $responseCode: $responseBody")
            disconnect()
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
