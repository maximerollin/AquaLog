package com.maximerollin.aqualog.shared

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Historical schemas are inputs, rather than a current database relabelled as an older version. */
@RunWith(Parameterized::class)
class HistoricalMigrationQualificationTest(private val version: Int) {
    @Test
    fun `historical copy keeps its Session and pending work after upgrade and reopen`() = runTest {
        val directory = Files.createTempDirectory("aqualog-19-schema-$version-")
        val path = directory.resolve("historical.db").toString()
        try {
            createHistoricalCopy(path)
            repeat(2) {
                val database = createAquaLogDatabase(createJvmDatabaseBuilder(path))
                try {
                    val repository = RoomAquariumRepository(
                        database,
                        { error("Reading migrated data must not create identities") },
                        { 1_700_000_100_000L },
                    )
                    val aquarium = assertNotNull(repository.observeCurrentAquarium().first())
                    assertEquals("Historical Aquarium", aquarium.name)
                    assertEquals(false, aquarium.isArchived)
                    val session = assertNotNull(repository.observeLatestSession(aquarium.id).first())
                    assertEquals("session-19", session.session.id)
                    assertEquals(1, repository.sessionCount(aquarium.id))
                    assertEquals(24.5, session.measurements.single().value)
                    assertEquals(MaintenanceActionType.WATER_CHANGE, session.maintenanceActions.single().type)
                    assertEquals(20.0, session.maintenanceActions.single().quantity)
                    assertEquals("Historical observation", session.events.single().note)
                    assertEquals("session-19", repository.observeTimeline(TimelineFilter()).first().single().session.id)
                    if (version >= 5) {
                        assertEquals("account-19", repository.accountMigrationState()?.accountId)
                    }
                    if (version >= 6) {
                        assertEquals("Clean filter", repository.observePendingTaskOccurrences().first().single().title)
                        assertEquals(
                            TaskResolution.COMPLETED,
                            repository.observeResolvedTaskOccurrences().first().single().resolution,
                        )
                    }
                    if (version >= 7) {
                        val pending = repository.pendingSyncBatch().operations.single()
                        assertEquals("session-19", pending.entityId)
                        assertEquals(SyncEntityType.SESSION, pending.entityType)
                        assertEquals(2, pending.attemptCount)
                    }
                } finally {
                    database.close()
                }
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun createHistoricalCopy(path: String) {
        val schema = Json.parseToJsonElement(
            Files.readString(Path.of("schemas/com.maximerollin.aqualog.shared.AquaLogDatabase/$version.json")),
        ).jsonObject.getValue("database").jsonObject
        BundledSQLiteDriver().open(path).use { connection ->
            schema.getValue("entities").jsonArray.forEach { element ->
                val entity = element.jsonObject
                val table = entity.getValue("tableName").jsonPrimitive.content
                fun sql(template: String) = template.replace("\${TABLE_NAME}", table)
                connection.execSQL(sql(entity.getValue("createSql").jsonPrimitive.content))
                entity["indices"]?.jsonArray?.forEach { index ->
                    connection.execSQL(sql(index.jsonObject.getValue("createSql").jsonPrimitive.content))
                }
                fixtureRows(table).forEach { row ->
                    val fields = entity.getValue("fields").jsonArray.map {
                        it.jsonObject.getValue("columnName").jsonPrimitive.content
                    }
                    val present = row.filterKeys(fields::contains)
                    connection.execSQL(
                        "INSERT INTO `$table` (${present.keys.joinToString(",") { "`$it`" }}) " +
                            "VALUES (${present.values.joinToString(",")})",
                    )
                }
            }
            schema.getValue("setupQueries").jsonArray.forEach {
                connection.execSQL(it.jsonPrimitive.content)
            }
            connection.execSQL("PRAGMA user_version = $version")
        }
    }

    private fun fixtureRows(table: String): List<Map<String, String>> = when (table) {
        "aquariums" -> listOf(mapOf(
            "id" to "'aquarium-19'", "name" to "'Historical Aquarium'", "volume" to "120.0",
            "volumeUnit" to "'liters'", "createdAtEpochMillis" to "1700000000000", "profile" to "'established'",
        ))
        "parameter_definitions" -> listOf(mapOf(
            "id" to "'parameter-19'", "aquariumId" to "'aquarium-19'", "parameter" to "'temperature'",
            "isActive" to "1", "position" to "0", "unit" to "'°C'", "precision" to "1",
            "indicativeMinimum" to "23.0", "indicativeMaximum" to "27.0",
        ))
        "sessions" -> listOf(mapOf(
            "id" to "'session-19'", "aquariumId" to "'aquarium-19'", "occurredAtEpochMillis" to "1700000000001",
            "createdAtEpochMillis" to "1700000000002", "idempotencyKey" to "'save-19'",
        ))
        "measurements" -> listOf(mapOf(
            "id" to "'measurement-19'", "sessionId" to "'session-19'",
            "parameterDefinitionId" to "'parameter-19'", "value" to "24.5",
        ))
        "maintenance_actions" -> listOf(mapOf(
            "id" to "'action-19'", "sessionId" to "'session-19'", "type" to "'water_change'",
            "quantity" to "20.0", "unit" to "'%'", "product" to "NULL",
        ))
        "session_events" -> listOf(mapOf(
            "id" to "'event-19'", "sessionId" to "'session-19'", "type" to "'observation'", "note" to "'Historical observation'",
        ))
        "account_state" -> listOf(mapOf(
            "singletonId" to "1", "accountId" to "'account-19'", "migrationStatus" to "'complete'", "email" to "NULL",
        ))
        "tasks" -> listOf(mapOf(
            "id" to "'task-19'", "aquariumId" to "'aquarium-19'", "title" to "'Clean filter'",
            "recurrence" to "'daily'", "monthlyAnchorDay" to "14",
        ))
        "task_occurrences" -> listOf(false, true).map { resolved -> mapOf(
            "id" to if (resolved) "'completed-19'" else "'pending-19'",
            "taskId" to "'task-19'", "aquariumId" to "'aquarium-19'", "title" to "'Clean filter'",
            "dueYear" to "2026", "dueMonth" to "10", "dueDay" to if (resolved) "13" else "14",
            "dueMinuteOfDay" to "600", "timeZoneId" to "'Europe/Paris'",
            "resolution" to if (resolved) "'completed'" else "NULL",
            "resolvedAtEpochMillis" to if (resolved) "1700000000003" else "NULL",
        ) }
        "sync_operations" -> listOf(mapOf(
            "id" to "1", "operationKey" to "'operation-19'", "entityType" to "'session'",
            "entityId" to "'session-19'", "kind" to "'upsert'", "attemptCount" to "2",
            "nextAttemptAtEpochMillis" to "0", "failureCategory" to "NULL", "failureDetail" to "NULL",
            "clientUpdatedAtEpochMillis" to "1700000000002", "baseServerVersion" to "0",
        ))
        else -> emptyList()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "Room {0} to current")
        fun versions() = (3..13).map { arrayOf(it) }
    }
}
