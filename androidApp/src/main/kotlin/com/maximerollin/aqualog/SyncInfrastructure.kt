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
import com.maximerollin.aqualog.shared.SyncConflictException
import com.maximerollin.aqualog.shared.SyncCoordinator
import com.maximerollin.aqualog.shared.SyncEntityType
import com.maximerollin.aqualog.shared.SyncNetworkException
import com.maximerollin.aqualog.shared.RemoteEntityVersion
import com.maximerollin.aqualog.shared.RemoteSyncSnapshot
import com.maximerollin.aqualog.shared.SyncRunResult
import com.maximerollin.aqualog.shared.SyncServerException
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
import java.io.IOException
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
            .sortedBy { it.id }
            .forEach { operation ->
                val payload = when (operation.entityType) {
                    SyncEntityType.AQUARIUM -> batch.aquariums.firstOrNull { it.id == operation.entityId }
                        ?.toRemoteJson(session.accountId)
                    SyncEntityType.PARAMETER_DEFINITION -> batch.parameterDefinitions
                        .firstOrNull { it.id == operation.entityId }?.toRemoteJson(session.accountId)
                    SyncEntityType.SESSION -> batch.sessions.firstOrNull { it.id == operation.entityId }
                        ?.toRemoteJson(session.accountId)
                    SyncEntityType.MEASUREMENT -> batch.measurements.firstOrNull { it.id == operation.entityId }
                        ?.toRemoteJson(session.accountId)
                    SyncEntityType.MAINTENANCE_ACTION -> batch.maintenanceActions
                        .firstOrNull { it.id == operation.entityId }?.toRemoteJson(session.accountId)
                    SyncEntityType.EVENT -> batch.events.firstOrNull { it.id == operation.entityId }
                        ?.toRemoteJson(session.accountId)
                }
                require(operation.kind == SyncOperationKind.DELETE || payload != null) {
                    "Pending upsert payload is missing"
                }
                request(
                    path = "/rest/v1/rpc/apply_sync_operation",
                    method = "POST",
                    accessToken = session.accessToken,
                    body = JSONObject()
                        .put("p_operation_key", operation.operationKey)
                        .put("p_entity_type", operation.entityType.storageValue)
                        .put("p_entity_id", operation.entityId)
                        .put("p_kind", operation.kind.storageValue)
                        .put("p_client_updated_at_epoch_millis", operation.clientUpdatedAtEpochMillis)
                        .put("p_payload", payload ?: JSONObject.NULL)
                        .toString(),
                    prefer = "return=minimal",
                )
            }
    }

    override suspend fun pull(session: SyncSession): InitialAccountCopy = pullVersioned(session).copy

    override suspend fun pullVersioned(session: SyncSession): RemoteSyncSnapshot = withContext(Dispatchers.IO) {
        require(configuration.isConfigured) { "Supabase is not configured for this build" }
        val aquariums = get("aquariums", session.accessToken)
        val parameters = get("parameter_definitions", session.accessToken)
        val sessions = get("sessions", session.accessToken)
        val measurements = get("measurements", session.accessToken)
        val actions = get("maintenance_actions", session.accessToken)
        val events = get("session_events", session.accessToken)
        RemoteSyncSnapshot(
            copy = InitialAccountCopy(
            aquariums = aquariums.filterLive().map { value ->
                Aquarium(
                    id = value.getString("id"),
                    name = value.getString("name"),
                    volume = value.getDouble("volume"),
                    volumeUnit = VolumeUnit.fromStorageValue(value.getString("volume_unit")),
                    createdAtEpochMillis = value.getLong("created_at_epoch_millis"),
                    profile = AquariumProfile.fromStorageValue(value.getString("profile")),
                )
            },
            parameterDefinitions = parameters.filterLive().map { value ->
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
            sessions = sessions.filterLive().map { value ->
                Session(
                    id = value.getString("id"),
                    aquariumId = value.getString("aquarium_id"),
                    occurredAtEpochMillis = value.getLong("occurred_at_epoch_millis"),
                    createdAtEpochMillis = value.getLong("created_at_epoch_millis"),
                )
            },
            measurements = measurements.filterLive().map { value ->
                Measurement(
                    id = value.getString("id"),
                    sessionId = value.getString("session_id"),
                    parameterDefinitionId = value.getString("parameter_definition_id"),
                    value = value.getDouble("value"),
                )
            },
            maintenanceActions = actions.filterLive().map { value ->
                MaintenanceAction(
                    id = value.getString("id"),
                    sessionId = value.getString("session_id"),
                    type = MaintenanceActionType.fromStorageValue(value.getString("type")),
                    quantity = value.nullableDouble("quantity"),
                    unit = value.nullableString("unit"),
                    product = value.nullableString("product"),
                )
            },
            events = events.filterLive().map { value ->
                SessionEvent(
                    id = value.getString("id"),
                    sessionId = value.getString("session_id"),
                    type = EventType.fromStorageValue(value.getString("type")),
                    note = value.getString("note"),
                )
            },
            ),
            versions = buildList {
                addAll(aquariums.toVersions(SyncEntityType.AQUARIUM))
                addAll(parameters.toVersions(SyncEntityType.PARAMETER_DEFINITION))
                addAll(sessions.toVersions(SyncEntityType.SESSION))
                addAll(measurements.toVersions(SyncEntityType.MEASUREMENT))
                addAll(actions.toVersions(SyncEntityType.MAINTENANCE_ACTION))
                addAll(events.toVersions(SyncEntityType.EVENT))
            },
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
            if (status == HttpURLConnection.HTTP_CONFLICT) {
                throw SyncConflictException("Supabase rejected a concurrent write (HTTP $status)")
            }
            if (status !in 200..299) throw SyncServerException("Supabase sync failed (HTTP $status)")
            response
        } catch (network: IOException) {
            throw SyncNetworkException(network.message?.take(160) ?: "Network request failed")
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

private fun List<JSONObject>.filterLive() = filter { it.isNull("deleted_at_epoch_millis") }

private fun List<JSONObject>.toVersions(entityType: SyncEntityType) = map { value ->
    RemoteEntityVersion(
        entityType = entityType,
        entityId = value.getString("id"),
        serverVersion = value.getLong("server_version"),
        serverUpdatedAtEpochMillis = value.getLong("server_updated_at_epoch_millis"),
        deletedAtEpochMillis = if (value.isNull("deleted_at_epoch_millis")) {
            null
        } else {
            value.getLong("deleted_at_epoch_millis")
        },
    )
}
