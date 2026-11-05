package com.maximerollin.aqualog.widget

import com.maximerollin.aqualog.HomeOverview
import com.maximerollin.aqualog.addedDays
import com.maximerollin.aqualog.encyclopedia.loadEncyclopedia
import com.maximerollin.aqualog.homeOverview
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumRepository
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.Inhabitant
import com.maximerollin.aqualog.shared.LatestReading
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.TaskOccurrence
import com.maximerollin.aqualog.shared.assistance.DueTest
import com.maximerollin.aqualog.shared.assistance.ReadingLevel
import com.maximerollin.aqualog.shared.assistance.dueTests
import com.maximerollin.aqualog.shared.encyclopedia.DrawingFamily
import com.maximerollin.aqualog.shared.encyclopedia.PresentGroup
import com.maximerollin.aqualog.shared.encyclopedia.Species
import com.maximerollin.aqualog.shared.toEpochDay
import com.maximerollin.aqualog.todayTasks
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

/** The status dot, from readings only (like the home's): an overdue Tâche does not change it. */
enum class WidgetLight { OK, ATTENTION, ALERT }

/** « La prochaine chose à faire », in priority order. */
sealed interface NextStep {
    data class Value(val definition: ParameterDefinition, val level: ReadingLevel) : NextStep
    data class Task(val occurrence: TaskOccurrence, val isOverdue: Boolean) : NextStep
    data class Tests(val tests: List<DueTest>) : NextStep
    data object Nothing : NextStep
}

data class WidgetAquarium(
    val aquarium: Aquarium,
    val overview: HomeOverview,
    val light: WidgetLight,
    val next: NextStep,
    /** [todayTasks]' due list: due today or overdue, at most 3. */
    val dueTasks: List<TaskOccurrence>,
    /** The 3 largest groups that swim (see [widgetGroups]). */
    val groups: List<PresentGroup> = emptyList(),
)

/**
 * The 3 largest present groups, merged per species (a custom one per name and family), biggest first, ties by key.
 * Plants are decor, not swimmers. Without the [encyclopedia] a group keeps its stored family and no species.
 */
fun widgetGroups(inhabitants: List<Inhabitant>, encyclopedia: List<Species>?): List<PresentGroup> =
    inhabitants.filter { it.family != DrawingFamily.PLANT && it.count > 0 }
        .groupBy { it.speciesId ?: "custom:${it.customName?.lowercase()}:${it.family}" }
        .map { (key, rows) -> key to PresentGroup(encyclopedia?.firstOrNull { it.id == rows.first().speciesId }, rows.first().family, rows.sumOf { it.count }) }
        .sortedWith(compareBy({ -it.second.count }, { it.first }))
        .take(3)
        .map { it.second }

/** Priority: a value in ALERT, an overdue Tâche, due tests, a value in ATTENTION, a Tâche due today, nothing. */
fun widgetAquarium(
    setup: AquariumSetup,
    readings: List<LatestReading>,
    dueTests: List<DueTest>,
    pending: List<TaskOccurrence>,
    today: TaskDate,
    groups: List<PresentGroup> = emptyList(),
): WidgetAquarium {
    val overview = homeOverview(setup, readings)
    val dueTasks = todayTasks(pending, setup.aquarium.id, today).due
    fun valueAt(level: ReadingLevel) = overview.values.firstOrNull { it.level == level }
        ?.let { NextStep.Value(it.definition, level) }
    fun TaskOccurrence.isOverdue() = dueDate.toEpochDay() < today.toEpochDay()
    val next = valueAt(ReadingLevel.ALERT)
        ?: dueTasks.firstOrNull { it.isOverdue() }?.let { NextStep.Task(it, isOverdue = true) }
        ?: dueTests.takeIf { it.isNotEmpty() }?.let { NextStep.Tests(it) }
        ?: valueAt(ReadingLevel.ATTENTION)
        ?: dueTasks.firstOrNull()?.let { NextStep.Task(it, isOverdue = false) }
        ?: NextStep.Nothing
    val light = when {
        overview.hasAlert -> WidgetLight.ALERT
        overview.outsideRangeCount > 0 -> WidgetLight.ATTENTION
        else -> WidgetLight.OK
    }
    return WidgetAquarium(setup.aquarium, overview, light, next, dueTasks, groups)
}

private suspend fun AquariumRepository.widgetAquarium(
    aquariumId: String,
    pending: List<TaskOccurrence>,
    today: TaskDate,
    timeZoneId: String,
): WidgetAquarium? = loadSetup(aquariumId)?.let { setup ->
    widgetAquarium(
        setup,
        observeLatestReadings(aquariumId).first(),
        dueTests(setup, observeReadingSamples(aquariumId, timeZoneId).first(), today, addedDays(aquariumId, timeZoneId)),
        pending,
        today,
        widgetGroups(observeInhabitants(aquariumId).first(), encyclopediaOrNull()),
    )
}

/** The widget never fails for the encyclopedia: without it the groups use their stored family. */
private suspend fun encyclopediaOrNull(): List<Species>? = try {
    loadEncyclopedia()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

/** [pinnedId] when it is a non-archived Aquarium, else the current one, else null (no Aquarium yet). */
suspend fun AquariumRepository.loadWidgetAquarium(pinnedId: String?, today: TaskDate, timeZoneId: String): WidgetAquarium? {
    val id = pinnedId?.takeIf { pinned -> observeAquariums().first().any { it.id == pinned } }
        ?: observeCurrentAquarium().first()?.id
        ?: return null
    return widgetAquarium(id, observePendingTaskOccurrences().first(), today, timeZoneId)
}

/** Every non-archived Aquarium, in the app's order. */
suspend fun AquariumRepository.loadWidgetAquariums(today: TaskDate, timeZoneId: String): List<WidgetAquarium> {
    val pending = observePendingTaskOccurrences().first()
    return observeAquariums().first().mapNotNull { widgetAquarium(it.id, pending, today, timeZoneId) }
}
