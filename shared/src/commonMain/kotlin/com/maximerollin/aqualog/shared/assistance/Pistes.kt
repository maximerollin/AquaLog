package com.maximerollin.aqualog.shared.assistance

import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.ParameterDefinition

enum class Direction { LOW, HIGH }

/** One entry per distinct French sentence of the Pistes table; the KDoc is the copy Task 7 maps. */
enum class PisteKey {
    /** « Changez 30 % de l'eau. » */
    WATER_CHANGE_30,
    /** « Changez 30 à 50 % de l'eau. » */
    WATER_CHANGE_30_TO_50,
    /** « Changez une partie de l'eau. » */
    WATER_CHANGE_PARTIAL,
    /** « Changez une partie de l'eau avec une eau plus douce. » */
    WATER_CHANGE_SOFTER_WATER,
    /** « Changez une partie de l'eau avec une eau plus pure. » */
    WATER_CHANGE_PURER_WATER,
    /** « Coupez l'eau avec de l'eau osmosée. » */
    CUT_WITH_OSMOSIS,
    /** « Votre changement d'eau est en retard : commencez par là. » */
    WATER_CHANGE_OVERDUE,
    /** « Nourrissez moins pendant 2 jours. » */
    FEED_LESS_2_DAYS,
    /** « Ajoutez des bactéries de démarrage. » */
    ADD_STARTER_BACTERIA,
    /** « Ajoutez un conditionneur d'eau qui neutralise l'ammoniac. » */
    ADD_AMMONIA_DETOXIFIER,
    /** « Vérifiez la quantité de nourriture. » */
    CHECK_FOOD_AMOUNT,
    /** « Ajoutez des plantes à croissance rapide. » */
    ADD_FAST_GROWING_PLANTS,
    /** « Vérifiez le KH : un KH bas fait chuter le pH. » */
    CHECK_KH_LOW_DROPS_PH,
    /** « Ajoutez un tampon pour remonter le KH. » */
    ADD_KH_BUFFER,
    /** « Vérifiez l'eau du robinet et les décors calcaires. » */
    CHECK_TAP_WATER_AND_DECOR,
    /** « Vérifiez le réglage du chauffage. » */
    CHECK_HEATER_SETTING,
    /** « Aérez la surface ou éteignez l'éclairage quelques heures. » */
    VENT_OR_LIGHTS_OFF,
    /** « Vérifiez que le chauffage fonctionne. » */
    CHECK_HEATER_WORKS,
    /** « Adaptez sa puissance au volume du bac. » */
    ADJUST_HEATER_POWER,
    /** « Ajoutez un reminéralisant. » */
    ADD_REMINERALIZER,
    /** « Réduisez la nourriture. » */
    REDUCE_FOOD,
    /** « Ajoutez un anti-phosphates. » */
    ADD_ANTI_PHOSPHATE,
    /** « Refaites un test pour confirmer. » */
    RETEST_TO_CONFIRM,
    /** « Pendant le cyclage, de l'ammoniac et des nitrites sont normaux. Attendez qu'ils redescendent à 0 avant d'ajouter des poissons. » */
    CYCLING_NORMAL,
}

data class PisteAdvice(
    val parameter: BuiltInParameter,
    val direction: Direction,
    val pistes: List<PisteKey>,
    val retestInDays: Int,
)

/** HIGH above the indicative maximum, LOW below the minimum, else the alert side (see [alertSide]); null in range. */
fun direction(definition: ParameterDefinition, value: Double): Direction? = when {
    definition.indicativeMaximum?.let { value > it } == true -> Direction.HIGH
    definition.indicativeMinimum?.let { value < it } == true -> Direction.LOW
    else -> alertSide(definition, value)
}

private data class Recipe(val pistes: List<PisteKey>, val retestInDays: Int)

