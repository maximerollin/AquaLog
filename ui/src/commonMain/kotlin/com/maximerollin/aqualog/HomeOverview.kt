package com.maximerollin.aqualog

import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.LatestReading
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.RecordedSession
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.TaskOccurrence
import com.maximerollin.aqualog.shared.isOutsideIndicativeRange

private const val MAX_HOME_VALUES = 6
private const val MAX_DUE_TASKS = 3

data class HomeValue(
    val definition: ParameterDefinition,
    val value: Double?,
    val occurredAtEpochMillis: Long?,
    val isOutsideRange: Boolean,
    val isOlderThanLatestSession: Boolean,
)

data class HomeOverview(
    val values: List<HomeValue>,
    val outsideRangeCount: Int,
    val latestSessionAtEpochMillis: Long?,
)

fun homeOverview(
    setup: AquariumSetup,
    readings: List<LatestReading>,
    latestSession: RecordedSession?,
): HomeOverview {
    val latestAt = latestSession?.session?.occurredAtEpochMillis
    val byDefinition = readings.associateBy { it.parameterDefinitionId }
    val all = setup.parameters.filter { it.isActive }.sortedBy { it.position }.map { definition ->
        val reading = byDefinition[definition.id]
        HomeValue(
            definition = definition,
            value = reading?.value,
            occurredAtEpochMillis = reading?.occurredAtEpochMillis,
            isOutsideRange = reading != null && definition.isOutsideIndicativeRange(reading.value),
            isOlderThanLatestSession = reading != null && latestAt != null && reading.occurredAtEpochMillis < latestAt,
        )
    }
    // Trimming priority: out of range, then measured, then never measured (stable sort keeps position order).
    val values = all.sortedBy { if (it.isOutsideRange) 0 else if (it.value != null) 1 else 2 }
        .take(MAX_HOME_VALUES)
        .sortedBy { it.definition.position }
    return HomeOverview(values, values.count { it.isOutsideRange }, latestAt)
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
