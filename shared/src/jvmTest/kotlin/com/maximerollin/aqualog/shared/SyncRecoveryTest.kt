package com.maximerollin.aqualog.shared

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
    fun `server last write wins per Aquarium without replacing unrelated local entities`() = runTest {
        var now = 100L
        val local = fixture(
            Files.createTempDirectory("aqualog-sync-lww").resolve("aqualog.db").toString(),
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
        local.repository.updateAquarium(aquarium.copy(name = "Older offline edit"))
        backend.forceServerAquarium(aquarium.copy(name = "Newer server edit"), updatedAtEpochMillis = 300L)

        assertIs<SyncRunResult.Synchronized>(coordinator.runOnce())
        assertEquals("Newer server edit", local.repository.observeCurrentAquarium().first()?.name)
        assertEquals(SyncStatus.SYNCED, local.repository.observeSyncState().first().status)
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

        now = 200L
        local.repository.updateAquarium(aquarium.copy(name = "Older offline edit"))
        backend.forceServerDeletion(aquarium.id, updatedAtEpochMillis = 300L)

        assertIs<SyncRunResult.Synchronized>(coordinator.runOnce())
        assertEquals(null, local.repository.observeCurrentAquarium().first())
        assertEquals(SyncStatus.SYNCED, local.repository.observeSyncState().first().status)
        local.database.close()
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
        private var updatedAtEpochMillis = Long.MIN_VALUE
        private var version = 0L
        private val acceptedOperations = mutableSetOf<String>()

        fun forceServerAquarium(value: Aquarium, updatedAtEpochMillis: Long) {
            aquarium = value
            entityId = value.id
            this.updatedAtEpochMillis = updatedAtEpochMillis
            deletedAtEpochMillis = null
            version++
        }

        fun forceServerDeletion(entityId: String, updatedAtEpochMillis: Long) {
            aquarium = null
            this.entityId = entityId
            this.updatedAtEpochMillis = updatedAtEpochMillis
            deletedAtEpochMillis = updatedAtEpochMillis
            version++
        }

        override suspend fun push(session: SyncSession, batch: PendingSyncBatch) {
            batch.operations.forEach { operation ->
                if (!acceptedOperations.add(operation.operationKey)) return@forEach
                if (operation.clientUpdatedAtEpochMillis >= updatedAtEpochMillis) {
                    aquarium = batch.aquariums.firstOrNull { it.id == operation.entityId }
                    entityId = operation.entityId
                    updatedAtEpochMillis = operation.clientUpdatedAtEpochMillis
                    deletedAtEpochMillis = operation.clientUpdatedAtEpochMillis
                        .takeIf { operation.kind == SyncOperationKind.DELETE }
                    version++
                }
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
                        serverUpdatedAtEpochMillis = updatedAtEpochMillis,
                        deletedAtEpochMillis = deletedAtEpochMillis,
                    ),
                )
            }.orEmpty(),
        )

        private var entityId: String? = null
        private var deletedAtEpochMillis: Long? = null
    }
}
