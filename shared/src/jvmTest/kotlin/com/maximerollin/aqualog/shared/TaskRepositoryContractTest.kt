package com.maximerollin.aqualog.shared

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskRepositoryContractTest {
    @Test
    fun `recurrence rules compute deterministic daily weekly and month-end dates`() {
        assertEquals(TaskDate(2027, 2, 1), nextTaskDate(TaskDate(2027, 1, 31), TaskRecurrence.DAILY, 31))
        assertEquals(TaskDate(2027, 2, 7), nextTaskDate(TaskDate(2027, 1, 31), TaskRecurrence.WEEKLY, 31))
        assertEquals(TaskDate(2027, 2, 28), nextTaskDate(TaskDate(2027, 1, 31), TaskRecurrence.MONTHLY, 31))
        assertEquals(TaskDate(2027, 3, 31), nextTaskDate(TaskDate(2027, 2, 28), TaskRecurrence.MONTHLY, 31))
    }

    @Test
    fun `completing an Occurrence records actual time schedules the next due date and creates no Action`() = runTest {
        val fixture = taskFixture()
        val aquarium = fixture.createAquarium()
        val created = fixture.repository.createTask(
            TaskInput(
                aquariumId = aquarium.aquarium.id,
                title = "Water change",
                recurrence = TaskRecurrence.WEEKLY,
                firstDueDate = TaskDate(2027, 1, 10),
                minuteOfDay = 18 * 60 + 30,
                timeZoneId = "Europe/Paris",
            ),
        )

        val result = fixture.repository.resolveTaskOccurrence(
            TaskResolutionInput(
                occurrenceId = created.occurrence.id,
                resolution = TaskResolution.COMPLETED,
                resolvedAtEpochMillis = 1_800_000_000_000L,
            ),
        )

        assertEquals(TaskResolution.COMPLETED, result.resolved.resolution)
        assertEquals(1_800_000_000_000L, result.resolved.resolvedAtEpochMillis)
        assertEquals(TaskDate(2027, 1, 17), result.nextOccurrence?.dueDate)
        assertTrue(fixture.repository.observeTimeline(TimelineFilter()).first().isEmpty())
        assertEquals(result.resolved.id, fixture.repository.observeResolvedTaskOccurrences().first().single().id)
        fixture.close()
    }

    @Test
    fun `completed postponed and ignored resolutions remain observable with their actual dates`() = runTest {
        val fixture = taskFixture()
        val aquariumId = fixture.createAquarium().aquarium.id
        val occurrences = listOf("Dose", "Filter", "Glass").mapIndexed { index, title ->
            fixture.repository.createTask(
                TaskInput(
                    aquariumId = aquariumId,
                    title = title,
                    recurrence = TaskRecurrence.ONCE,
                    firstDueDate = TaskDate(2027, 2, 10 + index),
                    minuteOfDay = null,
                    timeZoneId = "Europe/Paris",
                ),
            ).occurrence
        }

        fixture.repository.resolveTaskOccurrence(
            TaskResolutionInput(occurrences[0].id, TaskResolution.COMPLETED, 1_800_000_000_001L),
        )
        val postponed = fixture.repository.resolveTaskOccurrence(
            TaskResolutionInput(
                occurrences[1].id,
                TaskResolution.POSTPONED,
                1_800_000_000_002L,
                postponedUntil = TaskDate(2027, 2, 20),
            ),
        )
        fixture.repository.resolveTaskOccurrence(
            TaskResolutionInput(occurrences[2].id, TaskResolution.IGNORED, 1_800_000_000_003L),
        )

        val resolved = fixture.repository.observeResolvedTaskOccurrences().first()
        assertEquals(setOf(TaskResolution.COMPLETED, TaskResolution.POSTPONED, TaskResolution.IGNORED), resolved.mapNotNull { it.resolution }.toSet())
        assertEquals(setOf(1_800_000_000_001L, 1_800_000_000_002L, 1_800_000_000_003L), resolved.mapNotNull { it.resolvedAtEpochMillis }.toSet())
        assertEquals(TaskDate(2027, 2, 20), postponed.nextOccurrence?.dueDate)
        assertEquals(listOf(postponed.nextOccurrence?.id), fixture.repository.observePendingTaskOccurrences().first().map { it.id })
        fixture.close()
    }

    private fun taskFixture(): TaskFixture {
        val path = Files.createTempDirectory("aqualog-tasks").resolve("aqualog.db").toString()
        val database = createAquaLogDatabase(createJvmDatabaseBuilder(path))
        val ids = generateSequence(1) { it + 1 }.map { "id-$it" }.iterator()
        return TaskFixture(database, RoomAquariumRepository(database, ids::next, { 1_700_000_000_000L }))
    }

    private class TaskFixture(
        private val database: AquaLogDatabase,
        val repository: RoomAquariumRepository,
    ) {
        suspend fun createAquarium() = repository.createConfiguredAquarium(
            name = "Amazonien",
            volume = 120.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.ESTABLISHED,
            parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
        )

        fun close() = database.close()
    }
}
