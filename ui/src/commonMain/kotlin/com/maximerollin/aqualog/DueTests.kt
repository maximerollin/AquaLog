package com.maximerollin.aqualog

import com.maximerollin.aqualog.resources.Res
import com.maximerollin.aqualog.resources.list_pair
import com.maximerollin.aqualog.shared.AquariumRepository
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.assistance.AquariumHistory
import com.maximerollin.aqualog.shared.assistance.DueTest
import com.maximerollin.aqualog.shared.assistance.ReadingSample
import com.maximerollin.aqualog.shared.assistance.dueTests
import com.maximerollin.aqualog.shared.plusDays
import com.maximerollin.aqualog.shared.toEpochDay
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.getString

private val REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

/** 09:00 local of the earliest due date, or the next 09:00 after [now] when that moment has passed; null when nothing is due. */
fun nextDueTestReminder(dueDates: List<TaskDate>, zone: ZoneId, now: Long): Long? {
    val earliest = dueDates.minByOrNull { it.toEpochDay() } ?: return null
    val nowDay = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val dueDay = LocalDate.of(earliest.year, earliest.month, earliest.day)
    return generateSequence(maxOf(dueDay, nowDay)) { it.plusDays(1) }
        .map { it.atTime(REMINDER_TIME).atZone(zone).toInstant().toEpochMilli() }
        .first { it > now }
}

/**
 * The first day from [today] on which a test of [setup] is due, or null when none is within 31 days.
 * shortcut: the longest offered rhythm is 30 days; a longer interval synced from elsewhere waits until it is within range.
 */
fun nextDueDate(setup: AquariumSetup, samples: List<ReadingSample>, today: TaskDate): TaskDate? =
    (0..31).asSequence().map(today::plusDays).firstOrNull { dueTests(setup, samples, it).isNotEmpty() }

class DueTestSummary(val dueToday: List<DueTest>, val nextDueDate: TaskDate?)

/** Due tests of every non-archived Aquarium, read once (reminders cover all Aquariums, not only the displayed one). */
suspend fun AquariumRepository.dueTestSummary(today: TaskDate, timeZoneId: String): DueTestSummary {
    val inputs = observeAquariums().first().mapNotNull { aquarium ->
        loadSetup(aquarium.id)?.let { it to observeReadingSamples(aquarium.id, timeZoneId).first() }
    }
    return DueTestSummary(
        dueToday = inputs.flatMap { (setup, samples) -> dueTests(setup, samples, today) },
        nextDueDate = inputs.mapNotNull { (setup, samples) -> nextDueDate(setup, samples, today) }
            .minByOrNull { it.toEpochDay() },
    )
}

/** Every non-archived Aquarium with its readings, pending and resolved Tâches, read once. */
suspend fun AquariumRepository.aquariumHistories(timeZoneId: String): List<AquariumHistory> {
    val occurrences = observePendingTaskOccurrences().first() + observeResolvedTaskOccurrences().first()
    return observeAquariums().first().mapNotNull { aquarium ->
        loadSetup(aquarium.id)?.let {
            AquariumHistory(it, observeReadingSamples(aquarium.id, timeZoneId).first(), occurrences.filter { o -> o.aquariumId == aquarium.id })
        }
    }
}

private val SUMMARY_TIME: LocalTime = LocalTime.of(19, 0)

/** The next Sunday 19:00 local strictly after [now]. */
fun nextSundaySummary(now: Long, zone: ZoneId): Long {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return generateSequence(today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))) { it.plusWeeks(1) }
        .map { it.atTime(SUMMARY_TIME).atZone(zone).toInstant().toEpochMilli() }
        .first { it > now }
}

/** « NO₃, pH et GH »: [labels] without duplicates, separated by commas, the last two by [pair]. */
inline fun joinLabels(labels: List<String>, pair: (String, String) -> String): String {
    val distinct = labels.distinct()
    return if (distinct.size < 2) distinct.joinToString() else pair(distinct.dropLast(1).joinToString(", "), distinct.last())
}

/** The parameters of [tests] as short labels, for « Tester %1$s » and the reminder notification. */
suspend fun dueTestsLabels(tests: List<DueTest>): String =
    joinLabels(tests.map { getString(it.definition.parameter.shortLabelResource()) }) { first, last ->
        getString(Res.string.list_pair, first, last)
    }
