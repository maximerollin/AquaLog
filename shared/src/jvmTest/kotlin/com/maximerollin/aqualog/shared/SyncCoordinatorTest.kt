package com.maximerollin.aqualog.shared

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SyncCoordinatorTest {
    @Test
    fun `a local Aquarium creation leaves replayable entity operations after restart`() = runTest {
        val databasePath = Files.createTempDirectory("aqualog-sync-outbox")
            .resolve("aqualog.db")
            .toAbsolutePath()
            .toString()
        val first = fixture(databasePath)

        val setup = first.repository.createConfiguredAquarium(
            name = "Amazonien",
            volume = 120.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.ESTABLISHED,
            parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
        )

        assertEquals(SyncStatus.PENDING, first.repository.observeSyncState().first().status)
        first.database.close()

        val reopened = fixture(databasePath)
        val batch = reopened.repository.pendingSyncBatch()
        assertEquals(
            listOf(SyncEntityType.AQUARIUM) + List(setup.parameters.size) { SyncEntityType.PARAMETER_DEFINITION },
            batch.operations.map(SyncOperation::entityType),
        )
        assertEquals(setup.aquarium.id, batch.aquariums.single().id)
        assertEquals(setup.parameters.map(ParameterDefinition::id), batch.parameterDefinitions.map(ParameterDefinition::id))
        reopened.database.close()
    }

    @Test
    fun `a local Session atomically queues every changed entity without a network dependency`() = runTest {
        val fixture = fixture()
        val setup = fixture.repository.createConfiguredAquarium(
            name = "Amazonien",
            volume = 120.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.ESTABLISHED,
            parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
        )
        val temperature = setup.parameters.first { it.parameter == BuiltInParameter.TEMPERATURE }

        fixture.repository.saveRapidSession(
            RapidSessionInput(
                aquariumId = setup.aquarium.id,
                occurredAtEpochMillis = 1_700_000_100_000L,
                idempotencyKey = "local-first-session",
                measurementInputs = mapOf(temperature.id to "24.5"),
                maintenanceActions = listOf(
                    MaintenanceActionInput(MaintenanceActionType.WATER_CHANGE, "20", "%"),
                ),
                observation = "Eau claire",
            ),
        )

        val types = fixture.repository.pendingSyncBatch().operations.map(SyncOperation::entityType)
        assertEquals(1, types.count { it == SyncEntityType.SESSION })
        assertEquals(1, types.count { it == SyncEntityType.MEASUREMENT })
        assertEquals(1, types.count { it == SyncEntityType.MAINTENANCE_ACTION })
        assertEquals(1, types.count { it == SyncEntityType.EVENT })
        fixture.database.close()
    }

    @Test
    fun `editing a Session queues replayable upserts with stable child UUIDs`() = runTest {
        val fixture = fixture()
        val setup = fixture.repository.createConfiguredAquarium(
            name = "Amazonien",
            volume = 120.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.ESTABLISHED,
            parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
        )
        val temperature = setup.parameters.first { it.parameter == BuiltInParameter.TEMPERATURE }
        val recorded = fixture.repository.saveRapidSession(
            RapidSessionInput(
                aquariumId = setup.aquarium.id,
                occurredAtEpochMillis = 1_700_000_100_000L,
                idempotencyKey = "editable-session",
                measurementInputs = mapOf(temperature.id to "24.5"),
                maintenanceActions = listOf(
                    MaintenanceActionInput(MaintenanceActionType.WATER_CHANGE, "20", "%"),
                ),
                observation = "Eau claire",
            ),
        )
        fixture.repository.acknowledgeSyncOperations(
            fixture.repository.pendingSyncBatch().operations.map(SyncOperation::id),
        )

        val updated = fixture.repository.updateSession(
            SessionEditInput(
                sessionId = recorded.session.id,
                aquariumId = setup.aquarium.id,
                occurredAtEpochMillis = 1_700_000_200_000L,
                measurementInputs = mapOf(temperature.id to "25.1"),
                maintenanceActions = listOf(
                    MaintenanceActionInput(MaintenanceActionType.WATER_CHANGE, "30", "%"),
                ),
                observation = "Eau limpide",
            ),
        )

        assertEquals(recorded.measurements.single().id, updated.measurements.single().id)
        assertEquals(recorded.maintenanceActions.single().id, updated.maintenanceActions.single().id)
        assertEquals(recorded.events.single().id, updated.events.single().id)
        assertEquals(
            listOf(
                SyncEntityType.SESSION,
                SyncEntityType.MEASUREMENT,
                SyncEntityType.MAINTENANCE_ACTION,
                SyncEntityType.EVENT,
            ),
            fixture.repository.pendingSyncBatch().operations.map(SyncOperation::entityType),
        )
        fixture.database.close()
    }

    @Test
    fun `replayed sync is idempotent and a second device sees the Session in its local Timeline`() = runTest {
        val firstDevice = fixture()
        val secondDevice = fixture()
        val setup = firstDevice.repository.createConfiguredAquarium(
            name = "Amazonien",
            volume = 120.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.ESTABLISHED,
            parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
        )
        val temperature = setup.parameters.first { it.parameter == BuiltInParameter.TEMPERATURE }
        val localSession = firstDevice.repository.saveRapidSession(
            RapidSessionInput(
                aquariumId = setup.aquarium.id,
                occurredAtEpochMillis = 1_700_000_100_000L,
                idempotencyKey = "two-device-session",
                measurementInputs = mapOf(temperature.id to "24.5"),
                maintenanceActions = listOf(
                    MaintenanceActionInput(MaintenanceActionType.WATER_CHANGE, "20", "%"),
                ),
                observation = "Eau claire",
            ),
        )
        assertEquals(
            localSession.session.id,
            firstDevice.repository.observeLatestSession(setup.aquarium.id).first()?.session?.id,
        )
        val cloud = InMemorySyncBackend(failAfterFirstAcceptedPush = true)
        val sessionProvider = FixedSyncSessionProvider(SyncSession("account-1", "access-token"))
        val firstCoordinator = SyncCoordinator(firstDevice.repository, cloud, sessionProvider)

        val retry = assertIs<SyncRunResult.Retry>(firstCoordinator.runOnce())
        assertEquals(1_000L, retry.afterMillis)
        assertEquals(SyncStatus.PENDING, firstDevice.repository.observeSyncState().first().status)
        assertIs<SyncRunResult.Synchronized>(firstCoordinator.runOnce())
        assertEquals(SyncStatus.SYNCED, firstDevice.repository.observeSyncState().first().status)
        assertEquals(1, cloud.copy.sessions.size)
        assertEquals(1, cloud.copy.measurements.size)
        assertEquals(1, cloud.copy.maintenanceActions.size)
        assertEquals(1, cloud.copy.events.size)

        val secondCoordinator = SyncCoordinator(secondDevice.repository, cloud, sessionProvider)
        assertIs<SyncRunResult.Synchronized>(secondCoordinator.runOnce())
        val secondTimeline = secondDevice.repository.observeTimeline(TimelineFilter()).first()
        assertEquals(localSession.session.id, secondTimeline.single().session.id)
        assertEquals(24.5, secondTimeline.single().measurements.single().measurement.value)
        assertEquals("Eau claire", secondTimeline.single().events.single().note)
        firstDevice.database.close()
        secondDevice.database.close()
    }

    @Test
    fun `retry delays are deterministic and bounded`() {
        val policy = SyncRetryPolicy(baseDelayMillis = 1_000L, maximumDelayMillis = 8_000L)

        assertEquals(1_000L, policy.delayMillis(attempt = 1))
        assertEquals(2_000L, policy.delayMillis(attempt = 2))
        assertEquals(8_000L, policy.delayMillis(attempt = 20))
    }

    private fun fixture(path: String): Fixture {
        val database = createAquaLogDatabase(createJvmDatabaseBuilder(path))
        val ids = generateSequence(1) { it + 1 }.map { "uuid-$it" }.iterator()
        return Fixture(
            database = database,
            repository = RoomAquariumRepository(database, ids::next, { 1_700_000_000_000L }),
        )
    }

    private fun fixture(): Fixture = fixture(
        Files.createTempDirectory("aqualog-sync")
            .resolve("aqualog.db")
            .toAbsolutePath()
            .toString(),
    )

    private data class Fixture(
        val database: AquaLogDatabase,
        val repository: RoomAquariumRepository,
    )

    private class FixedSyncSessionProvider(
        private val session: SyncSession,
    ) : SyncSessionProvider {
        override suspend fun currentSession() = session
    }

    private class InMemorySyncBackend(
        private var failAfterFirstAcceptedPush: Boolean,
    ) : SyncBackend {
        private val aquariums = linkedMapOf<String, Aquarium>()
        private val parameters = linkedMapOf<String, ParameterDefinition>()
        private val sessions = linkedMapOf<String, Session>()
        private val measurements = linkedMapOf<String, Measurement>()
        private val actions = linkedMapOf<String, MaintenanceAction>()
        private val events = linkedMapOf<String, SessionEvent>()

        val copy: InitialAccountCopy
            get() = InitialAccountCopy(
                aquariums.values.toList(),
                parameters.values.toList(),
                sessions.values.toList(),
                measurements.values.toList(),
                actions.values.toList(),
                events.values.toList(),
            )

        override suspend fun push(session: SyncSession, batch: PendingSyncBatch) {
            batch.aquariums.forEach { aquariums[it.id] = it }
            batch.parameterDefinitions.forEach { parameters[it.id] = it }
            batch.sessions.forEach { sessions[it.id] = it }
            batch.measurements.forEach { measurements[it.id] = it }
            batch.maintenanceActions.forEach { actions[it.id] = it }
            batch.events.forEach { events[it.id] = it }
            if (failAfterFirstAcceptedPush) {
                failAfterFirstAcceptedPush = false
                error("connection lost after server acceptance")
            }
        }

        override suspend fun pull(session: SyncSession): InitialAccountCopy = copy
    }
}
