package com.maximerollin.aqualog

import com.maximerollin.aqualog.shared.AquariumRepository
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.ProductEvent
import com.maximerollin.aqualog.shared.RapidSessionInput
import com.maximerollin.aqualog.shared.RecordedSession
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.TaskOccurrence
import com.maximerollin.aqualog.shared.TaskResolution
import com.maximerollin.aqualog.shared.TaskResolutionInput
import com.maximerollin.aqualog.shared.TaskUsage
import kotlinx.coroutines.flow.first

/** The one path that ticks a Tâche, for the home and the widget. */
class TaskCompleter(
    private val repository: AquariumRepository,
    private val scheduler: TaskReminderScheduler,
    private val recordEvent: (ProductEvent) -> Unit,
    private val now: () -> Long,
    private val today: () -> TaskDate,
    private val newId: () -> String,
) {
    /**
     * A linked Tâche records its action (usual quantity) in a Session, then is resolved, even when not due yet;
     * a free-text one resolves. Returns the recorded Session, or null (also when [occurrence] is no longer pending).
     * Throws on failure.
     */
    suspend fun complete(occurrence: TaskOccurrence): RecordedSession? {
        if (repository.observePendingTaskOccurrences().first().none { it.id == occurrence.id }) return null
        val type = occurrence.maintenanceActionType
        if (type == null) {
            val result = repository.resolveTaskOccurrence(
                TaskResolutionInput(
                    occurrenceId = occurrence.id,
                    resolution = TaskResolution.COMPLETED,
                    resolvedAtEpochMillis = now(),
                    postponedMinuteOfDay = occurrence.minuteOfDay,
                    postponedTimeZoneId = occurrence.timeZoneId,
                ),
            )
            scheduler.cancel(occurrence.id)
            result.nextOccurrence?.let(scheduler::schedule)
            recordEvent(ProductEvent.TaskUsed(TaskUsage.COMPLETED))
            return null
        }
        val last = repository.loadRapidSessionContext(occurrence.aquariumId).lastMaintenanceActions[type]
        val recorded = repository.saveRapidSession(
            RapidSessionInput(
                aquariumId = occurrence.aquariumId,
                occurredAtEpochMillis = now(),
                idempotencyKey = newId(),
                maintenanceActions = listOf(defaultMaintenanceInput(type, last)),
            ),
        )
        completeLinked(recorded, alsoOccurrenceId = occurrence.id)
        return recorded
    }

    /** Resolves the linked Tâches that [recorded]'s actions satisfy. Throws when the resolution fails, so a caller that must know can react. */
    suspend fun completeLinked(
        recorded: RecordedSession,
        alreadyRecorded: Set<MaintenanceActionType> = emptySet(),
        alsoOccurrenceId: String? = null,
    ) {
        val types = recorded.maintenanceActions.map { it.type }.toSet() - alreadyRecorded
        if (types.isEmpty() && alsoOccurrenceId == null) return
        repository.completeLinkedTasks(recorded.session.aquariumId, types, today(), now(), alsoOccurrenceId)
            .forEach { result ->
                scheduler.cancel(result.resolved.id)
                result.nextOccurrence?.let(scheduler::schedule)
                recordEvent(ProductEvent.TaskUsed(TaskUsage.COMPLETED))
            }
    }
}
