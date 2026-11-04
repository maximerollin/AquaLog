package com.maximerollin.aqualog.shared.assistance

import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.TaskResolution
import com.maximerollin.aqualog.shared.toEpochDay
import kotlin.math.abs
import kotlin.math.pow

/** The Sunday summary of one Aquarium; [changes] and [nextWeek] are the Pro part. */
data class WeeklySummary(
    val testsDone: Int,
    val tasksCompleted: Int,
    val series: Int,
    val changes: List<ValueChange>,
    val nextWeek: List<DueTest>,
)

data class ValueChange(val definition: ParameterDefinition, val from: Double, val to: Double)

// Absorbs floating point error so that 7.1 -> 7.0 counts as one step of 0.1.
private const val EPSILON = 1e-9

/**
 * The week from [weekStart] (a Monday) to the Sunday after: readings taken, Tâches completed in it (by resolution day,
 * whatever their due date), active parameters whose last value of the week moved from the last one before by at least
 * one step of their precision, and the tests due by the following Sunday. [AquariumHistory.samples] are newest first.
 */
fun weeklySummary(history: AquariumHistory, weekStart: TaskDate, series: Int): WeeklySummary {
    val start = weekStart.toEpochDay()
    val week = start..start + 6
    val changes = history.setup.parameters.filter { it.isActive }.sortedBy { it.position }.mapNotNull { definition ->
        val own = history.samples.filter { it.parameterDefinitionId == definition.id }
        val to = own.firstOrNull { it.day.toEpochDay() in week }?.value ?: return@mapNotNull null
        val from = own.firstOrNull { it.day.toEpochDay() < start }?.value ?: return@mapNotNull null
        ValueChange(definition, from, to).takeIf { abs(to - from) + EPSILON >= 10.0.pow(-definition.precision) }
    }
    return WeeklySummary(
        testsDone = history.samples.count { it.day.toEpochDay() in week },
        tasksCompleted = history.occurrences.count { it.resolution == TaskResolution.COMPLETED && it.resolvedDay() in week },
        series = series,
        changes = changes,
        nextWeek = dueTests(history.setup, history.samples, TaskDate.fromEpochDay(start + 13), history.inhabitantAddedDays),
    )
}

/** The Monday of the week the summary covers: this week on Sunday, the week just ended on Monday, otherwise null. */
fun summaryWeekStart(today: TaskDate): TaskDate? {
    val day = today.toEpochDay()
    val monday = mondayOf(day)
    return when (day - monday) {
        6L -> monday
        0L -> monday - 7
        else -> null
    }?.let(TaskDate::fromEpochDay)
}
