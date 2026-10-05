package com.maximerollin.aqualog.shared

enum class AquariumProfile(val storageValue: String) {
    ESTABLISHED("established"),
    CYCLING("cycling"),
    PLANTED_SHRIMP("planted_shrimp");

    companion object {
        fun fromStorageValue(value: String): AquariumProfile =
            entries.first { it.storageValue == value }
    }
}

enum class BuiltInParameter(val storageValue: String) {
    TEMPERATURE("temperature"),
    PH("ph"),
    AMMONIA("ammonia"),
    NITRITE("nitrite"),
    NITRATE("nitrate"),
    GH("gh"),
    KH("kh"),
    TDS("tds"),
    PHOSPHATE("phosphate"),
    SALINITY("salinity");

    companion object {
        fun fromStorageValue(value: String): BuiltInParameter =
            entries.first { it.storageValue == value }
    }
}

data class ParameterDefinitionDraft(
    val parameter: BuiltInParameter,
    val isActive: Boolean,
    val position: Int,
    val unit: String,
    val precision: Int,
    val indicativeMinimum: Double?,
    val indicativeMaximum: Double?,
)

data class ParameterDefinition(
    val id: String,
    val aquariumId: String,
    val parameter: BuiltInParameter,
    val isActive: Boolean,
    val position: Int,
    val unit: String,
    val precision: Int,
    val indicativeMinimum: Double?,
    val indicativeMaximum: Double?,
)

object OnboardingPresets {
    fun parametersFor(profile: AquariumProfile): List<ParameterDefinitionDraft> {
        val activeOrder = when (profile) {
            AquariumProfile.ESTABLISHED -> listOf(
                BuiltInParameter.TEMPERATURE,
                BuiltInParameter.PH,
                BuiltInParameter.NITRITE,
                BuiltInParameter.NITRATE,
                BuiltInParameter.GH,
                BuiltInParameter.KH,
            )
            AquariumProfile.CYCLING -> listOf(
                BuiltInParameter.TEMPERATURE,
                BuiltInParameter.PH,
                BuiltInParameter.AMMONIA,
                BuiltInParameter.NITRITE,
                BuiltInParameter.NITRATE,
            )
            AquariumProfile.PLANTED_SHRIMP -> listOf(
                BuiltInParameter.TEMPERATURE,
                BuiltInParameter.PH,
                BuiltInParameter.GH,
                BuiltInParameter.KH,
                BuiltInParameter.TDS,
                BuiltInParameter.NITRATE,
                BuiltInParameter.PHOSPHATE,
            )
        }
        val ordered = activeOrder + BuiltInParameter.entries.filterNot(activeOrder::contains)
        return ordered.mapIndexed { position, parameter ->
            defaults(parameter).copy(
                isActive = parameter in activeOrder,
                position = position,
            )
        }
    }

    private fun defaults(parameter: BuiltInParameter): ParameterDefinitionDraft =
        when (parameter) {
            BuiltInParameter.TEMPERATURE -> draft(parameter, "°C", 1, 22.0, 28.0)
            BuiltInParameter.PH -> draft(parameter, "pH", 1, 6.5, 7.5)
            BuiltInParameter.AMMONIA -> draft(parameter, "mg/L", 2, 0.0, 0.0)
            BuiltInParameter.NITRITE -> draft(parameter, "mg/L", 2, 0.0, 0.0)
            BuiltInParameter.NITRATE -> draft(parameter, "mg/L", 1, 0.0, 40.0)
            BuiltInParameter.GH -> draft(parameter, "°dGH", 1, 4.0, 12.0)
            BuiltInParameter.KH -> draft(parameter, "°dKH", 1, 3.0, 10.0)
            BuiltInParameter.TDS -> draft(parameter, "ppm", 0, 100.0, 300.0)
            BuiltInParameter.PHOSPHATE -> draft(parameter, "mg/L", 2, 0.0, 1.0)
            BuiltInParameter.SALINITY -> draft(parameter, "ppt", 1, 0.0, 0.0)
        }

    private fun draft(
        parameter: BuiltInParameter,
        unit: String,
        precision: Int,
        minimum: Double?,
        maximum: Double?,
    ) = ParameterDefinitionDraft(
        parameter = parameter,
        isActive = false,
        position = 0,
        unit = unit,
        precision = precision,
        indicativeMinimum = minimum,
        indicativeMaximum = maximum,
    )
}
