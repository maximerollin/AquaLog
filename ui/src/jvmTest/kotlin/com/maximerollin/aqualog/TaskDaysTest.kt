package com.maximerollin.aqualog

import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.TaskOccurrence
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Tasks screen's days: an overdue Occurrence joins today, first; later days keep their own group (lot 4a, Task 3). */
class TaskDaysTest {
    private val today = TaskDate(2026, 11, 17)

    private fun occurrence(id: String, due: TaskDate, minuteOfDay: Int?) =
        TaskOccurrence(id, "t-$id", "a", "Tâche $id", due, minuteOfDay, "Europe/Paris", null, null)

    @Test
    fun `overdue joins today first`() {
        val todayEvening = occurrence("evening", TaskDate(2026, 11, 17), 18 * 60)
        val todayUntimed = occurrence("untimed", TaskDate(2026, 11, 17), null)
        val overdue = occurrence("overdue", TaskDate(2026, 11, 15), 9 * 60)

        val groups = taskDayGroups(listOf(todayUntimed, todayEvening, overdue), today)

        assertEquals(listOf(TaskDayGroup(today, listOf(overdue, todayEvening, todayUntimed))), groups)
    }

    @Test
    fun `later days keep their own group in order`() {
        val first = occurrence("first", TaskDate(2026, 11, 18), null)
        val later = occurrence("later", TaskDate(2026, 11, 20), null)
        val second = occurrence("second", TaskDate(2026, 11, 18), null)

        val groups = taskDayGroups(listOf(first, later, second), today)

        assertEquals(
            listOf(
                TaskDayGroup(TaskDate(2026, 11, 18), listOf(first, second)),
                TaskDayGroup(TaskDate(2026, 11, 20), listOf(later)),
            ),
            groups,
        )
    }

    @Test
    fun `no occurrence no group`() {
        assertEquals(emptyList(), taskDayGroups(emptyList(), today))
    }
}
