package com.maximerollin.aqualog

import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.LatestReading
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.TaskOccurrence
import com.maximerollin.aqualog.shared.assistance.ReadingLevel
import com.maximerollin.aqualog.shared.assistance.readingLevel
import kotlin.math.abs
import kotlin.math.pow

private const val MAX_HOME_VALUES = 6
private const val MAX_DUE_TASKS = 3

enum class Trend { UP, DOWN, FLAT }

data class HomeValue(
    val definition: ParameterDefinition,
    val value: Double?,
    val occurredAtEpochMillis: Long?,
    val level: ReadingLevel,
    val isOlderThanLatestReading: Boolean,
    val trend: Trend? = null,
)

data class HomeOverview(
    val values: List<HomeValue>,
    val outsideRangeCount: Int,
    /** The newest Measurement of an active parameter: a Session with only maintenance does not count. */
    val latestReadingAtEpochMillis: Long?,
    val hasAlert: Boolean = false,
)

fun homeOverview(setup: AquariumSetup, readings: List<LatestReading>): HomeOverview {
    val byDefinition = readings.associateBy { it.parameterDefinitionId }
    val active = setup.parameters.filter { it.isActive }.sortedBy { it.position }
    val latestAt = active.mapNotNull { byDefinition[it.id]?.occurredAtEpochMillis }.maxOrNull()
    val all = active.map { definition ->
        val reading = byDefinition[definition.id]
        HomeValue(
            definition = definition,
            value = reading?.value,
            occurredAtEpochMillis = reading?.occurredAtEpochMillis,
            level = reading?.let { readingLevel(definition, it.value) } ?: ReadingLevel.OK,
            isOlderThanLatestReading = reading != null && latestAt != null && reading.occurredAtEpochMillis < latestAt,
            trend = reading?.previousValue?.let { trend(reading.value, it, definition.precision) },
        )
    }
    // Trimming priority: out of range, then measured, then never measured (stable sort keeps position order).
    val values = all.sortedBy { if (it.level != ReadingLevel.OK) 0 else if (it.value != null) 1 else 2 }
        .take(MAX_HOME_VALUES)
        .sortedBy { it.definition.position }
    return HomeOverview(values, all.count { it.level != ReadingLevel.OK }, latestAt, all.any { it.level == ReadingLevel.ALERT })
}

/** Flat when both values show the same at the parameter's precision (half a displayed step apart at most). */
fun trend(latest: Double, previous: Double, precision: Int): Trend {
    val diff = latest - previous
    return when {
        abs(diff) < 0.5 * 10.0.pow(-precision) -> Trend.FLAT
        diff > 0 -> Trend.UP
        else -> Trend.DOWN
    }
}

data class TodayTasks(val due: List<TaskOccurrence>, val next: TaskOccurrence?)

private fun TaskOccurrence.compareDueTo(today: TaskDate) =
    compareValuesBy(dueDate, today, TaskDate::year, TaskDate::month, TaskDate::day)

fun todayTasks(pending: List<TaskOccurrence>, aquariumId: String, today: TaskDate): TodayTasks {
    val own = pending.filter { it.aquariumId == aquariumId }
    val due = own.filter { it.compareDueTo(today) <= 0 }.take(MAX_DUE_TASKS)
    val next = if (due.isEmpty()) own.firstOrNull { it.compareDueTo(today) > 0 } else null
    return TodayTasks(due, next)
}

/** Whether a linked Tâche of that action is pending and overdue for the Aquarium. */
fun overdueLinkedAction(
    pending: List<TaskOccurrence>,
    aquariumId: String,
    type: MaintenanceActionType,
    today: TaskDate,
): Boolean = pending.any {
    it.aquariumId == aquariumId && it.maintenanceActionType == type && it.compareDueTo(today) < 0
}

data class AquariumAlert(val aquarium: Aquarium, val occurrence: TaskOccurrence, val isOverdue: Boolean)

fun otherAquariumAlerts(
    aquariums: List<Aquarium>,
    currentAquariumId: String,
    pending: List<TaskOccurrence>,
    today: TaskDate,
): List<AquariumAlert> = aquariums
    .filter { it.id != currentAquariumId && !it.isArchived }
    .mapNotNull { aquarium ->
        pending.firstOrNull { it.aquariumId == aquarium.id && it.compareDueTo(today) <= 0 }
            ?.let { AquariumAlert(aquarium, it, isOverdue = it.compareDueTo(today) < 0) }
    }
