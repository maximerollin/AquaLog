package com.maximerollin.aqualog.shared.assistance

import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.LatestReading
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.plusDays
import com.maximerollin.aqualog.shared.toEpochDay

/** « Jamais » is stored as this positive sentinel so every existing "null or > 0" check still accepts it. */
const val NEVER_INTERVAL_DAYS = 9999

/** Days between two analyses of [parameter] by default (« — » in the rule table is null). */
fun defaultIntervalDays(profile: AquariumProfile, parameter: BuiltInParameter): Int? = when (profile) {
    AquariumProfile.ESTABLISHED -> when (parameter) {
        BuiltInParameter.PH, BuiltInParameter.NITRATE -> 7
        BuiltInParameter.NITRITE -> 14
        BuiltInParameter.GH, BuiltInParameter.KH -> 30
        else -> null
    }
    AquariumProfile.CYCLING -> when (parameter) {
        BuiltInParameter.PH -> 7
        BuiltInParameter.AMMONIA, BuiltInParameter.NITRITE, BuiltInParameter.NITRATE -> 2
        BuiltInParameter.GH, BuiltInParameter.KH -> 30
        else -> null
    }
    AquariumProfile.PLANTED_SHRIMP -> when (parameter) {
        BuiltInParameter.PH, BuiltInParameter.NITRATE, BuiltInParameter.GH, BuiltInParameter.TDS -> 7
        BuiltInParameter.NITRITE -> 14
        BuiltInParameter.KH -> 30
        else -> null
    }
}

/** The Aquarium's own interval wins; [NEVER_INTERVAL_DAYS] means never, so null. */
fun effectiveIntervalDays(definition: ParameterDefinition, profile: AquariumProfile): Int? =
    when (val own = definition.analysisIntervalDays) {
        null -> defaultIntervalDays(profile, definition.parameter)
        NEVER_INTERVAL_DAYS -> null
        else -> own
    }

data class ReadingSample(val parameterDefinitionId: String, val value: Double, val day: TaskDate)

enum class DueReason { RHYTHM, FOLLOW_UP }

data class DueTest(val definition: ParameterDefinition, val dueDate: TaskDate, val reason: DueReason)

private val FOLLOW_UP_PARAMETERS = setOf(BuiltInParameter.NITRITE, BuiltInParameter.AMMONIA)
private val FOLLOW_UP_OFFSETS = listOf(1, 3)

/** Active parameters whose analysis is due on [today]: the earliest due date per parameter, earliest first. */
fun dueTests(setup: AquariumSetup, samples: List<ReadingSample>, today: TaskDate): List<DueTest> {
    val active = setup.parameters.filter { it.isActive }
    val activeById = active.associateBy { it.id }
    val lastDay = samples.groupBy { it.parameterDefinitionId }
        .mapValues { (_, readings) -> readings.maxOf { it.day.toEpochDay() } }
    val triggerDays = samples
        .filter { sample -> activeById[sample.parameterDefinitionId]?.let { readingLevel(it, sample.value) != ReadingLevel.OK } == true }
        .map { it.day }
        .distinct()

    return active.mapNotNull { definition ->
        val last = lastDay[definition.id]
        val rhythm = effectiveIntervalDays(definition, setup.aquarium.profile)?.let { interval ->
            DueTest(definition, last?.let { TaskDate.fromEpochDay(it + interval) } ?: today, DueReason.RHYTHM)
        }
        val followUps = if (definition.parameter !in FOLLOW_UP_PARAMETERS) emptyList() else {
            triggerDays.flatMap { day -> FOLLOW_UP_OFFSETS.map { day.plusDays(it) } }
                .filter { last == null || last < it.toEpochDay() }
                .map { DueTest(definition, it, DueReason.FOLLOW_UP) }
        }
        (listOfNotNull(rhythm) + followUps)
            .filter { it.dueDate.toEpochDay() <= today.toEpochDay() }
            .minByOrNull { it.dueDate.toEpochDay() }
    }.sortedWith(compareBy({ it.dueDate.toEpochDay() }, { it.definition.position }))
}

/** A Cycling Aquarium whose ammonia and nitrite (when followed) both read 0 on the latest test. */
fun looksCycled(setup: AquariumSetup, latest: List<LatestReading>): Boolean {
    if (setup.aquarium.profile != AquariumProfile.CYCLING) return false
    val tracked = setup.parameters.filter { it.isActive && it.parameter in FOLLOW_UP_PARAMETERS }
    if (tracked.isEmpty()) return false
    return tracked.all { definition ->
        latest.firstOrNull { it.parameterDefinitionId == definition.id }?.let { it.value <= 0.0 } == true
    }
}
