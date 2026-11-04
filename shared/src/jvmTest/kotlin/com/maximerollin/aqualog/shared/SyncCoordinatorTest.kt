package com.maximerollin.aqualog.shared

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private fun aquariumOperation(id: Long) =
    SyncOperation(id, "key-$id", SyncEntityType.AQUARIUM, "aq", SyncOperationKind.UPSERT, 0, 0, 0, 0)

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
    fun `updateAnalysisInterval queues a parameter_definition sync operation`() = runTest {
        val first = fixture()
        val second = fixture()
        val setup = first.repository.createConfiguredAquarium(
            "Amazonien", 120.0, VolumeUnit.LITERS, AquariumProfile.ESTABLISHED,
            OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
        )
        val cloud = InMemorySyncBackend(false)
        val provider = FixedSyncSessionProvider(SyncSession("account-1", "access-token"))
        assertIs<SyncRunResult.Synchronized>(SyncCoordinator(first.repository, cloud, provider).runOnce())
        val parameter = setup.parameters.first()

        val updated = first.repository.updateAnalysisInterval(parameter.id, 10)

        assertEquals(parameter.copy(analysisIntervalDays = 10), updated)
        val batch = first.repository.pendingSyncBatch()
        assertEquals(listOf(SyncEntityType.PARAMETER_DEFINITION), batch.operations.map { it.entityType })
        assertEquals(parameter.id, batch.operations.single().entityId)
        assertEquals(10, batch.parameterDefinitions.single().analysisIntervalDays)
        assertIs<SyncRunResult.Synchronized>(SyncCoordinator(first.repository, cloud, provider).runOnce())
        assertIs<SyncRunResult.Synchronized>(SyncCoordinator(second.repository, cloud, provider).runOnce())
        assertEquals(
            10,
            second.repository.observeCurrentSetup().first()!!.parameters.single { it.id == parameter.id }
                .analysisIntervalDays,
        )
        first.database.close()
        second.database.close()
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

    @Test
    fun `pending photo is uploaded before the batch is pushed`() = runTest {
        val photos = PhotoFixture(pending = listOf(PendingPhotoSync("aq", "p1")))
        photos.store.write("p1", byteArrayOf(1, 2, 3))

        assertIs<SyncRunResult.Synchronized>(photos.coordinator().runOnce())

        assertEquals(listOf("upload(aq,[1, 2, 3])", "push"), photos.calls.take(2))
        assertEquals(listOf<Pair<String, String?>>("aq" to "p1"), photos.local.marked)
    }

    @Test
    fun `photo_id is pushed only after the upload, then the row is dropped`() = runTest {
        val photos = PhotoFixture(pending = listOf(PendingPhotoSync("aq", "p1")))
        photos.store.write("p1", byteArrayOf(1))

        assertIs<SyncRunResult.Synchronized>(photos.coordinator().runOnce())

        assertEquals(
            listOf("upload(aq,[1])", "push", "commit", "retainOnly([])", "push(photo [aq])", "commit", "retainOnly([])"),
            photos.calls,
        )
        assertEquals(listOf(setOf("aq")), photos.local.pushedPhotoIds.filter(Set<String>::isNotEmpty))
        assertEquals(emptySet(), photos.local.uploaded)
    }

    @Test
    fun `a failed push keeps the uploaded row for the retry`() = runTest {
        val photos = PhotoFixture()
        photos.local.uploaded += "aq"
        photos.backend.pushFailure = SyncNetworkException("offline")

        assertIs<SyncRunResult.Retry>(photos.coordinator().runOnce())

        assertEquals(setOf("aq"), photos.local.uploaded)
    }

    @Test
    fun `removed photo deletes the remote object`() = runTest {
        val photos = PhotoFixture(pending = listOf(PendingPhotoSync("aq", null)))

        assertIs<SyncRunResult.Synchronized>(photos.coordinator().runOnce())

        assertTrue("delete(aq)" in photos.calls)
        assertEquals(listOf<Pair<String, String?>>("aq" to null), photos.local.marked)
    }

    @Test
    fun `missing photos are downloaded before the snapshot is committed`() = runTest {
        val photos = PhotoFixture(
            remote = listOf(remoteAquarium("aq", "p2"), remoteAquarium("evil", "../escape")),
        )
        var storedAtCommit: ByteArray? = null
        photos.local.onCommit = { storedAtCommit = photos.store.read("p2") }

        assertIs<SyncRunResult.Synchronized>(photos.coordinator().runOnce())

        // The unsafe "../escape" id is never downloaded.
        assertEquals(listOf("push", "download(aq)", "commit", "retainOnly([])"), photos.calls)
        assertTrue(byteArrayOf(9).contentEquals(storedAtCommit))
    }

    @Test
    fun `a failed download does not fail the sync`() = runTest {
        val photos = PhotoFixture(
            remote = listOf(remoteAquarium("aq", "p2")),
            downloadFailure = SyncNetworkException("offline"),
        )

        assertIs<SyncRunResult.Synchronized>(photos.coordinator().runOnce())

        assertTrue("commit" in photos.calls)
        assertEquals(null, photos.store.read("p2"))
    }

    @Test
    fun `a photo that cannot be stored does not fail the sync`() = runTest {
        val photos = PhotoFixture(remote = listOf(remoteAquarium("aq", "p2")))
        photos.store.writeFailure = IOException("disk full")

        assertIs<SyncRunResult.Synchronized>(photos.coordinator().runOnce())

        assertTrue("commit" in photos.calls)
    }

    @Test
    fun `a photo changed during the run is uploaded before its Aquarium is pushed`() = runTest {
        val photos = PhotoFixture(localPhotoIds = setOf("p3"))
        var changed = false
        photos.local.onCommit = {
            if (!changed) {
                changed = true
                photos.store.write("p3", byteArrayOf(3))
                photos.local.pendingPhotos = listOf(PendingPhotoSync("aq", "p3"))
                photos.local.operations = listOf(aquariumOperation(2))
            }
        }

        assertIs<SyncRunResult.Synchronized>(photos.coordinator().runOnce())

        assertEquals(
            listOf(
                "push", "commit", "retainOnly([p3])",
                "upload(aq,[3])", "push", "commit", "retainOnly([p3])",
                "push(photo [aq])", "commit", "retainOnly([p3])",
            ),
            photos.calls,
        )
    }

    @Test
    fun `photos no Aquarium references are removed after commit`() = runTest {
        val photos = PhotoFixture(localPhotoIds = setOf("p1"))

        assertIs<SyncRunResult.Synchronized>(photos.coordinator().runOnce())

        assertEquals(listOf("push", "commit", "retainOnly([p1])"), photos.calls)
    }

    @Test
    fun `an upload failure retries like a push failure`() = runTest {
        val photos = PhotoFixture(
            pending = listOf(PendingPhotoSync("aq", "p1")),
            uploadFailure = SyncNetworkException("offline"),
        )
        photos.store.write("p1", byteArrayOf(1))

        assertIs<SyncRunResult.Retry>(photos.coordinator().runOnce())

        assertTrue("push" !in photos.calls)
        assertEquals(emptyList<Pair<String, String?>>(), photos.local.marked)
    }

    private fun remoteAquarium(id: String, photoId: String?) =
        Aquarium(id, "Remote", 60.0, VolumeUnit.LITERS, 1L, photoId = photoId)

    private class PhotoFixture(
        pending: List<PendingPhotoSync> = emptyList(),
        remote: List<Aquarium> = emptyList(),
        localPhotoIds: Set<String> = emptySet(),
        uploadFailure: Exception? = null,
        downloadFailure: Exception? = null,
    ) {
        val calls = mutableListOf<String>()
        val local = PhotoLocalData(calls, pending, localPhotoIds)
        val store = MemoryPhotoStore(calls)
        val backend = PhotoBackend(calls, remote, uploadFailure, downloadFailure)

        fun coordinator() = SyncCoordinator(
            local,
            backend,
            FixedSyncSessionProvider(SyncSession("account-1", "access")),
            photoStore = store,
        )
    }

    private class PhotoLocalData(
        private val calls: MutableList<String>,
        var pendingPhotos: List<PendingPhotoSync>,
        private val photoIds: Set<String>,
    ) : SyncLocalDataSource {
        val marked = mutableListOf<Pair<String, String?>>()
        val uploaded = mutableSetOf<String>()
        val pushedPhotoIds = mutableListOf<Set<String>>()
        var onCommit: suspend () -> Unit = {}
        // One pending operation, so a push happens on the first iteration.
        var operations = listOf(aquariumOperation(1))

        override fun observeSyncState() = flowOf(SyncState(SyncStatus.SYNCED, 0))
        override suspend fun pendingSyncBatch(limit: Int, eligibleAtEpochMillis: Long) = PendingSyncBatch(
            operations, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            photoAquariumIds = uploaded.toSet(),
        )
        override suspend fun nextRetryAtEpochMillis(): Long? = null
        override suspend fun syncAttemptCount() = 0
        override suspend fun syncGeneration() = 0L
        override suspend fun acknowledgeSyncOperations(operationIds: List<Long>) = Unit
        override suspend fun recordSyncFailure(
            operationIds: List<Long>,
            failure: SyncFailure,
            nextRetryAtEpochMillis: Long,
            expectedGeneration: Long,
        ) = true
        override suspend fun retrySyncNow() = Unit
        override suspend fun commitRemoteCopy(copy: InitialAccountCopy, acknowledgedOperationIds: List<Long>) {
            operations = operations.filterNot { it.id in acknowledgedOperationIds }
            onCommit()
            calls += "commit"
        }
        override suspend fun pendingPhotoSyncs() = pendingPhotos
        override suspend fun markPhotoSynced(aquariumId: String, photoId: String?) {
            marked += aquariumId to photoId
            pendingPhotos = pendingPhotos.filterNot { it.aquariumId == aquariumId }
            uploaded += aquariumId
            operations = operations + aquariumOperation(100L + marked.size)
        }
        override suspend fun photoIdsPushed(aquariumIds: Set<String>) {
            pushedPhotoIds += aquariumIds
            uploaded -= aquariumIds
        }
        override suspend fun aquariumPhotoIds() = photoIds
    }

    private class MemoryPhotoStore(private val calls: MutableList<String>) : AquariumPhotoStore {
        private val photos = mutableMapOf<String, ByteArray>()
        var writeFailure: IOException? = null
        override suspend fun write(photoId: String, jpeg: ByteArray) {
            writeFailure?.let { throw it }
            photos[photoId] = jpeg
        }
        override suspend fun read(photoId: String) = photos[photoId]
        override suspend fun delete(photoId: String) {
            photos.remove(photoId)
        }
        override suspend fun retainOnly(photoIds: Set<String>) {
            calls += "retainOnly($photoIds)"
            photos.keys.retainAll(photoIds)
        }
        override suspend fun clear() = photos.clear()
    }

    private class PhotoBackend(
        private val calls: MutableList<String>,
        private val remote: List<Aquarium>,
        private val uploadFailure: Exception?,
        private val downloadFailure: Exception?,
    ) : SyncBackend {
        var pushFailure: Exception? = null
        override suspend fun push(session: SyncSession, batch: PendingSyncBatch) {
            pushFailure?.let { throw it }
            calls += if (batch.photoAquariumIds.isEmpty()) "push" else "push(photo ${batch.photoAquariumIds})"
        }
        override suspend fun pull(session: SyncSession) =
            InitialAccountCopy(remote, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        override suspend fun uploadAquariumPhoto(session: SyncSession, aquariumId: String, jpeg: ByteArray) {
            uploadFailure?.let { throw it }
            calls += "upload($aquariumId,${jpeg.toList()})"
        }
        override suspend fun deleteAquariumPhoto(session: SyncSession, aquariumId: String) {
            calls += "delete($aquariumId)"
        }
        override suspend fun downloadAquariumPhoto(session: SyncSession, aquariumId: String): ByteArray {
            downloadFailure?.let { throw it }
            calls += "download($aquariumId)"
            return byteArrayOf(9)
        }
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
