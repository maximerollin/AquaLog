package com.maximerollin.aqualog.shared

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SyncCoordinatorTest {
    @Test
    fun `inflight pull cannot commit locally after account revocation starts`() = runTest {
        val fixture = fixture()
        fixture.repository.createAquarium("Pending Aquarium", 80.0, VolumeUnit.LITERS)
        val pullStarted = CompletableDeferred<Unit>()
        val releasePull = CompletableDeferred<Unit>()
        val backend = object : SyncBackend {
            override suspend fun push(session: SyncSession, batch: PendingSyncBatch) = Unit
            override suspend fun pull(session: SyncSession): InitialAccountCopy {
                pullStarted.complete(Unit)
                releasePull.await()
                return InitialAccountCopy(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
            }
        }
        val gate = AccountOperationGate(fixture.repository)
        val run = async {
            SyncCoordinator(
                fixture.repository,
                backend,
                FixedSyncSessionProvider(SyncSession("account-1", "access")),
                operationGate = gate,
            ).runOnce()
        }
        pullStarted.await()
        val revoke = async(start = CoroutineStart.UNDISPATCHED) { gate.beginRevocation() }

        releasePull.complete(Unit)

        revoke.await()
        assertEquals(SyncRunResult.SignedOut, run.await())
        assertTrue(fixture.repository.pendingSyncBatch().operations.isNotEmpty())
        fixture.database.close()
    }

    @Test
    fun `a local edit created during push survives the stale pull and is drained before success`() = runTest {
        val fixture = fixture()
        val aquarium = fixture.repository.createAquarium("Amazonien", 120.0, VolumeUnit.LITERS)
        val cloud = InMemorySyncBackend(failAfterFirstAcceptedPush = false)
        cloud.afterNextPush = {
            fixture.repository.updateAquarium(aquarium.copy(name = "Amazonien planté"))
        }

        val result = SyncCoordinator(
            fixture.repository,
            cloud,
            FixedSyncSessionProvider(SyncSession("account-1", "access-token")),
        ).runOnce()

        assertIs<SyncRunResult.Synchronized>(result)
        assertEquals("Amazonien planté", fixture.repository.observeCurrentAquarium().first()?.name)
        assertEquals("Amazonien planté", cloud.copy.aquariums.single().name)
        assertEquals(SyncStatus.SYNCED, fixture.repository.observeSyncState().first().status)
        assertEquals(2, cloud.pushCount)
        fixture.database.close()
    }

    @Test
    fun `Aquarium and Parameter updates enqueue their current values`() = runTest {
        val fixture = fixture()
        val setup = fixture.repository.createConfiguredAquarium(
            "Amazonien", 120.0, VolumeUnit.LITERS, AquariumProfile.ESTABLISHED,
            OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
        )
        fixture.repository.acknowledgeSyncOperations(fixture.repository.pendingSyncBatch().operations.map { it.id })

        fixture.repository.updateAquarium(setup.aquarium.copy(name = "Bac salon", volume = 180.0))
        fixture.repository.updateParameterDefinition(setup.parameters.first().copy(unit = "C", precision = 2))

        val batch = fixture.repository.pendingSyncBatch()
        assertEquals("Bac salon", batch.aquariums.single().name)
        assertEquals("C", batch.parameterDefinitions.single().unit)
        assertEquals(
            listOf(SyncEntityType.AQUARIUM, SyncEntityType.PARAMETER_DEFINITION),
            batch.operations.map { it.entityType },
        )
        fixture.database.close()
    }

    @Test
    fun `removing Session children queues remote deletions that do not resurrect on another device`() = runTest {
        val first = fixture()
        val second = fixture()
        val setup = first.repository.createConfiguredAquarium(
            "Amazonien", 120.0, VolumeUnit.LITERS, AquariumProfile.ESTABLISHED,
            OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
        )
        val parameter = setup.parameters.first { it.parameter == BuiltInParameter.TEMPERATURE }
        val recorded = first.repository.saveRapidSession(
            RapidSessionInput(
                setup.aquarium.id, 1_700_000_100_000L, "children-delete",
                mapOf(parameter.id to "24.5"),
                listOf(MaintenanceActionInput(MaintenanceActionType.WATER_CHANGE, "20", "%")),
                observation = "Eau claire",
            ),
        )
        val cloud = InMemorySyncBackend(false)
        val provider = FixedSyncSessionProvider(SyncSession("account-1", "access-token"))
        assertIs<SyncRunResult.Synchronized>(SyncCoordinator(first.repository, cloud, provider).runOnce())
        assertIs<SyncRunResult.Synchronized>(SyncCoordinator(second.repository, cloud, provider).runOnce())

        first.repository.updateSession(
            SessionEditInput(
                recorded.session.id, setup.aquarium.id, recorded.session.occurredAtEpochMillis,
                emptyMap(), emptyList(), observation = "", incident = "Fuite",
            ),
        )
        val deletions = first.repository.pendingSyncBatch().operations.filter { it.kind == SyncOperationKind.DELETE }
        assertTrue(deletions.any { it.entityType == SyncEntityType.MEASUREMENT })
        assertTrue(deletions.any { it.entityType == SyncEntityType.MAINTENANCE_ACTION })
        assertTrue(deletions.any { it.entityType == SyncEntityType.EVENT })
        assertIs<SyncRunResult.Synchronized>(SyncCoordinator(first.repository, cloud, provider).runOnce())
        assertIs<SyncRunResult.Synchronized>(SyncCoordinator(second.repository, cloud, provider).runOnce())

        val downloaded = second.repository.observeSession(recorded.session.id).first()!!
        assertTrue(downloaded.measurements.isEmpty())
        assertTrue(downloaded.maintenanceActions.isEmpty())
        assertEquals(listOf("Fuite"), downloaded.events.map { it.note })
        first.database.close()
        second.database.close()
    }

    @Test
    fun `deleting a Session persists tombstones for the aggregate until synchronization acknowledges them`() = runTest {
        val fixture = fixture()
        val setup = fixture.repository.createConfiguredAquarium(
            "Amazonien", 120.0, VolumeUnit.LITERS, AquariumProfile.ESTABLISHED,
            OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
        )
        val parameter = setup.parameters.first { it.parameter == BuiltInParameter.TEMPERATURE }
        val recorded = fixture.repository.saveRapidSession(
            RapidSessionInput(
                setup.aquarium.id, 1_700_000_100_000L, "delete-session",
                mapOf(parameter.id to "24.5"),
                listOf(MaintenanceActionInput(MaintenanceActionType.WATER_CHANGE, "20", "%")),
                observation = "Eau claire",
            ),
        )
        fixture.repository.acknowledgeSyncOperations(
            fixture.repository.pendingSyncBatch().operations.map(SyncOperation::id),
        )

        fixture.repository.deleteSession(recorded.session.id)

        assertEquals(null, fixture.repository.observeSession(recorded.session.id).first())
        val tombstones = fixture.repository.pendingSyncBatch().operations
        assertEquals(
            setOf(
                SyncEntityType.SESSION,
                SyncEntityType.MEASUREMENT,
                SyncEntityType.MAINTENANCE_ACTION,
                SyncEntityType.EVENT,
            ),
            tombstones.filter { it.kind == SyncOperationKind.DELETE }.map { it.entityType }.toSet(),
        )
        fixture.database.close()
    }

    @Test
    fun `an unauthorized sync refreshes once then requires authentication without a loop`() = runTest {
        val local = fixture()
        local.repository.createAquarium("Amazonien", 120.0, VolumeUnit.LITERS)
        val backend = object : SyncBackend {
            var calls = 0
            override suspend fun push(session: SyncSession, batch: PendingSyncBatch) {
                calls++
                throw CloudAuthenticationExpiredException()
            }
            override suspend fun pull(session: SyncSession) = InitialAccountCopy(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        }
        val provider = object : SyncSessionProvider {
            var refreshes = 0
            override suspend fun currentSession() = SyncSession("account-1", "expired")
            override suspend fun refreshSession(expiredSession: SyncSession): SyncSession? {
                refreshes++
                return SyncSession("account-1", "also-expired")
            }
            override suspend fun requireReauthentication() = Unit
        }

        assertEquals(SyncRunResult.SignedOut, SyncCoordinator(local.repository, backend, provider).runOnce())
        assertEquals(2, backend.calls)
        assertEquals(1, provider.refreshes)
        local.database.close()
    }

    @Test
    fun `an unauthorized sync replays once with the refreshed access token`() = runTest {
        val local = fixture()
        local.repository.createAquarium("Amazonien", 120.0, VolumeUnit.LITERS)
        val usedTokens = mutableListOf<String>()
        val backend = object : SyncBackend {
            override suspend fun push(session: SyncSession, batch: PendingSyncBatch) {
                usedTokens += session.accessToken
                if (session.accessToken == "expired") throw CloudAuthenticationExpiredException()
            }
            override suspend fun pull(session: SyncSession) = InitialAccountCopy(
                local.repository.pendingSyncBatch().aquariums,
                emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            )
        }
        val provider = object : SyncSessionProvider {
            override suspend fun currentSession() = SyncSession("account-1", "expired")
            override suspend fun refreshSession(expiredSession: SyncSession) = SyncSession("account-1", "fresh")
        }

        assertIs<SyncRunResult.Synchronized>(SyncCoordinator(local.repository, backend, provider).runOnce())
        assertEquals(listOf("expired", "fresh"), usedTokens)
        assertEquals(SyncStatus.SYNCED, local.repository.observeSyncState().first().status)
        local.database.close()
    }
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
        assertEquals(SyncStatus.FAILED, firstDevice.repository.observeSyncState().first().status)
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
        var afterNextPush: (suspend () -> Unit)? = null
        var pushCount = 0
        private val aquariums = linkedMapOf<String, Aquarium>()
        private val parameters = linkedMapOf<String, ParameterDefinition>()
        private val sessions = linkedMapOf<String, Session>()
        private val measurements = linkedMapOf<String, Measurement>()
        private val actions = linkedMapOf<String, MaintenanceAction>()
        private val events = linkedMapOf<String, SessionEvent>()
        private val acceptedOperationKeys = mutableSetOf<String>()
        private val versions = linkedMapOf<Pair<SyncEntityType, String>, RemoteEntityVersion>()

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
            pushCount++
            val latestOperations = batch.operations.groupBy { it.entityType to it.entityId }
                .mapValues { (_, operations) -> operations.maxBy { it.id } }
                .values
                .filter { acceptedOperationKeys.add(it.operationKey) }
            latestOperations.forEach { operation ->
                val key = operation.entityType to operation.entityId
                val previousVersion = versions[key]?.serverVersion ?: 0L
                versions[key] = RemoteEntityVersion(
                    entityType = operation.entityType,
                    entityId = operation.entityId,
                    serverVersion = previousVersion + 1L,
                    serverUpdatedAtEpochMillis = operation.clientUpdatedAtEpochMillis,
                    deletedAtEpochMillis = operation.clientUpdatedAtEpochMillis
                        .takeIf { operation.kind == SyncOperationKind.DELETE },
                )
            }
            latestOperations.filter { it.kind == SyncOperationKind.DELETE }.forEach { operation ->
                when (operation.entityType) {
                    SyncEntityType.AQUARIUM -> aquariums.remove(operation.entityId)
                    SyncEntityType.PARAMETER_DEFINITION -> parameters.remove(operation.entityId)
                    SyncEntityType.SESSION -> sessions.remove(operation.entityId)
                    SyncEntityType.MEASUREMENT -> measurements.remove(operation.entityId)
                    SyncEntityType.MAINTENANCE_ACTION -> actions.remove(operation.entityId)
                    SyncEntityType.EVENT -> events.remove(operation.entityId)
                }
            }
            batch.aquariums.forEach { aquariums[it.id] = it }
            batch.parameterDefinitions.forEach { parameters[it.id] = it }
            batch.sessions.forEach { sessions[it.id] = it }
            batch.measurements.forEach { measurements[it.id] = it }
            batch.maintenanceActions.forEach { actions[it.id] = it }
            batch.events.forEach { events[it.id] = it }
            afterNextPush?.also { callback ->
                afterNextPush = null
                callback()
            }
            if (failAfterFirstAcceptedPush) {
                failAfterFirstAcceptedPush = false
                error("connection lost after server acceptance")
            }
        }

        override suspend fun pull(session: SyncSession): InitialAccountCopy = copy

        override suspend fun pullVersioned(session: SyncSession) = RemoteSyncSnapshot(copy, versions.values.toList())
    }
}
