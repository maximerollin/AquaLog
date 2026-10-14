package com.maximerollin.aqualog.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SyncSchedulingTest {
    @Test
    fun `a persisted Room deadline is scheduled exactly after restart without another backoff`() = runTest {
        val path = Files.createTempDirectory("aqualog-sync-deadline")
            .resolve("aqualog.db")
            .toAbsolutePath()
            .toString()
        var now = 5_000L
        val first = fixture(path) { now }
        first.repository.createAquarium("Offline", 80.0, VolumeUnit.LITERS)
        assertEquals(
            SyncRunResult.Retry(1_000L),
            SyncCoordinator(
                first.repository,
                AlwaysFailingBackend(),
                FixedSessionProvider(),
                currentTimeMillis = { now },
            ).runOnce(),
        )
        first.database.close()

        now = 5_500L
        val reopened = fixture(path) { now }
        val backend = CountingBackend()
        val scheduler = RecordingScheduler()
        val execution = ScheduledSyncExecution(
            coordinator = SyncCoordinator(
                reopened.repository,
                backend,
                FixedSessionProvider(),
                currentTimeMillis = { now },
            ),
            scheduler = scheduler,
            currentTimeMillis = { now },
        )

        assertEquals(SyncRunResult.Retry(500L), execution.runOnce())
        assertEquals(0, backend.calls)
        val expectedActions: List<SchedulerAction> = listOf(SchedulerAction.ReplaceDeadline(6_000L))
        assertEquals(expectedActions, scheduler.actions)
        reopened.database.close()
    }

    @Test
    fun `manual retry during upload preserves the running drain and appends an immediate drain`() = runTest {
        var now = 10_000L
        val fixture = fixture(
            Files.createTempDirectory("aqualog-sync-manual-during-upload")
                .resolve("aqualog.db")
                .toAbsolutePath()
                .toString(),
        ) { now }
        fixture.repository.createAquarium("Offline", 80.0, VolumeUnit.LITERS)
        val backend = BlockingFailingBackend()
        val scheduler = RecordingScheduler()
        val execution = ScheduledSyncExecution(
            coordinator = SyncCoordinator(
                fixture.repository,
                backend,
                FixedSessionProvider(),
                currentTimeMillis = { now },
            ),
            scheduler = scheduler,
            currentTimeMillis = { now },
        )

        val running = async { execution.runOnce() }
        backend.started.await()
        fixture.repository.retrySyncNow()
        execution.requestImmediate()
        backend.release.complete(Unit)

        assertEquals(SyncRunResult.Retry(0L), running.await())
        val state = fixture.repository.observeSyncState().first()
        assertEquals(SyncStatus.PENDING, state.status)
        assertEquals(null, state.nextRetryAtEpochMillis)
        assertTrue(scheduler.actions.none { it is SchedulerAction.ReplaceDeadline })
        assertTrue(scheduler.actions.count { it == SchedulerAction.AppendImmediate } >= 1)
        fixture.database.close()
    }

    private fun fixture(path: String, now: () -> Long): Fixture {
        val database = createAquaLogDatabase(createJvmDatabaseBuilder(path))
        val ids = generateSequence(1) { it + 1 }.map { "scheduling-id-$it" }.iterator()
        return Fixture(database, RoomAquariumRepository(database, ids::next, now))
    }

    private data class Fixture(
        val database: AquaLogDatabase,
        val repository: RoomAquariumRepository,
    )

    private class FixedSessionProvider : SyncSessionProvider {
        override suspend fun currentSession() = SyncSession("account-1", "access-token")
    }

    private class AlwaysFailingBackend : SyncBackend {
        override suspend fun push(session: SyncSession, batch: PendingSyncBatch) {
            throw SyncNetworkException("offline")
        }

        override suspend fun pull(session: SyncSession) = emptyCopy()
    }

    private class CountingBackend : SyncBackend {
        var calls = 0
        override suspend fun push(session: SyncSession, batch: PendingSyncBatch) {
            calls++
        }

        override suspend fun pull(session: SyncSession): InitialAccountCopy {
            calls++
            return emptyCopy()
        }
    }

    private class BlockingFailingBackend : SyncBackend {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        override suspend fun push(session: SyncSession, batch: PendingSyncBatch) {
            started.complete(Unit)
            release.await()
            throw SyncNetworkException("late failure")
        }

        override suspend fun pull(session: SyncSession) = emptyCopy()
    }

    private class RecordingScheduler : SyncWorkScheduler {
        val actions = mutableListOf<SchedulerAction>()

        override fun appendImmediate() {
            actions += SchedulerAction.AppendImmediate
        }

        override fun replaceDeadline(deadlineEpochMillis: Long) {
            actions += SchedulerAction.ReplaceDeadline(deadlineEpochMillis)
        }

        override fun cancelDeadline() {
            actions += SchedulerAction.CancelDeadline
        }
    }

    private sealed interface SchedulerAction {
        data object AppendImmediate : SchedulerAction
        data object CancelDeadline : SchedulerAction
        data class ReplaceDeadline(val deadlineEpochMillis: Long) : SchedulerAction
    }

    private companion object {
        fun emptyCopy() = InitialAccountCopy(
            aquariums = emptyList(),
            parameterDefinitions = emptyList(),
            sessions = emptyList(),
            measurements = emptyList(),
            maintenanceActions = emptyList(),
            events = emptyList(),
        )
    }
}
