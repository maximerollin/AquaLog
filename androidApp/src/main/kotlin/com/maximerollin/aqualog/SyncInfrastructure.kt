package com.maximerollin.aqualog

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.maximerollin.aqualog.shared.AccountLocalDataSource
import com.maximerollin.aqualog.shared.AuthGateway
import com.maximerollin.aqualog.shared.AuthTokens
import com.maximerollin.aqualog.shared.AuthenticationResult
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.CloudAuthenticationExpiredException
import com.maximerollin.aqualog.shared.EventType
import com.maximerollin.aqualog.shared.InitialAccountCopy
import com.maximerollin.aqualog.shared.InitialMigrationStatus
import com.maximerollin.aqualog.shared.MaintenanceAction
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.Measurement
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.PendingSyncBatch
import com.maximerollin.aqualog.shared.SecureTokenStorage
import com.maximerollin.aqualog.shared.Session
import com.maximerollin.aqualog.shared.SessionEvent
import com.maximerollin.aqualog.shared.SyncBackend
import com.maximerollin.aqualog.shared.SyncCoordinator
import com.maximerollin.aqualog.shared.SyncRunResult
import com.maximerollin.aqualog.shared.SyncSession
import com.maximerollin.aqualog.shared.SyncSessionProvider
import com.maximerollin.aqualog.shared.SyncOperationKind
import com.maximerollin.aqualog.shared.VolumeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class LocalAccountSyncSessionProvider(
    private val localData: AccountLocalDataSource,
    private val tokenStorage: SecureTokenStorage,
    private val authGateway: AuthGateway,
) : SyncSessionProvider {
    override suspend fun currentSession(): SyncSession? {
        val account = localData.accountMigrationState()
            ?.takeIf { it.status == InitialMigrationStatus.COMPLETE }
            ?: return null
        val tokens = tokenStorage.read() ?: return null
        return SyncSession(account.accountId, tokens.accessToken)
    }

    override suspend fun refreshSession(expiredSession: SyncSession): SyncSession? {
        val refreshToken = tokenStorage.read()?.refreshToken ?: return null
        val refreshed = authGateway.refreshSession(refreshToken) as? AuthenticationResult.Authenticated ?: return null
        if (refreshed.account.accountId != expiredSession.accountId) return null
        tokenStorage.save(AuthTokens(refreshed.account.accessToken, refreshed.account.refreshToken))
        return SyncSession(refreshed.account.accountId, refreshed.account.accessToken)
    }

    override suspend fun requireReauthentication() {
        tokenStorage.clear()
    }
}

class SupabaseSyncBackend(
    private val configuration: SupabaseConfiguration,
) : SyncBackend {
    override suspend fun push(session: SyncSession, batch: PendingSyncBatch) = withContext(Dispatchers.IO) {
        require(configuration.isConfigured) { "Supabase is not configured for this build" }
        batch.operations.groupBy { it.entityType to it.entityId }
            .mapValues { (_, operations) -> operations.maxBy { it.id } }
            .values
            .filter { it.kind == SyncOperationKind.DELETE }
            .forEach { operation -> delete(operation.entityType.remoteTable, operation.entityId, session.accessToken) }
        upsert("aquariums", batch.aquariums.map { it.toRemoteJson(session.accountId) }, session.accessToken)
        upsert(
            "parameter_definitions",
            batch.parameterDefinitions.map { it.toRemoteJson(session.accountId) },
            session.accessToken,
        )
        upsert("sessions", batch.sessions.map { it.toRemoteJson(session.accountId) }, session.accessToken)
        upsert("measurements", batch.measurements.map { it.toRemoteJson(session.accountId) }, session.accessToken)
        upsert(
            "maintenance_actions",
            batch.maintenanceActions.map { it.toRemoteJson(session.accountId) },
            session.accessToken,
        )
        upsert("session_events", batch.events.map { it.toRemoteJson(session.accountId) }, session.accessToken)
    }

    override suspend fun pull(session: SyncSession): InitialAccountCopy = withContext(Dispatchers.IO) {
        require(configuration.isConfigured) { "Supabase is not configured for this build" }
        InitialAccountCopy(
            aquariums = get("aquariums", session.accessToken).map { value ->
                Aquarium(
                    id = value.getString("id"),
                    name = value.getString("name"),
                    volume = value.getDouble("volume"),
                    volumeUnit = VolumeUnit.fromStorageValue(value.getString("volume_unit")),
                    createdAtEpochMillis = value.getLong("created_at_epoch_millis"),
                    profile = AquariumProfile.fromStorageValue(value.getString("profile")),
                )
            },
            parameterDefinitions = get("parameter_definitions", session.accessToken).map { value ->
                ParameterDefinition(
                    id = value.getString("id"),
                    aquariumId = value.getString("aquarium_id"),
                    parameter = BuiltInParameter.fromStorageValue(value.getString("parameter")),
                    isActive = value.getBoolean("is_active"),
                    position = value.getInt("position"),
                    unit = value.getString("unit"),
                    precision = value.getInt("precision"),
                    indicativeMinimum = value.nullableDouble("indicative_minimum"),
                    indicativeMaximum = value.nullableDouble("indicative_maximum"),
                )
            },
            sessions = get("sessions", session.accessToken).map { value ->
                Session(
                    id = value.getString("id"),
                    aquariumId = value.getString("aquarium_id"),
                    occurredAtEpochMillis = value.getLong("occurred_at_epoch_millis"),
                    createdAtEpochMillis = value.getLong("created_at_epoch_millis"),
                )
            },
            measurements = get("measurements", session.accessToken).map { value ->
                Measurement(
                    id = value.getString("id"),
                    sessionId = value.getString("session_id"),
                    parameterDefinitionId = value.getString("parameter_definition_id"),
                    value = value.getDouble("value"),
                )
            },
            maintenanceActions = get("maintenance_actions", session.accessToken).map { value ->
                MaintenanceAction(
                    id = value.getString("id"),
                    sessionId = value.getString("session_id"),
                    type = MaintenanceActionType.fromStorageValue(value.getString("type")),
                    quantity = value.nullableDouble("quantity"),
                    unit = value.nullableString("unit"),
                    product = value.nullableString("product"),
                )
            },
            events = get("session_events", session.accessToken).map { value ->
                SessionEvent(
                    id = value.getString("id"),
                    sessionId = value.getString("session_id"),
                    type = EventType.fromStorageValue(value.getString("type")),
                    note = value.getString("note"),
                )
            },
        )
    }

    private fun upsert(table: String, rows: List<JSONObject>, accessToken: String) {
        if (rows.isEmpty()) return
        request(
            path = "/rest/v1/$table?on_conflict=id",
            method = "POST",
            accessToken = accessToken,
            body = JSONArray(rows).toString(),
            prefer = "resolution=merge-duplicates,return=minimal",
        )
    }

    private fun get(table: String, accessToken: String): List<JSONObject> {
        val values = JSONArray(
            request(
                path = "/rest/v1/$table?select=*",
                method = "GET",
                accessToken = accessToken,
            ),
        )
        return List(values.length()) { index -> values.getJSONObject(index) }
    }

    private fun delete(table: String, entityId: String, accessToken: String) {
        request(
            path = "/rest/v1/$table?id=eq.$entityId",
            method = "DELETE",
            accessToken = accessToken,
            prefer = "return=minimal",
        )
    }

    private fun request(
        path: String,
        method: String,
        accessToken: String,
        body: String? = null,
        prefer: String? = null,
    ): String {
        val connection = URL("${configuration.projectUrl}$path").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 20_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("apikey", configuration.anonymousKey)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            prefer?.let { connection.setRequestProperty("Prefer", it) }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED) throw CloudAuthenticationExpiredException()
            if (status !in 200..299) error("Supabase sync returned HTTP $status: $response")
            response
        } finally {
            connection.disconnect()
        }
    }
}

class AquaLogSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val application = applicationContext as AquaLogApplication
        return when (application.syncCoordinator.runOnce()) {
            is SyncRunResult.Retry -> Result.retry()
            SyncRunResult.SignedOut,
            is SyncRunResult.Synchronized,
            -> Result.success()
        }
    }
}

object AndroidSyncScheduler {
    private const val workName = "aqualog-entity-sync"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<AquaLogSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}

private fun Aquarium.toRemoteJson(accountId: String) = JSONObject()
    .put("id", id).put("account_id", accountId).put("name", name).put("volume", volume)
    .put("volume_unit", volumeUnit.storageValue).put("created_at_epoch_millis", createdAtEpochMillis)
    .put("profile", profile.storageValue)

private fun ParameterDefinition.toRemoteJson(accountId: String) = JSONObject()
    .put("id", id).put("account_id", accountId).put("aquarium_id", aquariumId)
    .put("parameter", parameter.storageValue).put("is_active", isActive).put("position", position)
    .put("unit", unit).put("precision", precision).putNullable("indicative_minimum", indicativeMinimum)
    .putNullable("indicative_maximum", indicativeMaximum)

private fun Session.toRemoteJson(accountId: String) = JSONObject()
    .put("id", id).put("account_id", accountId).put("aquarium_id", aquariumId)
    .put("occurred_at_epoch_millis", occurredAtEpochMillis).put("created_at_epoch_millis", createdAtEpochMillis)

private fun Measurement.toRemoteJson(accountId: String) = JSONObject()
    .put("id", id).put("account_id", accountId).put("session_id", sessionId)
    .put("parameter_definition_id", parameterDefinitionId).put("value", value)

private fun MaintenanceAction.toRemoteJson(accountId: String) = JSONObject()
    .put("id", id).put("account_id", accountId).put("session_id", sessionId).put("type", type.storageValue)
    .putNullable("quantity", quantity).putNullable("unit", unit).putNullable("product", product)

private fun SessionEvent.toRemoteJson(accountId: String) = JSONObject()
    .put("id", id).put("account_id", accountId).put("session_id", sessionId)
    .put("type", type.storageValue).put("note", note)

private fun JSONObject.putNullable(name: String, value: Any?): JSONObject = put(name, value ?: JSONObject.NULL)
private fun JSONObject.nullableDouble(name: String): Double? = if (isNull(name)) null else getDouble(name)
private fun JSONObject.nullableString(name: String): String? = if (isNull(name)) null else getString(name)

private val com.maximerollin.aqualog.shared.SyncEntityType.remoteTable: String
    get() = when (this) {
        com.maximerollin.aqualog.shared.SyncEntityType.AQUARIUM -> "aquariums"
        com.maximerollin.aqualog.shared.SyncEntityType.PARAMETER_DEFINITION -> "parameter_definitions"
        com.maximerollin.aqualog.shared.SyncEntityType.SESSION -> "sessions"
        com.maximerollin.aqualog.shared.SyncEntityType.MEASUREMENT -> "measurements"
        com.maximerollin.aqualog.shared.SyncEntityType.MAINTENANCE_ACTION -> "maintenance_actions"
        com.maximerollin.aqualog.shared.SyncEntityType.EVENT -> "session_events"
    }
