package com.maximerollin.aqualog.shared.assistance

import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.TaskOccurrence
import com.maximerollin.aqualog.shared.TaskResolution
import com.maximerollin.aqualog.shared.toEpochDay
import java.time.Instant
import java.time.ZoneId

/** What one Aquarium holds that the weekly series reads; never stored, rebuilt from history. */
data class AquariumHistory(
    val setup: AquariumSetup,
    val samples: List<ReadingSample>,
    val occurrences: List<TaskOccurrence>,
    /** See [inhabitantAddedDays]; they feed the follow-up tests of the Sunday summary. */
    val inhabitantAddedDays: List<TaskDate> = emptyList(),
)

private enum class Outcome { MET, OPEN, MISSED }

private const val GRACE_DAYS = 1L

/** Monday of the week holding [epochDay] (1970-01-01 was a Thursday). */
internal fun mondayOf(epochDay: Long) = epochDay - (epochDay + 3).mod(7L)

/** The local day a resolution was recorded, in the occurrence's own zone. */
internal fun TaskOccurrence.resolvedDay(): Long? =
    resolvedAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of(timeZoneId)).toLocalDate().toEpochDay() }

/**
 * Consecutive weeks (Monday to Sunday) of regular care, going back from the last finished week, plus the current week once met.
 * A week requires a reading of every active parameter tested at least weekly and every Tâche due in it done; each may be
 * met until the day after the week ends. A week with no requirement is skipped; the current week never breaks the series.
 * Each Aquarium counts from the week it was created in, read in [zone]; earlier weeks are neutral for it.
 * shortcut: parameters and rhythms are today's, history of setup changes is not stored.
 */
fun weeklySeries(histories: List<AquariumHistory>, today: TaskDate, zone: ZoneId): Int {
    val todayDay = today.toEpochDay()
    val thisWeek = mondayOf(todayDay)
    fun AquariumHistory.firstWeek() =
        mondayOf(Instant.ofEpochMilli(setup.aquarium.createdAtEpochMillis).atZone(zone).toLocalDate().toEpochDay())
    val firstWeek = histories.minOfOrNull { it.firstWeek() } ?: return 0

    fun week(start: Long): Outcome? {
        val outcomes = histories.filter { start >= it.firstWeek() }
            .flatMap { outcomes(it, start, todayDay) }
        return outcomes.firstOrNull { it == Outcome.MISSED } ?: outcomes.firstOrNull { it == Outcome.OPEN } ?: outcomes.firstOrNull()
    }

    var series = if (week(thisWeek) == Outcome.MET) 1 else 0
    var start = thisWeek - 7
    while (start >= firstWeek) {
        // OPEN (the grace day has not passed yet) neither counts nor breaks.
        when (week(start)) {
            Outcome.MISSED -> break
            Outcome.MET -> series++
            else -> Unit
        }
        start -= 7
    }
    return series
}

private fun outcomes(history: AquariumHistory, start: Long, today: Long): List<Outcome> {
    val deadline = start + 6 + GRACE_DAYS
    fun outcome(metDay: Long?) = when {
        metDay != null && metDay <= deadline -> Outcome.MET
        deadline < today -> Outcome.MISSED
        else -> Outcome.OPEN
    }
    val readings = history.setup.parameters
        .filter { it.isActive && (effectiveIntervalDays(it, history.setup.aquarium.profile) ?: Int.MAX_VALUE) <= 7 }
        .map { definition ->
            outcome(history.samples.filter { it.parameterDefinitionId == definition.id }.map { it.day.toEpochDay() }.filter { it >= start }.minOrNull())
        }
    val tasks = history.occurrences
        .filter { it.dueDate.toEpochDay() in start..start + 6 && it.resolution != TaskResolution.POSTPONED }
        .map { occurrence ->
            when (occurrence.resolution) {
                TaskResolution.IGNORED -> Outcome.MISSED
                TaskResolution.COMPLETED -> outcome(occurrence.resolvedDay())
                else -> outcome(null)
            }
        }
    return readings + tasks
}
