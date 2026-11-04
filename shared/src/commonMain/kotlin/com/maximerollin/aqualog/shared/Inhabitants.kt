package com.maximerollin.aqualog.shared

import com.maximerollin.aqualog.shared.encyclopedia.DrawingFamily

/**
 * One group of animals or plants added to an Aquarium at one time. Removing part of it splits it: see
 * [AquariumRepository.removeInhabitants]. A row with [removedAtEpochMillis] set is history, not a present inhabitant.
 */
data class Inhabitant(
    val id: String,
    val aquariumId: String,
    /** Encyclopedia id; null for a custom species, which then carries [customName]. */
    val speciesId: String?,
    val customName: String?,
    val family: DrawingFamily,
    val count: Int,
    val addedAtEpochMillis: Long,
    val removedAtEpochMillis: Long? = null,
)
