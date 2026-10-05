package com.maximerollin.aqualog.shared

data class Aquarium(
    val id: String,
    val name: String,
    val volume: Double,
    val volumeUnit: VolumeUnit,
    val createdAtEpochMillis: Long,
    val profile: AquariumProfile = AquariumProfile.ESTABLISHED,
)

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
