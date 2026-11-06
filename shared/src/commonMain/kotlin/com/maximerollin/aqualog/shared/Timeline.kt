package com.maximerollin.aqualog.shared

data class TimelineFilter(
    val sinceEpochMillisInclusive: Long? = null,
    val aquariumId: String? = null,
    val parameterDefinitionId: String? = null,
    val maintenanceActionType: MaintenanceActionType? = null,
)

data class TimelineMeasurement(
    val measurement: Measurement,
    val definition: ParameterDefinition,
)

/**
 * One Chronology row per Session. Its child content remains grouped here so a
 * Measurement, maintenance Action or Event is never rendered twice.
 */
data class TimelineSession(
    val aquarium: Aquarium,
    val session: Session,
    val measurements: List<TimelineMeasurement>,
    val maintenanceActions: List<MaintenanceAction>,
    val events: List<SessionEvent>,
)

/** One Chronologie line: [inhabitant].count animals added, or removed, at [atEpochMillis]. */
data class TimelineInhabitantChange(
    val aquarium: Aquarium,
    val inhabitant: Inhabitant,
    val removed: Boolean,
    val atEpochMillis: Long,
)

/**
 * Splitting a group leaves several rows sharing one [Inhabitant.addedAtEpochMillis]; they were added together,
 * so they are one "added" change. Each row with a removal date is one "removed" change.
 */
fun timelineInhabitantChanges(
    history: List<Inhabitant>,
    aquariums: List<Aquarium>,
    filter: TimelineFilter,
): List<TimelineInhabitantChange> {
    val byId = aquariums.associateBy(Aquarium::id)
    val added = history.groupBy { listOf(it.aquariumId, it.speciesId, it.customName, it.addedAtEpochMillis) }
        .values.mapNotNull { rows ->
            val aquarium = byId[rows.first().aquariumId] ?: return@mapNotNull null
            TimelineInhabitantChange(
                aquarium,
                rows.first().copy(count = rows.sumOf(Inhabitant::count), removedAtEpochMillis = null),
                removed = false,
                atEpochMillis = rows.first().addedAtEpochMillis,
            )
        }
    val removed = history.mapNotNull { row ->
        val at = row.removedAtEpochMillis ?: return@mapNotNull null
        byId[row.aquariumId]?.let { TimelineInhabitantChange(it, row, removed = true, atEpochMillis = at) }
    }
    return (added + removed).filter {
        (filter.aquariumId == null || it.aquarium.id == filter.aquariumId) &&
            (filter.sinceEpochMillisInclusive == null || it.atEpochMillis >= filter.sinceEpochMillisInclusive)
    }.sortedWith(compareBy({ it.atEpochMillis }, { it.removed }, { it.inhabitant.id }))
}
