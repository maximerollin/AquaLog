package com.maximerollin.aqualog

import android.content.Context
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
import com.maximerollin.aqualog.shared.Inhabitant
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
import com.maximerollin.aqualog.shared.AccountOperationGate
import com.maximerollin.aqualog.shared.AccountLeaseResult
import com.maximerollin.aqualog.shared.SyncEntityType
import com.maximerollin.aqualog.shared.SyncNetworkException
import com.maximerollin.aqualog.shared.RemoteEntityVersion
import com.maximerollin.aqualog.shared.RemoteSyncSnapshot
import com.maximerollin.aqualog.shared.ScheduledSyncExecution
import com.maximerollin.aqualog.shared.SyncServerException
import com.maximerollin.aqualog.shared.SyncSession
import com.maximerollin.aqualog.shared.SyncSessionProvider
import com.maximerollin.aqualog.shared.SyncOperation
import com.maximerollin.aqualog.shared.SyncOperationKind
import com.maximerollin.aqualog.shared.SyncWorkScheduler
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.encyclopedia.DrawingFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException
import java.util.concurrent.TimeUnit

class LocalAccountSyncSessionProvider(
    private val localData: AccountLocalDataSource,
    private val tokenStorage: SecureTokenStorage,
    private val authGateway: AuthGateway,
    private val operationGate: AccountOperationGate? = null,
) : SyncSessionProvider {
    override suspend fun currentSession(): SyncSession? {
        val account = localData.accountMigrationState()
            ?.takeIf { it.status == InitialMigrationStatus.COMPLETE }
            ?: return null
        val tokens = tokenStorage.read() ?: return null
        return SyncSession(account.accountId, tokens.accessToken)
    }

    override suspend fun refreshSession(expiredSession: SyncSession): SyncSession? {
        val lease = operationGate?.acquire()
        if (operationGate != null && lease == null) return null
        val refreshToken = tokenStorage.read()?.refreshToken ?: return null
        val refreshed = authGateway.refreshSession(refreshToken) as? AuthenticationResult.Authenticated ?: return null
        if (refreshed.account.accountId != expiredSession.accountId) return null
        if (operationGate != null) {
            val saved = operationGate.runIfCurrent(requireNotNull(lease)) {
                tokenStorage.save(AuthTokens(refreshed.account.accessToken, refreshed.account.refreshToken))
            }
            if (saved is AccountLeaseResult.Revoked) return null
        } else {
            tokenStorage.save(AuthTokens(refreshed.account.accessToken, refreshed.account.refreshToken))
        }
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
        batch.operations.inPushOrder()
            .forEach { operation ->
                val payload = when (operation.entityType) {
                    SyncEntityType.AQUARIUM -> batch.aquariums.firstOrNull { it.id == operation.entityId }
                        ?.toRemoteJson(session.accountId, withPhotoId = operation.entityId in batch.photoAquariumIds)
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
                    SyncEntityType.INHABITANT -> batch.inhabitants.firstOrNull { it.id == operation.entityId }
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
                        .put("p_base_server_version", operation.baseServerVersion)
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
        val inhabitants = get("inhabitants", session.accessToken).withKnownFamily()
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
                    isArchived = value.optBoolean("is_archived", false),
                    photoId = value.nullableString("photo_id"),
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
                    analysisIntervalDays = value.nullableInt("analysis_interval_days"),
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
            inhabitants = inhabitants.filterLive().map { it.toInhabitant() },
            ),
            versions = buildList {
                addAll(aquariums.toVersions(SyncEntityType.AQUARIUM))
                addAll(parameters.toVersions(SyncEntityType.PARAMETER_DEFINITION))
                addAll(sessions.toVersions(SyncEntityType.SESSION))
                addAll(measurements.toVersions(SyncEntityType.MEASUREMENT))
                addAll(actions.toVersions(SyncEntityType.MAINTENANCE_ACTION))
                addAll(events.toVersions(SyncEntityType.EVENT))
                addAll(inhabitants.toVersions(SyncEntityType.INHABITANT))
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

    override suspend fun uploadAquariumPhoto(session: SyncSession, aquariumId: String, jpeg: ByteArray) {
        withContext(Dispatchers.IO) {
            requestBytes(
                path = photoPath(session, aquariumId, authenticated = false),
                method = "POST",
                accessToken = session.accessToken,
                body = jpeg,
                contentType = "image/jpeg",
                upsert = true,
                storage = true,
            )
        }
    }

    // A missing object is already deleted: failing here would block every later sync.
    override suspend fun deleteAquariumPhoto(session: SyncSession, aquariumId: String) {
        withContext(Dispatchers.IO) {
            requestBytes(
                path = photoPath(session, aquariumId, authenticated = false),
                method = "DELETE",
                accessToken = session.accessToken,
                storage = true,
                missingIsNull = true,
            )
        }
    }

    override suspend fun downloadAquariumPhoto(session: SyncSession, aquariumId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            requestBytes(
                path = photoPath(session, aquariumId, authenticated = true),
                method = "GET",
                accessToken = session.accessToken,
                storage = true,
                missingIsNull = true,
            )
        }

    private fun photoPath(session: SyncSession, aquariumId: String, authenticated: Boolean): String {
        require(configuration.isConfigured) { "Supabase is not configured for this build" }
        return "/storage/v1/object/${if (authenticated) "authenticated/" else ""}aquarium-photos/" +
            "${session.accountId}/$aquariumId.jpg"
    }

    private fun request(
        path: String,
        method: String,
        accessToken: String,
        body: String? = null,
        prefer: String? = null,
    ): String = requestBytes(path, method, accessToken, body?.toByteArray(), prefer = prefer)
        ?.toString(Charsets.UTF_8).orEmpty()

    /** The single HTTP helper. A [storage] failure is classified by [classifyStorageFailure]; a missing object returns null if [missingIsNull]. */
    private fun requestBytes(
        path: String,
        method: String,
        accessToken: String,
        body: ByteArray? = null,
        contentType: String = "application/json",
        prefer: String? = null,
        upsert: Boolean = false,
        storage: Boolean = false,
        missingIsNull: Boolean = false,
    ): ByteArray? {
        val connection = URL("${configuration.projectUrl}$path").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 20_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("apikey", configuration.anonymousKey)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            prefer?.let { connection.setRequestProperty("Prefer", it) }
            if (upsert) connection.setRequestProperty("x-upsert", "true")
            if (body != null) {
                // Storage answers 400 to a Content-Type without a body, so it is only sent with one.
                connection.setRequestProperty("Content-Type", contentType)
                connection.doOutput = true
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.use { it.readBytes() } ?: ByteArray(0)
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED) throw CloudAuthenticationExpiredException()
            if (status == HttpURLConnection.HTTP_CONFLICT) {
                throw SyncConflictException("Supabase rejected a concurrent write (HTTP $status)")
            }
            if (storage && status !in 200..299) {
                when (classifyStorageFailure(status, response.toString(Charsets.UTF_8))) {
                    StorageFailure.MISSING -> if (missingIsNull) return null
                    StorageFailure.AUTH_EXPIRED -> throw CloudAuthenticationExpiredException()
                    StorageFailure.OTHER -> {}
                }
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

/**
 * The latest operation per entity, ordered by when the entity was first touched: a later update must not
 * move an Aquarium's insert after the rows that reference it.
 */
internal fun List<SyncOperation>.inPushOrder(): List<SyncOperation> =
    groupBy { it.entityType to it.entityId }.values
        .sortedBy { operations -> operations.minOf { it.id } }
        .map { operations -> operations.maxBy { it.id } }

internal enum class StorageFailure { MISSING, AUTH_EXPIRED, OTHER }

/**
 * Storage answers HTTP 400 to most client errors and carries the real code in the JSON body, e.g. an expired
 * token is `{"statusCode":"403","error":"Unauthorized",...}` and a missing object `{"statusCode":"404","error":"not_found",...}`.
 */
internal fun classifyStorageFailure(status: Int, body: String): StorageFailure {
    if (status == HttpURLConnection.HTTP_NOT_FOUND) return StorageFailure.MISSING
    if (status == HttpURLConnection.HTTP_UNAUTHORIZED) return StorageFailure.AUTH_EXPIRED
    if (status != HttpURLConnection.HTTP_BAD_REQUEST) return StorageFailure.OTHER
    val json = try { JSONObject(body) } catch (_: JSONException) { return StorageFailure.OTHER }
    val code = json.optString("statusCode")
    val error = json.optString("error")
    return when {
        code == "404" || error == "not_found" || error == "NoSuchKey" -> StorageFailure.MISSING
        // A row-level-security refusal is not an expired token: a new token would be refused too.
        json.optString("message").contains("row-level security", ignoreCase = true) -> StorageFailure.OTHER
        code == "401" || code == "403" || error == "InvalidJWT" -> StorageFailure.AUTH_EXPIRED
        else -> StorageFailure.OTHER
    }
}

class AquaLogSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val application = applicationContext as AquaLogApplication
        ScheduledSyncExecution(
            coordinator = application.syncCoordinator,
            scheduler = AndroidSyncWorkScheduler(applicationContext),
            currentTimeMillis = System::currentTimeMillis,
        ).runOnce()
        return Result.success()
    }
}

class AquaLogSyncDeadlineWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        AndroidSyncScheduler.appendDrain(applicationContext)
        return Result.success()
    }
}

private class AndroidSyncWorkScheduler(
    private val context: Context,
) : SyncWorkScheduler {
    override fun appendImmediate() = AndroidSyncScheduler.appendDrain(context)

    override fun replaceDeadline(deadlineEpochMillis: Long) =
        AndroidSyncScheduler.replaceDeadline(context, deadlineEpochMillis)

    override fun cancelDeadline() = AndroidSyncScheduler.cancelDeadline(context)
}

object AndroidSyncScheduler {
    private const val drainWorkName = "aqualog-entity-sync-drain"
    private const val deadlineWorkName = "aqualog-entity-sync-deadline"

    fun requestImmediate(context: Context) {
        cancelDeadline(context)
        appendDrain(context)
    }

    internal fun appendDrain(context: Context) {
        val request = OneTimeWorkRequestBuilder<AquaLogSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(drainWorkName, ExistingWorkPolicy.APPEND, request)
    }

    internal fun replaceDeadline(context: Context, deadlineEpochMillis: Long) {
        val delayMillis = (deadlineEpochMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<AquaLogSyncDeadlineWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(deadlineWorkName, ExistingWorkPolicy.REPLACE, request)
    }

    internal fun cancelDeadline(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(deadlineWorkName)
    }
}

/** Without [withPhotoId] the key is absent and the server keeps its photo_id: a stale one never overrides it. */
internal fun Aquarium.toRemoteJson(accountId: String, withPhotoId: Boolean) = JSONObject()
    .put("id", id).put("account_id", accountId).put("name", name).put("volume", volume)
    .put("volume_unit", volumeUnit.storageValue).put("created_at_epoch_millis", createdAtEpochMillis)
    .put("profile", profile.storageValue).put("is_archived", isArchived)
    .apply { if (withPhotoId) putNullable("photo_id", photoId) }

private fun ParameterDefinition.toRemoteJson(accountId: String) = JSONObject()
    .put("id", id).put("account_id", accountId).put("aquarium_id", aquariumId)
    .put("parameter", parameter.storageValue).put("is_active", isActive).put("position", position)
    .put("unit", unit).put("precision", precision).putNullable("indicative_minimum", indicativeMinimum)
    .putNullable("indicative_maximum", indicativeMaximum).putNullable("analysis_interval_days", analysisIntervalDays)

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

internal fun Inhabitant.toRemoteJson(accountId: String) = JSONObject()
    .put("id", id).put("account_id", accountId).put("aquarium_id", aquariumId)
    .putNullable("species_id", speciesId).putNullable("custom_name", customName).put("family", family.name)
    .put("count", count).put("added_at_epoch_millis", addedAtEpochMillis)
    .putNullable("removed_at_epoch_millis", removedAtEpochMillis)

/** A family this build does not know (written by a newer one) is skipped, row and version, until an update knows it. */
internal fun List<JSONObject>.withKnownFamily() =
    filter { row -> DrawingFamily.entries.any { it.name == row.optString("family") } }

internal fun JSONObject.toInhabitant() = Inhabitant(
    id = getString("id"),
    aquariumId = getString("aquarium_id"),
    speciesId = nullableString("species_id"),
    customName = nullableString("custom_name"),
    family = DrawingFamily.valueOf(getString("family")),
    count = getInt("count"),
    addedAtEpochMillis = getLong("added_at_epoch_millis"),
    removedAtEpochMillis = if (isNull("removed_at_epoch_millis")) null else getLong("removed_at_epoch_millis"),
)

private fun JSONObject.putNullable(name: String, value: Any?): JSONObject = put(name, value ?: JSONObject.NULL)
private fun JSONObject.nullableDouble(name: String): Double? = if (isNull(name)) null else getDouble(name)
// isNull is also true for a missing key, so rows from a server without the column read as null.
private fun JSONObject.nullableInt(name: String): Int? = if (isNull(name)) null else getInt(name)
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
