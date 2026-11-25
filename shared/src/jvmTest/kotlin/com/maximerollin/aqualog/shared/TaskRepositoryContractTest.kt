package com.maximerollin.aqualog.shared

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    private suspend fun TaskFixture.linkedTask(
        aquariumId: String,
        due: TaskDate,
        recurrence: TaskRecurrence = TaskRecurrence.DAILY,
        type: MaintenanceActionType = MaintenanceActionType.WATER_CHANGE,
    ) = repository.createTask(
        TaskInput(aquariumId, "Water change", recurrence, due, null, "Europe/Paris", type),
    )

    @Test
    fun `saving a water change completes the due linked water-change task once`() = runTest {
        val fixture = taskFixture()
        val aquariumId = fixture.createAquarium().aquarium.id
        val today = TaskDate(2027, 3, 10)
        val created = fixture.linkedTask(aquariumId, today)
        val other = fixture.linkedTask(aquariumId, today, type = MaintenanceActionType.VACUUM)

        val first = fixture.repository.completeLinkedTasks(aquariumId, setOf(MaintenanceActionType.WATER_CHANGE), today, 1_800_000_000_000L)
        val second = fixture.repository.completeLinkedTasks(aquariumId, setOf(MaintenanceActionType.WATER_CHANGE), today, 1_800_000_000_001L)

        assertEquals(listOf(created.occurrence.id), first.map { it.resolved.id })
        assertEquals(TaskResolution.COMPLETED, first.single().resolved.resolution)
        assertEquals(TaskDate(2027, 3, 11), first.single().nextOccurrence?.dueDate)
        assertTrue(second.isEmpty())
        assertTrue(fixture.repository.observePendingTaskOccurrences().first().any { it.id == other.occurrence.id })
        fixture.close()
    }

    @Test
    fun `a future linked occurrence is untouched`() = runTest {
        val fixture = taskFixture()
        val aquariumId = fixture.createAquarium().aquarium.id
        val created = fixture.linkedTask(aquariumId, TaskDate(2027, 3, 12))

        val results = fixture.repository.completeLinkedTasks(
            aquariumId, setOf(MaintenanceActionType.WATER_CHANGE), TaskDate(2027, 3, 10), 1_800_000_000_000L,
        )

        assertTrue(results.isEmpty())
        assertEquals(listOf(created.occurrence.id), fixture.repository.observePendingTaskOccurrences().first().map { it.id })
        fixture.close()
    }

    @Test
    fun `ticking then saving the same action the same day does not complete the next occurrence`() = runTest {
        val fixture = taskFixture()
        val aquariumId = fixture.createAquarium().aquarium.id
        val today = TaskDate(2027, 3, 10)
        fixture.linkedTask(aquariumId, today)
        val types = setOf(MaintenanceActionType.WATER_CHANGE)

        val ticked = fixture.repository.completeLinkedTasks(aquariumId, types, today, 1_800_000_000_000L)
        val saved = fixture.repository.completeLinkedTasks(aquariumId, types, today, 1_800_000_000_500L)

        assertEquals(1, ticked.size)
        assertTrue(saved.isEmpty())
        assertEquals(listOf(ticked.single().nextOccurrence?.id), fixture.repository.observePendingTaskOccurrences().first().map { it.id })
        fixture.close()
    }

    @Test
    fun `an overdue weekly linked task is completed once and its next occurrence falls after today`() = runTest {
        val fixture = taskFixture()
        val aquariumId = fixture.createAquarium().aquarium.id
        val today = TaskDate(2027, 3, 24)
        fixture.linkedTask(aquariumId, TaskDate(2027, 3, 10), TaskRecurrence.WEEKLY)
        val types = setOf(MaintenanceActionType.WATER_CHANGE)

        val first = fixture.repository.completeLinkedTasks(aquariumId, types, today, 1_800_000_000_000L)
        val second = fixture.repository.completeLinkedTasks(aquariumId, types, today, 1_800_000_000_500L)

        assertEquals(1, first.size)
        assertEquals(TaskDate(2027, 3, 31), first.single().nextOccurrence?.dueDate)
        assertTrue(second.isEmpty())
        fixture.close()
    }

    @Test
    fun `ticking a future linked occurrence resolves exactly that occurrence`() = runTest {
        val fixture = taskFixture()
        val aquariumId = fixture.createAquarium().aquarium.id
        val today = TaskDate(2027, 3, 10)
        val future = fixture.linkedTask(aquariumId, TaskDate(2027, 3, 12), TaskRecurrence.WEEKLY)
        val types = setOf(MaintenanceActionType.WATER_CHANGE)

        val results = fixture.repository.completeLinkedTasks(aquariumId, types, today, 1_800_000_000_000L, alsoOccurrenceId = future.occurrence.id)

        assertEquals(listOf(future.occurrence.id), results.map { it.resolved.id })
        assertEquals(TaskDate(2027, 3, 19), results.single().nextOccurrence?.dueDate)
        assertEquals(listOf(results.single().nextOccurrence?.id), fixture.repository.observePendingTaskOccurrences().first().map { it.id })
        fixture.close()
    }

    @Test
    fun `task recurrences are observed by task id`() = runTest {
        val fixture = taskFixture()
        val aquariumId = fixture.createAquarium().aquarium.id
        val weekly = fixture.repository.createTask(
            TaskInput(aquariumId, "Water change", TaskRecurrence.WEEKLY, TaskDate(2027, 1, 10), null, "Europe/Paris"),
        ).task
        val once = fixture.repository.createTask(
            TaskInput(aquariumId, "Glass", TaskRecurrence.ONCE, TaskDate(2027, 1, 11), null, "Europe/Paris"),
        ).task

        val recurrences = fixture.repository.observeTaskRecurrences().first()

        assertEquals(mapOf(weekly.id to TaskRecurrence.WEEKLY, once.id to TaskRecurrence.ONCE), recurrences)
        fixture.close()
    }

    @Test
    fun `a stored recurrence the app does not know is left out of the map`() = runTest {
        val path = Files.createTempDirectory("aqualog-tasks").resolve("aqualog.db").toString()
        val fixture = taskFixture(path)
        val aquariumId = fixture.createAquarium().aquarium.id
        val weekly = fixture.repository.createTask(
            TaskInput(aquariumId, "Water change", TaskRecurrence.WEEKLY, TaskDate(2027, 1, 10), null, "Europe/Paris"),
        ).task
        val unknown = fixture.repository.createTask(
            TaskInput(aquariumId, "Glass", TaskRecurrence.ONCE, TaskDate(2027, 1, 11), null, "Europe/Paris"),
        ).task
        fixture.close()
        // A value written by a newer version (or a damaged row): the Room schema is unchanged, the column is free text.
        BundledSQLiteDriver().open(path).use { connection ->
            connection.execSQL("UPDATE tasks SET recurrence = 'fortnightly' WHERE id = '${unknown.id}'")
        }

        val reopened = taskFixture(path)
        val recurrences = reopened.repository.observeTaskRecurrences().first()

        assertEquals(mapOf(weekly.id to TaskRecurrence.WEEKLY), recurrences)
        reopened.close()
    }

    @Test
    fun `Free keeps two Aquariums in use, archived ones aside, and restores one only while a place is free`() = runTest {
        val fixture = taskFixture()
        val first = fixture.createAquarium().aquarium.id
        val second = fixture.createAquarium().aquarium.id
        assertFailsWith<FreeAquariumLimitReached> { fixture.createAquarium() }

        fixture.repository.archiveAquarium(first)
        val third = fixture.createAquarium().aquarium.id
        assertFailsWith<FreeAquariumLimitReached> { fixture.repository.unarchiveAquarium(first) }
        assertEquals(setOf(second, third), fixture.repository.observeAquariums().first().map { it.id }.toSet())

        assertEquals(false, fixture.repository.unarchiveAquarium(first, AquariumCreationAccess.PRO).isArchived)
        assertEquals(3, fixture.repository.observeAquariums().first().size)
        fixture.close()
    }

    @Test
    fun `editing a Task changes it and its pending Occurrence, which keeps its id`() = runTest {
        val fixture = taskFixture()
        val aquariumId = fixture.createAquarium().aquarium.id
        val created = fixture.repository.createTask(
            TaskInput(aquariumId, "Dose", TaskRecurrence.DAILY, TaskDate(2027, 1, 10), null, "Europe/Paris"),
        )

        val edited = fixture.repository.updateTask(
            TaskEditInput(
                created.occurrence.id, "  Dose iron ", TaskRecurrence.MONTHLY, TaskDate(2027, 1, 31), 8 * 60, "Europe/Paris",
                MaintenanceActionType.FERTILIZATION,
            ),
        )

        val pending = fixture.repository.observePendingTaskOccurrences().first().single()
        assertEquals(edited.occurrence, pending)
        assertEquals(created.occurrence.id, pending.id)
        assertEquals("Dose iron", pending.title)
        assertEquals(TaskDate(2027, 1, 31), pending.dueDate)
        assertEquals(8 * 60, pending.minuteOfDay)
        assertEquals(MaintenanceActionType.FERTILIZATION, pending.maintenanceActionType)
        assertEquals(mapOf(created.task.id to TaskRecurrence.MONTHLY), fixture.repository.observeTaskRecurrences().first())
        // The month-end anchor follows the new due date.
        val next = fixture.repository.resolveTaskOccurrence(
            TaskResolutionInput(pending.id, TaskResolution.IGNORED, 1_800_000_000_000L),
        ).nextOccurrence
        assertEquals(TaskDate(2027, 2, 28), next?.dueDate)
        fixture.close()
    }

    @Test
    fun `deleting a Task removes what is pending and keeps what was resolved`() = runTest {
        val fixture = taskFixture()
        val aquariumId = fixture.createAquarium().aquarium.id
        fun input(title: String) = TaskInput(aquariumId, title, TaskRecurrence.DAILY, TaskDate(2027, 1, 10), null, "Europe/Paris")
        val mistake = fixture.repository.createTask(input("Mistake"))
        val dosing = fixture.repository.createTask(input("Dose"))
        val done = fixture.repository.resolveTaskOccurrence(
            TaskResolutionInput(dosing.occurrence.id, TaskResolution.COMPLETED, 1_800_000_000_000L),
        )

        fixture.repository.deleteTask(mistake.task.id)
        fixture.repository.deleteTask(dosing.task.id)

        assertTrue(fixture.repository.observePendingTaskOccurrences().first().isEmpty())
        assertEquals(listOf(done.resolved.id), fixture.repository.observeResolvedTaskOccurrences().first().map { it.id })
        assertEquals(setOf(dosing.task.id), fixture.repository.observeTaskRecurrences().first().keys)
        fixture.close()
    }

    @Test
    fun `the Occurrences of an archived Aquarium are no longer pending`() = runTest {
        val fixture = taskFixture()
        val kept = fixture.createAquarium().aquarium.id
        val archived = fixture.repository.createConfiguredAquarium(
            name = "Nano",
            volume = 30.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.ESTABLISHED,
            parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            access = AquariumCreationAccess.PRO,
        ).aquarium.id
        listOf(kept, archived).forEach {
            fixture.repository.createTask(TaskInput(it, "Glass", TaskRecurrence.WEEKLY, TaskDate(2027, 1, 10), null, "Europe/Paris"))
        }

        fixture.repository.archiveAquarium(archived)

        assertEquals(listOf(kept), fixture.repository.observePendingTaskOccurrences().first().map { it.aquariumId })
        fixture.close()
    }

    private fun taskFixture(
        path: String = Files.createTempDirectory("aqualog-tasks").resolve("aqualog.db").toString(),
    ): TaskFixture {
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
