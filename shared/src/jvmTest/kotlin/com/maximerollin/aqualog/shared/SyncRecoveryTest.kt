package com.maximerollin.aqualog.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class SyncRecoveryTest {
    @Test
    fun `pull failure without pending writes persists across restart`() = runTest {
        val path = Files.createTempDirectory("aqualog-sync-pull-recovery")
            .resolve("aqualog.db")
            .toAbsolutePath()
            .toString()
        var now = 5_000L
        val first = fixture(path) { now }
        val coordinator = SyncCoordinator(
            first.repository,
            PullFailingBackend(),
            FixedSessionProvider(),
            currentTimeMillis = { now },
        )

        assertEquals(SyncRunResult.Retry(1_000L), coordinator.runOnce())
        assertEquals(SyncStatus.FAILED, first.repository.observeSyncState().first().status)
        first.database.close()

        now = 5_500L
        val reopened = fixture(path) { now }
        assertEquals(SyncStatus.FAILED, reopened.repository.observeSyncState().first().status)
        assertEquals(6_000L, reopened.repository.nextRetryAtEpochMillis())
        reopened.database.close()
    }

    @Test
    fun `network retry schedule survives restart while local data stays available`() = runTest {
        val path = Files.createTempDirectory("aqualog-sync-recovery")
            .resolve("aqualog.db")
            .toAbsolutePath()
            .toString()
        var now = 10_000L
        val first = fixture(path) { now }
        val aquarium = first.repository.createAquarium("Offline Aquarium", 120.0, VolumeUnit.LITERS)
        val backend = FailingNetworkBackend()
        val session = FixedSessionProvider()

        assertEquals(
            SyncRunResult.Retry(afterMillis = 1_000L),
            SyncCoordinator(first.repository, backend, session, currentTimeMillis = { now }).runOnce(),
        )
        val failed = first.repository.observeSyncState().first()
        assertEquals(SyncStatus.FAILED, failed.status)
        assertEquals(SyncFailureCategory.NETWORK, failed.failure?.category)
        assertEquals(11_000L, failed.nextRetryAtEpochMillis)
        assertEquals(aquarium, first.repository.observeCurrentAquarium().first())
        first.database.close()

        now = 10_500L
        val reopened = fixture(path) { now }
        val coordinator = SyncCoordinator(reopened.repository, backend, session, currentTimeMillis = { now })
        assertEquals(SyncRunResult.Retry(afterMillis = 500L), coordinator.runOnce())
        assertEquals(1, backend.attempts)
        assertEquals(aquarium, reopened.repository.observeCurrentAquarium().first())

        now = 11_000L
        assertEquals(SyncRunResult.Retry(afterMillis = 2_000L), coordinator.runOnce())
        assertEquals(2, backend.attempts)
        assertEquals(13_000L, reopened.repository.observeSyncState().first().nextRetryAtEpochMillis)
        reopened.database.close()
    }

    @Test
    fun `manual retry clears the persisted failure and can synchronize immediately`() = runTest {
        var now = 20_000L
        val path = Files.createTempDirectory("aqualog-sync-manual-retry")
            .resolve("aqualog.db")
            .toAbsolutePath()
            .toString()
        val local = fixture(path) { now }
        local.repository.createAquarium("Retry Aquarium", 80.0, VolumeUnit.LITERS)
        val backend = FailingNetworkBackend()
        val coordinator = SyncCoordinator(
            local.repository,
            backend,
            FixedSessionProvider(),
            currentTimeMillis = { now },
        )
        assertIs<SyncRunResult.Retry>(coordinator.runOnce())

        backend.shouldFail = false
        local.repository.retrySyncNow()

        assertEquals(SyncStatus.PENDING, local.repository.observeSyncState().first().status)
        assertIs<SyncRunResult.Synchronized>(coordinator.runOnce())
        assertEquals(SyncStatus.SYNCED, local.repository.observeSyncState().first().status)
        local.database.close()
    }

    @Test
    fun `server acceptance order wins across devices regardless of client clocks`() = runTest {
        var firstClock = 100L
        var secondClock = 100L
        val first = fixture(
            Files.createTempDirectory("aqualog-sync-lww-first").resolve("aqualog.db").toString(),
        ) { firstClock }
        val second = fixture(
            Files.createTempDirectory("aqualog-sync-lww-second").resolve("aqualog.db").toString(),
        ) { secondClock }
        val aquarium = first.repository.createAquarium("Original", 80.0, VolumeUnit.LITERS)
        val backend = VersionedAquariumBackend()
        val firstCoordinator = SyncCoordinator(
            first.repository,
            backend,
            FixedSessionProvider(),
            currentTimeMillis = { firstClock },
        )
        val secondCoordinator = SyncCoordinator(
            second.repository,
            backend,
            FixedSessionProvider(),
            currentTimeMillis = { secondClock },
        )
        assertIs<SyncRunResult.Synchronized>(firstCoordinator.runOnce())
        assertIs<SyncRunResult.Synchronized>(secondCoordinator.runOnce())

        firstClock = 9_999_999_999_999L
        first.repository.updateAquarium(aquarium.copy(name = "Future clock accepted first"))
        second.repository.updateAquarium(aquarium.copy(name = "Normal clock accepted second"))
        assertEquals(1L, first.repository.pendingSyncBatch().operations.single().baseServerVersion)
        assertEquals(1L, second.repository.pendingSyncBatch().operations.single().baseServerVersion)

        assertIs<SyncRunResult.Synchronized>(firstCoordinator.runOnce())
        assertIs<SyncRunResult.Synchronized>(secondCoordinator.runOnce())
        assertIs<SyncRunResult.Synchronized>(firstCoordinator.runOnce())

        assertEquals("Normal clock accepted second", first.repository.observeCurrentAquarium().first()?.name)
        assertEquals("Normal clock accepted second", second.repository.observeCurrentAquarium().first()?.name)
        first.database.close()
        second.database.close()
    }

    @Test
    fun `an offline edit carries the last downloaded server version as its conflict base`() = runTest {
        var now = 100L
        val local = fixture(
            Files.createTempDirectory("aqualog-sync-base-version").resolve("aqualog.db").toString(),
        ) { now }
        val aquarium = local.repository.createAquarium("Original", 80.0, VolumeUnit.LITERS)
        val backend = VersionedAquariumBackend()
        val coordinator = SyncCoordinator(
            local.repository,
            backend,
            FixedSessionProvider(),
            currentTimeMillis = { now },
        )
        assertIs<SyncRunResult.Synchronized>(coordinator.runOnce())

        now = 200L
        local.repository.updateAquarium(aquarium.copy(name = "Offline edit"))

        assertEquals(1L, local.repository.pendingSyncBatch().operations.single().baseServerVersion)
        local.database.close()
    }

    @Test
    fun `a newer server tombstone wins over an older concurrent local edit`() = runTest {
        var now = 100L
        val local = fixture(
            Files.createTempDirectory("aqualog-sync-delete-conflict").resolve("aqualog.db").toString(),
        ) { now }
        val aquarium = local.repository.createAquarium("Original", 80.0, VolumeUnit.LITERS)
        val backend = VersionedAquariumBackend()
        val coordinator = SyncCoordinator(
            local.repository,
            backend,
            FixedSessionProvider(),
            currentTimeMillis = { now },
        )
        assertIs<SyncRunResult.Synchronized>(coordinator.runOnce())

        now = 9_999_999_999_999L
        local.repository.updateAquarium(aquarium.copy(name = "Older offline edit"))
        assertIs<SyncRunResult.Synchronized>(coordinator.runOnce())
        backend.forceServerDeletion(aquarium.id)

        assertIs<SyncRunResult.Synchronized>(coordinator.runOnce())
        assertEquals(null, local.repository.observeCurrentAquarium().first())
        assertEquals(SyncStatus.SYNCED, local.repository.observeSyncState().first().status)
        local.database.close()
    }

    @Test
    fun `a pending local photo survives a remote copy`() = runTest {
        val local = photoFixture()
        val aquarium = local.repository.createAquarium("Salon", 80.0, VolumeUnit.LITERS)
        val photoId = local.repository.setAquariumPhoto(aquarium.id, byteArrayOf(1)).photoId

        local.repository.commitRemoteCopy(
            InitialAccountCopy(
                listOf(aquarium.copy(name = "Remote", photoId = null)),
                emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            ),
            local.repository.pendingSyncBatch().operations.map(SyncOperation::id),
        )

        val stored = local.repository.loadSetup(aquarium.id)?.aquarium
        assertEquals("Remote", stored?.name)
        assertEquals(photoId, stored?.photoId)
        local.database.close()
    }

    @Test
    fun `a pending local photo survives a remote snapshot`() = runTest {
        val local = photoFixture()
        val aquarium = local.repository.createAquarium("Salon", 80.0, VolumeUnit.LITERS)
        val photoId = local.repository.setAquariumPhoto(aquarium.id, byteArrayOf(1)).photoId

        local.repository.commitRemoteSnapshot(
            RemoteSyncSnapshot(
                InitialAccountCopy(
                    listOf(aquarium.copy(name = "Remote", photoId = null)),
                    emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
                ),
                listOf(RemoteEntityVersion(SyncEntityType.AQUARIUM, aquarium.id, 5L, 1L, null)),
            ),
            local.repository.pendingSyncBatch().operations.map(SyncOperation::id),
        )

        val stored = local.repository.loadSetup(aquarium.id)?.aquarium
        assertEquals("Remote", stored?.name)
        assertEquals(photoId, stored?.photoId)
        local.database.close()
    }

    @Test
    fun `markPhotoSynced marks the upload and enqueues the Aquarium upsert carrying photo_id`() = runTest {
        val local = photoFixture()
        val aquarium = local.repository.createAquarium("Salon", 80.0, VolumeUnit.LITERS)
        val first = local.repository.setAquariumPhoto(aquarium.id, byteArrayOf(1)).photoId
        assertEquals(emptySet(), local.repository.pendingSyncBatch().photoAquariumIds)
        local.repository.acknowledgeSyncOperations(local.repository.pendingSyncBatch().operations.map(SyncOperation::id))

        local.repository.markPhotoSynced(aquarium.id, first)

        assertEquals(emptyList(), local.repository.pendingPhotoSyncs())
        val batch = local.repository.pendingSyncBatch()
        val operation = batch.operations.single()
        assertEquals(SyncEntityType.AQUARIUM to SyncOperationKind.UPSERT, operation.entityType to operation.kind)
        assertEquals(aquarium.id, operation.entityId)
        assertEquals(setOf(aquarium.id), batch.photoAquariumIds)

        val second = local.repository.setAquariumPhoto(aquarium.id, byteArrayOf(2)).photoId
        // The newer photo is not uploaded yet: its row is back to pending and photo_id is not pushed.
        assertEquals(listOf(PendingPhotoSync(aquarium.id, second)), local.repository.pendingPhotoSyncs())
        assertEquals(emptySet(), local.repository.pendingSyncBatch().photoAquariumIds)
        local.repository.acknowledgeSyncOperations(local.repository.pendingSyncBatch().operations.map(SyncOperation::id))

        local.repository.markPhotoSynced(aquarium.id, first)

        assertEquals(listOf(PendingPhotoSync(aquarium.id, second)), local.repository.pendingPhotoSyncs())
        assertEquals(emptyList(), local.repository.pendingSyncBatch().operations)
        local.database.close()
    }

    @Test
    fun `photoIdsPushed drops only the rows still marked uploaded`() = runTest {
        val local = photoFixture()
        val pushed = local.repository.createAquarium("Salon", 80.0, VolumeUnit.LITERS)
        val changed = local.repository.createAquarium("Cuisine", 40.0, VolumeUnit.LITERS, access = AquariumCreationAccess.PRO)
        val pushedPhoto = local.repository.setAquariumPhoto(pushed.id, byteArrayOf(1)).photoId
        val changedPhoto = local.repository.setAquariumPhoto(changed.id, byteArrayOf(2)).photoId
        local.repository.markPhotoSynced(pushed.id, pushedPhoto)
        local.repository.markPhotoSynced(changed.id, changedPhoto)
        val newer = local.repository.setAquariumPhoto(changed.id, byteArrayOf(3)).photoId

        local.repository.photoIdsPushed(setOf(pushed.id, changed.id))

        assertEquals(listOf(PendingPhotoSync(changed.id, newer)), local.repository.pendingPhotoSyncs())
        assertEquals(emptySet(), local.repository.pendingSyncBatch().photoAquariumIds)
        // The pushed photo is no longer protected: the server value now applies.
        local.repository.commitRemoteCopy(
            InitialAccountCopy(
                listOf(pushed.copy(photoId = null)),
                emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            ),
            local.repository.pendingSyncBatch().operations.map(SyncOperation::id),
        )
        assertEquals(null, local.repository.loadSetup(pushed.id)?.aquarium?.photoId)
        local.database.close()
    }

    @Test
    fun `an uploaded photo is still protected from a remote copy until its upsert is pushed`() = runTest {
        val local = photoFixture()
        val aquarium = local.repository.createAquarium("Salon", 80.0, VolumeUnit.LITERS)
        val photoId = local.repository.setAquariumPhoto(aquarium.id, byteArrayOf(1)).photoId
        local.repository.markPhotoSynced(aquarium.id, photoId)

        local.repository.commitRemoteCopy(
            InitialAccountCopy(
                listOf(aquarium.copy(photoId = null)),
                emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            ),
            local.repository.pendingSyncBatch().operations.map(SyncOperation::id),
        )

        assertEquals(photoId, local.repository.loadSetup(aquarium.id)?.aquarium?.photoId)
        local.database.close()
    }

    @Test
    fun `a synced photo survives the initial copy into another account`() = runTest {
        val local = photoFixture()
        val aquarium = local.repository.createAquarium("Salon", 80.0, VolumeUnit.LITERS)
        val synced = local.repository.createAquarium("Cuisine", 40.0, VolumeUnit.LITERS, access = AquariumCreationAccess.PRO)
        val photoId = assertNotNull(local.repository.setAquariumPhoto(aquarium.id, byteArrayOf(1)).photoId)
        val uploadedId = assertNotNull(local.repository.setAquariumPhoto(synced.id, byteArrayOf(2)).photoId)
        local.repository.markPhotoSynced(aquarium.id, photoId)
        local.repository.photoIdsPushed(setOf(aquarium.id))
        // Signed out between the upload and the push of its photo_id.
        local.repository.markPhotoSynced(synced.id, uploadedId)
        local.repository.detachAccountKeepingLocalCopy()

        local.repository.beginAccountMigration("account-2", null)
        local.repository.completeAccountMigration("account-2")
        local.repository.commitRemoteSnapshot(
            RemoteSyncSnapshot(
                InitialAccountCopy(
                    listOf(aquarium.copy(photoId = null), synced.copy(photoId = null)),
                    emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
                ),
                listOf(
                    RemoteEntityVersion(SyncEntityType.AQUARIUM, aquarium.id, 1L, 1L, null),
                    RemoteEntityVersion(SyncEntityType.AQUARIUM, synced.id, 1L, 1L, null),
                ),
            ),
            emptyList(),
        )

        assertEquals(photoId, local.repository.loadSetup(aquarium.id)?.aquarium?.photoId)
        assertEquals(setOf(photoId, uploadedId), local.repository.aquariumPhotoIds())
        assertEquals(
            setOf(PendingPhotoSync(aquarium.id, photoId), PendingPhotoSync(synced.id, uploadedId)),
            local.repository.pendingPhotoSyncs().toSet(),
        )
        local.database.close()
    }

    @Test
    fun `a downloaded photo wakes the observers of the Aquariums`() = runTest {
        val local = photoFixture()
        val aquarium = local.repository.createAquarium("Salon", 80.0, VolumeUnit.LITERS)
        val change = async(Dispatchers.Default) { local.repository.observeChanges().first() }

        // Repeated because the observer registers asynchronously; without the touch this times out.
        withContext(Dispatchers.Default) {
            withTimeout(10_000) {
                while (!change.isCompleted) {
                    local.repository.photosDownloaded(setOf(aquarium.id))
                    delay(50)
                }
            }
        }
        local.database.close()
    }

    @Test
    fun `pendingPhotoSyncs lists the Aquarium with its current photoId`() = runTest {
        val local = photoFixture()
        val aquarium = local.repository.createAquarium("Salon", 80.0, VolumeUnit.LITERS)
        val photoId = assertNotNull(local.repository.setAquariumPhoto(aquarium.id, byteArrayOf(1)).photoId)

        assertEquals(listOf(PendingPhotoSync(aquarium.id, photoId)), local.repository.pendingPhotoSyncs())
        assertEquals(setOf(photoId), local.repository.aquariumPhotoIds())

        local.repository.setAquariumPhoto(aquarium.id, null)

        assertEquals(listOf(PendingPhotoSync(aquarium.id, null)), local.repository.pendingPhotoSyncs())
        assertEquals(emptySet(), local.repository.aquariumPhotoIds())
        local.database.close()
    }

    private fun photoFixture(): Fixture {
        val directory = Files.createTempDirectory("aqualog-sync-photos")
        val database = createAquaLogDatabase(createJvmDatabaseBuilder(directory.resolve("aqualog.db").toString()))
        val ids = generateSequence(1) { it + 1 }.map { "photo-id-$it" }.iterator()
        return Fixture(
            database,
            RoomAquariumRepository(
                database, ids::next, { 100L },
                photoStore = DirectoryAquariumPhotoStore(directory.resolve("photos").toFile()),
            ),
        )
    }

    private fun fixture(path: String, now: () -> Long): Fixture {
        val database = createAquaLogDatabase(createJvmDatabaseBuilder(path))
        val ids = generateSequence(1) { it + 1 }.map { "recovery-id-$it" }.iterator()
        return Fixture(database, RoomAquariumRepository(database, ids::next, now))
    }

    private data class Fixture(
        val database: AquaLogDatabase,
        val repository: RoomAquariumRepository,
    )

    private class FixedSessionProvider : SyncSessionProvider {
        override suspend fun currentSession() = SyncSession("account-1", "access-token")
    }

    private class FailingNetworkBackend : SyncBackend {
        var attempts = 0
        var shouldFail = true
        private var copy = InitialAccountCopy(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

        override suspend fun push(session: SyncSession, batch: PendingSyncBatch) {
            attempts++
            if (shouldFail) throw SyncNetworkException("Network is unreachable")
            copy = InitialAccountCopy(
                batch.aquariums,
                batch.parameterDefinitions,
                batch.sessions,
                batch.measurements,
                batch.maintenanceActions,
                batch.events,
            )
        }

        override suspend fun pull(session: SyncSession): InitialAccountCopy = copy
    }

    private class PullFailingBackend : SyncBackend {
        override suspend fun push(session: SyncSession, batch: PendingSyncBatch) = Unit

        override suspend fun pull(session: SyncSession): InitialAccountCopy {
            throw SyncNetworkException("Network is unreachable")
        }
    }

    private class VersionedAquariumBackend : SyncBackend {
        private var aquarium: Aquarium? = null
        private var serverUpdatedAtEpochMillis = 0L
        private var version = 0L
        private val acceptedOperations = mutableSetOf<String>()

        fun forceServerDeletion(entityId: String) {
            aquarium = null
            this.entityId = entityId
            serverUpdatedAtEpochMillis++
            deletedAtEpochMillis = serverUpdatedAtEpochMillis
            version++
        }

        override suspend fun push(session: SyncSession, batch: PendingSyncBatch) {
            batch.operations.forEach { operation ->
                if (!acceptedOperations.add(operation.operationKey)) return@forEach
                aquarium = batch.aquariums.firstOrNull { it.id == operation.entityId }
                entityId = operation.entityId
                serverUpdatedAtEpochMillis++
                deletedAtEpochMillis = serverUpdatedAtEpochMillis
                    .takeIf { operation.kind == SyncOperationKind.DELETE }
                version++
            }
        }

        override suspend fun pull(session: SyncSession): InitialAccountCopy = pullVersioned(session).copy

        override suspend fun pullVersioned(session: SyncSession) = RemoteSyncSnapshot(
            copy = InitialAccountCopy(
                aquariums = listOfNotNull(aquarium),
                parameterDefinitions = emptyList(),
                sessions = emptyList(),
                measurements = emptyList(),
                maintenanceActions = emptyList(),
                events = emptyList(),
            ),
            versions = entityId?.let { remoteEntityId ->
                listOf(
                    RemoteEntityVersion(
                        entityType = SyncEntityType.AQUARIUM,
                        entityId = remoteEntityId,
                        serverVersion = version,
                        serverUpdatedAtEpochMillis = serverUpdatedAtEpochMillis,
                        deletedAtEpochMillis = deletedAtEpochMillis,
                    ),
                )
            }.orEmpty(),
        )

        private var entityId: String? = null
        private var deletedAtEpochMillis: Long? = null
    }
}
