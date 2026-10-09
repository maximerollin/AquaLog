package com.maximerollin.aqualog.shared

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
        restored.repository.beginAccountMigration("local-account-state")
        restored.repository.markAccountInvitationOffered()
        val firstImport = assertIs<PortableImportResult.Imported>(restored.service.importData(exported))
        assertEquals(PortableDuplicateStrategy.REPLACE_LOCAL_PORTABLE_DATA, firstImport.duplicateStrategy)
        assertEquals(exported, restored.service.export())
        assertEquals("local-account-state", restored.repository.accountMigrationState()?.accountId)
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
            valid.dropLast(12) to PortableImportRejection.INVALID_FORMAT,
            """{"formatVersion": 1}""" to PortableImportRejection.INVALID_FORMAT,
            valid.replaceFirst("\"formatVersion\": 1", "\"formatVersion\": 99") to
                PortableImportRejection.UNSUPPORTED_VERSION,
            valid.replace(
                "\"sessionId\": \"${incomingSession.session.id}\"",
                "\"sessionId\": \"missing-session\"",
            ) to PortableImportRejection.INVALID_DATA,
            valid.replaceFirst(
                "\"id\": \"${incomingSetup.parameters[1].id}\"",
                "\"id\": \"${incomingSetup.parameters[0].id}\"",
            ) to PortableImportRejection.INVALID_DATA,
        )

        cases.forEach { (payload, expectedReason) ->
            val rejected = assertIs<PortableImportResult.Rejected>(existing.service.importData(payload))
            assertEquals(expectedReason, rejected.reason)
            assertTrue(rejected.explanation.isNotBlank())
            assertEquals(before, existing.service.export())
        }

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
        fun close() = database.close()
    }
}
