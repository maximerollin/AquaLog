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

private val FOLLOW_UP_OFFSETS = listOf(1, 3)

/**
 * Active parameters whose analysis is due on [today]: the earliest due date per parameter, earliest first.
 * Besides the rhythm, a FOLLOW_UP is due for ammonia and nitrite at T+1 and T+3 after a day T with an ALERT
 * or an out-of-range ammonia/nitrite (not while Cycling, where they are expected), and for any parameter
 * whose latest reading is out of range, at that day plus the pistes' retest delay. « Jamais » has no follow-up.
 */
fun dueTests(setup: AquariumSetup, samples: List<ReadingSample>, today: TaskDate): List<DueTest> {
    val profile = setup.aquarium.profile
    val active = setup.parameters.filter { it.isActive }
    val activeById = active.associateBy { it.id }
    val lastDay = samples.groupBy { it.parameterDefinitionId }
        .mapValues { (_, readings) -> readings.maxOf { it.day.toEpochDay() } }
    val triggerDays = if (profile == AquariumProfile.CYCLING) emptyList() else samples
        .filter { sample ->
            val definition = activeById[sample.parameterDefinitionId] ?: return@filter false
            val level = readingLevel(definition, sample.value, profile)
            level == ReadingLevel.ALERT || (definition.parameter in NITROGEN_WASTE && level != ReadingLevel.OK)
        }
        .map { it.day }
        .distinct()

    return active.mapNotNull { definition ->
        val last = lastDay[definition.id]
        val rhythm = effectiveIntervalDays(definition, profile)?.let { interval ->
            DueTest(definition, last?.let { TaskDate.fromEpochDay(it + interval) } ?: today, DueReason.RHYTHM)
        }
        val followUpDays = if (definition.analysisIntervalDays == NEVER_INTERVAL_DAYS) emptyList() else {
            val afterTrigger = if (definition.parameter !in NITROGEN_WASTE) emptyList() else {
                triggerDays.flatMap { day -> FOLLOW_UP_OFFSETS.map { day.plusDays(it) } }
            }
            afterTrigger + listOfNotNull(retestDay(definition, samples, last, profile))
        }
        val followUps = followUpDays
            .filter { last == null || last < it.toEpochDay() }
            .map { DueTest(definition, it, DueReason.FOLLOW_UP) }
        // On a tie the rhythm comes first, so minBy keeps it.
        (listOfNotNull(rhythm) + followUps)
            .filter { it.dueDate.toEpochDay() <= today.toEpochDay() }
            .minByOrNull { it.dueDate.toEpochDay() }
    }.sortedWith(compareBy({ it.dueDate.toEpochDay() }, { it.definition.position }))
}

/** The pistes' retest day after the latest reading day of [definition], when a reading of that day is out of range. */
private fun retestDay(definition: ParameterDefinition, samples: List<ReadingSample>, last: Long?, profile: AquariumProfile): TaskDate? {
    if (last == null) return null
    val direction = samples.filter { it.parameterDefinitionId == definition.id && it.day.toEpochDay() == last }
        .firstNotNullOfOrNull { direction(definition, it.value) } ?: return null
    return TaskDate.fromEpochDay(last + pistesFor(definition.parameter, direction, profile, waterChangeOverdue = false).retestInDays)
}

/** A Cycling Aquarium whose ammonia and nitrite (when followed) both read 0 on the latest test. */
fun looksCycled(setup: AquariumSetup, latest: List<LatestReading>): Boolean {
    if (setup.aquarium.profile != AquariumProfile.CYCLING) return false
    val tracked = setup.parameters.filter { it.isActive && it.parameter in NITROGEN_WASTE }
    if (tracked.isEmpty()) return false
    return tracked.all { definition ->
        latest.firstOrNull { it.parameterDefinitionId == definition.id }?.let { it.value <= 0.0 } == true
    }
}
