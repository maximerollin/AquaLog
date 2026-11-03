package com.maximerollin.aqualog

import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.MaintenanceActionInput
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.ProductEvent
import com.maximerollin.aqualog.shared.RapidSessionInput
import com.maximerollin.aqualog.shared.RoomAquariumRepository
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.TaskInput
import com.maximerollin.aqualog.shared.TaskOccurrence
import com.maximerollin.aqualog.shared.TaskRecurrence
import com.maximerollin.aqualog.shared.TaskResolution
import com.maximerollin.aqualog.shared.TaskUsage
import com.maximerollin.aqualog.shared.TimelineFilter
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.createAquaLogDatabase
import com.maximerollin.aqualog.shared.createJvmDatabaseBuilder
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskCompleterTest {
    private val today = TaskDate(2026, 11, 3)

    private class RecordingScheduler : TaskReminderScheduler {
        val cancelled = mutableListOf<String>()
        val scheduled = mutableListOf<TaskOccurrence>()
        override fun schedule(occurrence: TaskOccurrence) { scheduled += occurrence }
        override fun cancel(occurrenceId: String) { cancelled += occurrenceId }
        override fun reconcile(occurrences: List<TaskOccurrence>) = Unit
        override fun scheduleDueTests(at: TaskDate?) = Unit
        override fun scheduleWeeklySummary() = Unit
        override fun isBatteryOptimizationActive() = false
    }

    private class Fixture {
        private val path = Files.createTempDirectory("aqualog-completer").resolve("aqualog.db").toString()
        private val database = createAquaLogDatabase(createJvmDatabaseBuilder(path))
        private val ids = generateSequence(1) { it + 1 }.map { "id-$it" }.iterator()
        val repository = RoomAquariumRepository(database, ids::next, { 1_700_000_000_000L })
        val scheduler = RecordingScheduler()
        val events = mutableListOf<ProductEvent>()
        val completer = TaskCompleter(repository, scheduler, { events += it }, { 1_800_000_000_000L }, { TaskDate(2026, 11, 3) }, ids::next)

        suspend fun createAquarium() = repository.createConfiguredAquarium(
            name = "Amazonien",
            volume = 120.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.ESTABLISHED,
            parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
        ).aquarium

        suspend fun task(aquariumId: String, due: TaskDate, type: MaintenanceActionType?) = repository.createTask(
            TaskInput(aquariumId, "Task", TaskRecurrence.WEEKLY, due, null, "UTC", type),
        ).occurrence

        fun close() = database.close()
    }

    @Test
    fun `a linked water change records the usual volume and resolves the occurrence`() = runTest {
        val fixture = Fixture()
        val aquariumId = fixture.createAquarium().id
        fixture.repository.saveRapidSession(
            RapidSessionInput(
                aquariumId, 1_700_000_000_000L, "earlier",
                maintenanceActions = listOf(MaintenanceActionInput(MaintenanceActionType.WATER_CHANGE, "30", "L")),
            ),
        )
        val occurrence = fixture.task(aquariumId, today, MaintenanceActionType.WATER_CHANGE)

        val recorded = assertNotNull(fixture.completer.complete(occurrence))

        val change = recorded.maintenanceActions.single()
        assertEquals(MaintenanceActionType.WATER_CHANGE, change.type)
        assertEquals(30.0, change.quantity)
        assertEquals("L", change.unit)
        assertEquals(TaskResolution.COMPLETED, fixture.repository.observeResolvedTaskOccurrences().first().single { it.id == occurrence.id }.resolution)
        assertTrue(occurrence.id in fixture.scheduler.cancelled)
        assertEquals(1, fixture.scheduler.scheduled.size)
        assertEquals(listOf<ProductEvent>(ProductEvent.TaskUsed(TaskUsage.COMPLETED)), fixture.events)
        fixture.close()
    }

    @Test
    fun `a free-text task resolves without a Session`() = runTest {
        val fixture = Fixture()
        val aquariumId = fixture.createAquarium().id
        val occurrence = fixture.task(aquariumId, today, null)

        assertNull(fixture.completer.complete(occurrence))

        assertEquals(TaskResolution.COMPLETED, fixture.repository.observeResolvedTaskOccurrences().first().single { it.id == occurrence.id }.resolution)
        assertTrue(occurrence.id in fixture.scheduler.cancelled)
        assertEquals(listOf<ProductEvent>(ProductEvent.TaskUsed(TaskUsage.COMPLETED)), fixture.events)
        fixture.close()
    }

    @Test
    fun `completing an already resolved occurrence does nothing`() = runTest {
        val fixture = Fixture()
        val aquariumId = fixture.createAquarium().id
        val occurrence = fixture.task(aquariumId, today, MaintenanceActionType.WATER_CHANGE)
        assertNotNull(fixture.completer.complete(occurrence))
        val eventsAfterFirst = fixture.events.toList()

        assertNull(fixture.completer.complete(occurrence))

        assertEquals(eventsAfterFirst, fixture.events)
        assertEquals(1, fixture.repository.observeTimeline(TimelineFilter()).first().size)
        fixture.close()
    }
}
