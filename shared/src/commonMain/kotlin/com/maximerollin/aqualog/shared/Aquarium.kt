package com.maximerollin.aqualog.shared

data class Aquarium(
    val id: String,
    val name: String,
    val volume: Double,
    val volumeUnit: VolumeUnit,
    val createdAtEpochMillis: Long,
    val profile: AquariumProfile = AquariumProfile.ESTABLISHED,
    val isArchived: Boolean = false,
    val photoId: String? = null,
)

enum class AquariumCreationAccess {
    FREE,
    PRO,
}

/** Aquariums a Free user keeps in use at once; archived ones do not count, and Pro has no limit. */
const val FREE_AQUARIUM_LIMIT = 2

class FreeAquariumLimitReached : IllegalStateException("Free supports $FREE_AQUARIUM_LIMIT Aquariums")

data class AquariumSetup(
    val aquarium: Aquarium,
    val parameters: List<ParameterDefinition>,
)

enum class VolumeUnit(val storageValue: String) {
    LITERS("liters"),
    US_GALLONS("us_gallons");

    companion object {
        fun fromStorageValue(value: String): VolumeUnit =
            entries.first { it.storageValue == value }
    }
}
