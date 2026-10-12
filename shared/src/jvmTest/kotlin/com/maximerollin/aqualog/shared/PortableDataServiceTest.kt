package com.maximerollin.aqualog.shared

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortableDataServiceTest {
    @Test
    fun `empty export has a deterministic versioned portable schema`() = runTest {
        val fixture = portableFixture()

        assertEquals(
            """
            {
                "formatVersion": 1,
                "duplicateStrategy": "replace_local_portable_data",
                "aquariums": [],
                "parameterDefinitions": [],
                "sessions": [],
                "measurements": [],
                "maintenanceActions": [],
                "events": [],
                "tasks": [],
                "taskOccurrences": []
            }
            """.trimIndent(),
            fixture.service.export(),
        )

        fixture.close()
    }

    @Test
    fun `portable export round trip preserves local behavior stable ids and every relation`() = runTest {
        val source = portableFixture()
        val setup = source.repository.createConfiguredAquarium(
            name = "Amazonien",
            volume = 120.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.ESTABLISHED,
            parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
        )
        val active = setup.parameters.filter(ParameterDefinition::isActive)
        val session = source.repository.saveRapidSession(
            RapidSessionInput(
                aquariumId = setup.aquarium.id,
                occurredAtEpochMillis = 1_800_000_100_000L,
                idempotencyKey = "portable-session",
                measurementInputs = mapOf(active[0].id to "24.5", active[1].id to "7.2"),
                maintenanceActions = listOf(
                    MaintenanceActionInput(
                        type = MaintenanceActionType.WATER_CHANGE,
                        quantity = "20",
                        unit = "%",
                        product = "Conditioner",
                    ),
                ),
                observation = "Fish active",
                incident = "Short power cut",
            ),
        )
        val createdTask = source.repository.createTask(
            TaskInput(
                aquariumId = setup.aquarium.id,
                title = "Clean filter",
                recurrence = TaskRecurrence.WEEKLY,
                firstDueDate = TaskDate(2027, 1, 10),
                minuteOfDay = 18 * 60 + 30,
                timeZoneId = "Europe/Paris",
            ),
        )
        source.repository.resolveTaskOccurrence(
            TaskResolutionInput(
                occurrenceId = createdTask.occurrence.id,
                resolution = TaskResolution.COMPLETED,
                resolvedAtEpochMillis = 1_800_000_200_000L,
            ),
        )
        source.repository.beginAccountMigration("private-account-id")
        source.repository.markAccountInvitationOffered()

        val exported = source.service.export()

        assertFalse(exported.contains("private-account-id"))
        assertFalse(exported.contains("migrationStatus"))
        assertFalse(exported.contains("wasOffered"))

        val restored = portableFixture()
        restored.repository.createConfiguredAquarium(
            name = "Replaced local copy",
            volume = 20.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.CYCLING,
            parameters = OnboardingPresets.parametersFor(AquariumProfile.CYCLING),
        )
        restored.repository.markAccountInvitationOffered()
        val firstImport = assertIs<PortableImportResult.Imported>(restored.service.importData(exported))
        assertEquals(PortableDuplicateStrategy.REPLACE_LOCAL_PORTABLE_DATA, firstImport.duplicateStrategy)
        assertEquals(exported, restored.service.export())
        assertNull(restored.repository.accountMigrationState())
        assertEquals(SyncStatus.SYNCED, restored.repository.observeSyncState().first().status)
        assertTrue(restored.repository.wasAccountInvitationOffered())

        val restoredSetup = restored.repository.observeCurrentSetup().first()
        assertEquals(setup, restoredSetup)
        val restoredSession = restored.repository.observeLatestSession(setup.aquarium.id).first()
        assertEquals(session, restoredSession)
        assertEquals(session.session.id, restored.repository.observeTimeline(TimelineFilter()).first().single().session.id)
        assertEquals(createdTask.occurrence.id, restored.repository.observeResolvedTaskOccurrences().first().single().id)
        assertEquals(TaskDate(2027, 1, 17), restored.repository.observePendingTaskOccurrences().first().single().dueDate)

        assertIs<PortableImportResult.Imported>(restored.service.importData(exported))
        assertEquals(exported, restored.service.export())
        assertEquals(1, restored.repository.sessionCount(setup.aquarium.id))
        assertEquals(1, restored.repository.observeResolvedTaskOccurrences().first().size)
        assertEquals(1, restored.repository.observePendingTaskOccurrences().first().size)

        source.close()
        restored.close()
    }

    @Test
    fun `restore is rejected atomically while account migration is pending or linked`() = runTest {
        val incoming = portableFixture()
        incoming.repository.createConfiguredAquarium(
            name = "Incoming",
            volume = 80.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.ESTABLISHED,
            parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
        )
        val payload = incoming.service.export()

        listOf(false, true).forEach { migrationComplete ->
            val existing = portableFixture()
            existing.repository.createConfiguredAquarium(
                name = "Must stay local",
                volume = 45.0,
                volumeUnit = VolumeUnit.LITERS,
                profile = AquariumProfile.CYCLING,
                parameters = OnboardingPresets.parametersFor(AquariumProfile.CYCLING),
            )
            existing.repository.beginAccountMigration("linked-account")
            if (migrationComplete) existing.repository.completeAccountMigration("linked-account")
            val before = existing.service.export()
            val pendingBefore = existing.repository.observeSyncState().first().pendingOperationCount

            val rejected = assertIs<PortableImportResult.Rejected>(existing.service.importData(payload))

            assertEquals(PortableImportRejection.ACCOUNT_LINKED, rejected.reason)
            assertEquals(PortableImportProblem.ACCOUNT_LINKED, rejected.problem)
            assertEquals(before, existing.service.export())
            assertEquals("linked-account", existing.repository.accountMigrationState()?.accountId)
            assertEquals(pendingBefore, existing.repository.observeSyncState().first().pendingOperationCount)
            existing.close()
        }
        incoming.close()
    }

    @Test
    fun `invalid unknown truncated and inconsistent imports leave existing data intact`() = runTest {
        val incoming = portableFixture()
        val incomingSetup = incoming.repository.createConfiguredAquarium(
            name = "Incoming",
            volume = 80.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.CYCLING,
            parameters = OnboardingPresets.parametersFor(AquariumProfile.CYCLING),
        )
        val active = incomingSetup.parameters.first(ParameterDefinition::isActive)
        val incomingSession = incoming.repository.saveRapidSession(
            RapidSessionInput(
                aquariumId = incomingSetup.aquarium.id,
                occurredAtEpochMillis = 1_800_000_100_000L,
                idempotencyKey = "incoming-session",
                measurementInputs = mapOf(active.id to "23.0"),
            ),
        )
        val valid = incoming.service.export()

        val existing = portableFixture()
        existing.repository.createConfiguredAquarium(
            name = "Must survive",
            volume = 45.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.PLANTED_SHRIMP,
            parameters = OnboardingPresets.parametersFor(AquariumProfile.PLANTED_SHRIMP),
        )
        val before = existing.service.export()

        val cases = listOf(
            Triple(
                valid.dropLast(12),
                PortableImportRejection.INVALID_FORMAT,
                PortableImportProblem.INVALID_DOCUMENT,
            ),
            Triple(
                """{"formatVersion": 1}""",
                PortableImportRejection.INVALID_FORMAT,
                PortableImportProblem.SCHEMA_MISMATCH,
            ),
            Triple(
                valid.replaceFirst("\"formatVersion\": 1", "\"formatVersion\": 99"),
                PortableImportRejection.UNSUPPORTED_VERSION,
                PortableImportProblem.UNSUPPORTED_VERSION,
            ),
            Triple(
                valid.replace(
                    "\"sessionId\": \"${incomingSession.session.id}\"",
                    "\"sessionId\": \"missing-session\"",
                ),
                PortableImportRejection.INVALID_DATA,
                PortableImportProblem.INVALID_MEASUREMENTS,
            ),
            Triple(
                valid.replaceFirst(
                    "\"id\": \"${incomingSetup.parameters[1].id}\"",
                    "\"id\": \"${incomingSetup.parameters[0].id}\"",
                ),
                PortableImportRejection.INVALID_DATA,
                PortableImportProblem.DUPLICATE_IDENTIFIERS,
            ),
        )

        cases.forEach { (payload, expectedReason, expectedProblem) ->
            val rejected = assertIs<PortableImportResult.Rejected>(existing.service.importData(payload))
            assertEquals(expectedReason, rejected.reason)
            assertEquals(expectedProblem, rejected.problem)
            assertTrue(rejected.explanation.isNotBlank())
            assertEquals(before, existing.service.export())
        }

        incoming.close()
        existing.close()
    }

    @Test
    fun `non numeric format versions are rejected without changing existing data`() = runTest {
        val existing = portableFixture()
        existing.repository.createConfiguredAquarium(
            name = "Must survive malformed versions",
            volume = 45.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.CYCLING,
            parameters = OnboardingPresets.parametersFor(AquariumProfile.CYCLING),
        )
        val before = existing.service.export()
        val malformedVersions = listOf(
            """{"formatVersion": {}}""",
            """{"formatVersion": []}""",
            """{"formatVersion": true}""",
            """{"formatVersion": "1"}""",
        )

        malformedVersions.forEach { payload ->
            val rejected = assertIs<PortableImportResult.Rejected>(existing.service.importData(payload))
            assertEquals(PortableImportRejection.INVALID_FORMAT, rejected.reason)
            assertEquals(PortableImportProblem.MISSING_VERSION, rejected.problem)
            assertEquals(before, existing.service.export())
        }

        existing.close()
    }

    @Test
    fun `import rejects incomplete unordered inactive Parameter sets and empty Sessions`() = runTest {
        val incoming = portableFixture()
        val setup = incoming.repository.createConfiguredAquarium(
            name = "Incoming",
            volume = 80.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.ESTABLISHED,
            parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
        )
        val active = setup.parameters.first(ParameterDefinition::isActive)
        incoming.repository.saveRapidSession(
            RapidSessionInput(
                aquariumId = setup.aquarium.id,
                occurredAtEpochMillis = 1_800_000_100_000L,
                idempotencyKey = "validation-session",
                measurementInputs = mapOf(active.id to "24.0"),
            ),
        )
        val document = TEST_JSON.decodeFromString<PortableDataDocument>(incoming.service.export())

        val target = portableFixture()
        target.repository.createConfiguredAquarium(
            name = "Must survive",
            volume = 45.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.CYCLING,
            parameters = OnboardingPresets.parametersFor(AquariumProfile.CYCLING),
        )
        val before = target.service.export()
        val invalidDocuments = listOf(
            document.copy(parameterDefinitions = document.parameterDefinitions.dropLast(1)) to
                PortableImportProblem.INVALID_PARAMETERS,
            document.copy(
                parameterDefinitions = document.parameterDefinitions.map { it.copy(isActive = false) },
            ) to PortableImportProblem.INVALID_PARAMETERS,
            document.copy(
                parameterDefinitions = document.parameterDefinitions.mapIndexed { index, parameter ->
                    if (index == 0) parameter.copy(position = 2) else parameter
                },
            ) to PortableImportProblem.INVALID_PARAMETERS,
            document.copy(measurements = emptyList()) to PortableImportProblem.INVALID_SESSIONS,
        )

        invalidDocuments.forEach { (invalid, expectedProblem) ->
            val result = assertIs<PortableImportResult.Rejected>(
                target.service.importData(TEST_JSON.encodeToString(invalid)),
            )
            assertEquals(PortableImportRejection.INVALID_DATA, result.reason)
            assertEquals(expectedProblem, result.problem)
            assertEquals(before, target.service.export())
        }

        incoming.close()
        target.close()
    }

    @Test
    fun `historical Measurement may reference a currently inactive Parameter in the same Aquarium`() = runTest {
        val source = portableFixture()
        val setup = source.repository.createConfiguredAquarium(
            name = "History",
            volume = 80.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.ESTABLISHED,
            parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
        )
        val active = setup.parameters.first(ParameterDefinition::isActive)
        val inactive = setup.parameters.first { !it.isActive }
        source.repository.saveRapidSession(
            RapidSessionInput(
                aquariumId = setup.aquarium.id,
                occurredAtEpochMillis = 1_800_000_100_000L,
                idempotencyKey = "historical-inactive-parameter",
                measurementInputs = mapOf(active.id to "24.0"),
            ),
        )
        val document = TEST_JSON.decodeFromString<PortableDataDocument>(source.service.export())
        val historicalDocument = document.copy(
            measurements = document.measurements.map { it.copy(parameterDefinitionId = inactive.id) },
        )

        val target = portableFixture()
        assertIs<PortableImportResult.Imported>(
            target.service.importData(TEST_JSON.encodeToString(historicalDocument)),
        )
        val restored = target.repository.observeTimeline(TimelineFilter()).first().single()
        assertEquals(inactive.id, restored.measurements.single().definition.id)
        assertFalse(restored.measurements.single().definition.isActive)

        source.close()
        target.close()
    }

    @Test
    fun `Room transaction restores every local row when insertion fails after delete`() = runTest {
        val incoming = portableFixture()
        incoming.repository.createConfiguredAquarium(
            name = "Incoming",
            volume = 80.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.ESTABLISHED,
            parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
        )
        val payload = incoming.service.export()

        val existing = portableFixture()
        existing.repository.createConfiguredAquarium(
            name = "Must survive rollback",
            volume = 45.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.CYCLING,
            parameters = OnboardingPresets.parametersFor(AquariumProfile.CYCLING),
        )
        val before = existing.service.export()
        val pendingBefore = existing.repository.observeSyncState().first().pendingOperationCount
        existing.rejectAquariumInserts()

        val rejected = assertIs<PortableImportResult.Rejected>(existing.service.importData(payload))

        assertEquals(PortableImportRejection.IMPORT_FAILED, rejected.reason)
        assertEquals(PortableImportProblem.DATABASE_WRITE_FAILED, rejected.problem)
        assertEquals(before, existing.service.export())
        assertEquals("Must survive rollback", existing.repository.observeCurrentSetup().first()?.aquarium?.name)
        assertNull(existing.repository.accountMigrationState())
        assertEquals(pendingBefore, existing.repository.observeSyncState().first().pendingOperationCount)

        incoming.close()
        existing.close()
    }

    private fun portableFixture(): PortableFixture {
        val path = Files.createTempDirectory("aqualog-portable-data")
            .resolve("aqualog.db")
            .toAbsolutePath()
            .toString()
        val database = createAquaLogDatabase(createJvmDatabaseBuilder(path))
        val ids = generateSequence(1) { it + 1 }.map { "portable-id-$it" }.iterator()
        return PortableFixture(
            database = database,
            repository = RoomAquariumRepository(database, ids::next, { 1_800_000_000_000L }),
            service = PortableDataService(database),
        )
    }

    private class PortableFixture(
        private val database: AquaLogDatabase,
        val repository: RoomAquariumRepository,
        val service: PortableDataService,
    ) {
        suspend fun rejectAquariumInserts() {
            database.useConnection(false) { connection ->
                connection.usePrepared(
                    """
                    CREATE TRIGGER reject_portable_aquarium_insert
                    BEFORE INSERT ON aquariums
                    BEGIN
                        SELECT RAISE(ABORT, 'forced portable import failure');
                    END
                    """.trimIndent(),
                ) { statement ->
                    statement.step()
                }
            }
        }

        fun close() = database.close()
    }
}

private val TEST_JSON = Json { encodeDefaults = true }