private val recipes: Map<Pair<BuiltInParameter, Direction>, Recipe> = mapOf(
    (BuiltInParameter.NITRITE to Direction.HIGH) to Recipe(
        listOf(PisteKey.WATER_CHANGE_30, PisteKey.FEED_LESS_2_DAYS, PisteKey.ADD_STARTER_BACTERIA), 1,
    ),
    (BuiltInParameter.AMMONIA to Direction.HIGH) to Recipe(
        listOf(PisteKey.WATER_CHANGE_30, PisteKey.FEED_LESS_2_DAYS, PisteKey.ADD_AMMONIA_DETOXIFIER), 1,
    ),
    (BuiltInParameter.NITRATE to Direction.HIGH) to Recipe(
        listOf(PisteKey.WATER_CHANGE_30_TO_50, PisteKey.CHECK_FOOD_AMOUNT, PisteKey.ADD_FAST_GROWING_PLANTS), 3,
    ),
    (BuiltInParameter.PH to Direction.LOW) to Recipe(
        listOf(PisteKey.CHECK_KH_LOW_DROPS_PH, PisteKey.WATER_CHANGE_PARTIAL, PisteKey.ADD_KH_BUFFER), 2,
    ),
    (BuiltInParameter.PH to Direction.HIGH) to Recipe(
        listOf(PisteKey.CHECK_TAP_WATER_AND_DECOR, PisteKey.WATER_CHANGE_SOFTER_WATER), 2,
    ),
    (BuiltInParameter.TEMPERATURE to Direction.HIGH) to Recipe(
        listOf(PisteKey.CHECK_HEATER_SETTING, PisteKey.VENT_OR_LIGHTS_OFF), 1,
    ),
    (BuiltInParameter.TEMPERATURE to Direction.LOW) to Recipe(
        listOf(PisteKey.CHECK_HEATER_WORKS, PisteKey.ADJUST_HEATER_POWER), 1,
    ),
    (BuiltInParameter.KH to Direction.LOW) to Recipe(
        listOf(PisteKey.ADD_KH_BUFFER, PisteKey.WATER_CHANGE_PARTIAL), 3,
    ),
    (BuiltInParameter.GH to Direction.LOW) to Recipe(
        listOf(PisteKey.ADD_REMINERALIZER, PisteKey.WATER_CHANGE_PARTIAL), 3,
    ),
    (BuiltInParameter.GH to Direction.HIGH) to Recipe(listOf(PisteKey.CUT_WITH_OSMOSIS), 7),
    (BuiltInParameter.TDS to Direction.HIGH) to Recipe(listOf(PisteKey.WATER_CHANGE_PURER_WATER), 3),
    (BuiltInParameter.PHOSPHATE to Direction.HIGH) to Recipe(
        listOf(PisteKey.WATER_CHANGE_PARTIAL, PisteKey.REDUCE_FOOD, PisteKey.ADD_ANTI_PHOSPHATE), 3,
    ),
)

private val genericRecipe = Recipe(listOf(PisteKey.RETEST_TO_CONFIRM, PisteKey.WATER_CHANGE_PARTIAL), 2)

/** Pistes that change water; the overdue suggestion already says so, so these are dropped after it. */
private val waterChangePistes = setOf(
    PisteKey.WATER_CHANGE_30,
    PisteKey.WATER_CHANGE_30_TO_50,
    PisteKey.WATER_CHANGE_PARTIAL,
    PisteKey.WATER_CHANGE_SOFTER_WATER,
    PisteKey.WATER_CHANGE_PURER_WATER,
)

fun pistesFor(
    parameter: BuiltInParameter,
    direction: Direction,
    profile: AquariumProfile,
    waterChangeOverdue: Boolean,
): PisteAdvice {
    // Cycling: ammonia and nitrite are expected, so the usual corrective pistes would mislead.
    if (profile == AquariumProfile.CYCLING && direction == Direction.HIGH && parameter in NITROGEN_WASTE) {
        return PisteAdvice(parameter, direction, listOf(PisteKey.CYCLING_NORMAL), 2)
    }
    val recipe = recipes[parameter to direction] ?: genericRecipe
    val pistes = if (waterChangeOverdue) {
        listOf(PisteKey.WATER_CHANGE_OVERDUE) + recipe.pistes.filterNot { it in waterChangePistes }.take(2)
    } else {
        recipe.pistes
    }
    return PisteAdvice(parameter, direction, pistes, recipe.retestInDays)
}
