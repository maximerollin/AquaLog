package com.maximerollin.aqualog

import com.maximerollin.aqualog.resources.*
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.MaintenanceActionInput
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.RapidSessionInput
import com.maximerollin.aqualog.shared.RoomAquariumRepository
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.TaskInput
import com.maximerollin.aqualog.shared.TaskRecurrence
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.encyclopedia.DrawingFamily
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Produces the Google Play listing screenshots from realistic demo data, one set per listing language.
 * Skipped unless run on purpose: see docs/store/README.md for the command and the emulator setup.
 */
@RunWith(AndroidJUnit4::class)
class PlayStoreScreenshotsTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application get() = instrumentation.targetContext.applicationContext as TestAquaLogApplication

    @After
    fun restoreDeviceConfiguration() {
        restoreDisplay()
        application.resetRepository()
    }

    @Test
    fun capturesTheListingScreenshots() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Run with -Pandroid.testInstrumentationRunnerArguments.playStoreScreenshots=true", arguments.getString("playStoreScreenshots") == "true")
        val languages = arguments.getString("playStoreLanguages")?.split(',')?.map(String::trim) ?: DEMO.keys.toList()
        languages.forEach(::captureListing)
    }

    private fun captureListing(language: String) {
        val demo = requireNotNull(DEMO[language]) { "No demo content for $language" }
        val strings = LocalizedStrings(language)
        configure(language, darkLarge = false)

        application.resetRepository()
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitText(strings.getString(Res.string.get_started))
            capture(language, "05-onboarding")
        }

        application.resetRepository()
        seed(demo)
        ActivityScenario.launch(MainActivity::class.java).use {
            val measure = strings.getString(Res.string.measure_action)
            compose.awaitLabel(measure)
            awaitText(demo.aquarium)
            capture(language, "01-home")

            compose.onNode(hasLabel(measure)).performClick()
            awaitText(strings.getString(Res.string.measurements_heading))
            NEW_SESSION.forEach { (parameter, value) ->
                val name = strings.getString(parameter)
                val field = hasContentDescription(strings.getString(Res.string.measurement_for_parameter, name))
                compose.onNode(hasScrollAction()).performScrollToNode(field)
                compose.onNode(field).performTextInput(value.replace('.', demo.decimalSeparator))
            }
            closeSoftKeyboard()
            val waterChange = strings.getString(Res.string.action_water_change)
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(waterChange))
            compose.onNode(hasText(waterChange) and hasClickAction()).performClick()
            closeSoftKeyboard()
            compose.onNodeWithTag("rapid-session-list").performScrollToIndex(0)
            capture(language, "02-session")
            compose.onNode(hasLabel(strings.getString(Res.string.cancel)) and hasClickAction()).performClick()

            compose.awaitLabel(strings.getString(Res.string.navigation_history))
            compose.onNode(hasLabel(strings.getString(Res.string.navigation_history))).performClick()
            awaitText(strings.getString(Res.string.timeline_title))
            capture(language, "03-history")

            compose.onNode(hasLabel(strings.getString(Res.string.navigation_trends))).performClick()
            compose.awaitLabel(strings.getString(Res.string.trends_custom_period))
            capture(language, "04-trends")

            compose.onNode(hasLabel(strings.getString(Res.string.navigation_more))).performClick()
            val discoverPro = strings.getString(Res.string.more_pro_banner_action)
            compose.onNodeWithTag("settings-list").performScrollToNode(hasText(discoverPro))
            compose.onNode(hasText(discoverPro) and hasClickAction()).performClick()
            awaitText(strings.getString(Res.string.paywall_headline))
            compose.waitUntil(10_000) { compose.onAllNodes(hasText(demo.annualPrice, substring = true)).fetchSemanticsNodes().isNotEmpty() }
            // At 9:16 the plans sit under the actions: bring them, prices and trial line included, into view.
            compose.onNodeWithTag("onboarding-list").performScrollToNode(hasTestTag("plan-lifetime"))
            compose.onNodeWithTag("onboarding-list").performScrollToNode(hasText(demo.annualPrice, substring = true) and !hasClickAction())
            capture(language, "06-offer")
        }
    }

    /** About eight weeks of a planted shrimp tank: a reading every three or four days, nitrate creeping up lately. */
    private fun seed(demo: Demo) = runBlocking {
        val repository = application.aquariumRepository
        // The Sunday summary card (Sunday and Monday only) changes the home: keep its clock midweek.
        val now = System.currentTimeMillis()
        val shiftDays = when (Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).dayOfWeek) {
            DayOfWeek.SUNDAY -> 3L
            DayOfWeek.MONDAY -> 2L
            else -> 0L
        }
        val clock = now + shiftDays * DAY
        application.clockMillis = clock
        application.annualPrice = demo.annualPrice
        application.lifetimePrice = demo.lifetimePrice
        application.annualBillingDateMillis = clock + 14 * DAY

        val latest = now - 3 * HOUR
        // The tank and its inhabitants date from before the first reading, so History opens on Sessions.
        application.repositoryClockMillis = latest - 60 * DAY
        val setup = repository.createConfiguredAquarium(
            name = demo.aquarium,
            volume = 120.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.PLANTED_SHRIMP,
            parameters = OnboardingPresets.parametersFor(AquariumProfile.PLANTED_SHRIMP),
        )
        val ids = setup.parameters.associate { it.parameter to it.id }
        listOf(
            Triple("red-cherry", DrawingFamily.SHRIMP, 12),
            Triple("neon", DrawingFamily.TETRA, 10),
            Triple("corydoras-panda", DrawingFamily.CATFISH, 6),
            Triple("otocinclus", DrawingFamily.CATFISH, 3),
            Triple("neritine", DrawingFamily.SNAIL, 2),
        ).forEach { (species, family, count) -> repository.addInhabitant(setup.aquarium.id, species, null, family, count) }
        application.repositoryClockMillis = null
        READINGS.forEachIndexed { index, reading ->
            val measurements = buildMap {
                put(ids.getValue(BuiltInParameter.TEMPERATURE), reading.temperature)
                put(ids.getValue(BuiltInParameter.PH), reading.ph)
                put(ids.getValue(BuiltInParameter.GH), reading.gh)
                put(ids.getValue(BuiltInParameter.KH), reading.kh)
                put(ids.getValue(BuiltInParameter.TDS), reading.tds)
                put(ids.getValue(BuiltInParameter.NITRATE), reading.nitrate)
                reading.phosphate?.let { put(ids.getValue(BuiltInParameter.PHOSPHATE), it) }
            }
            repository.saveRapidSession(
                RapidSessionInput(
                    aquariumId = setup.aquarium.id,
                    occurredAtEpochMillis = latest - reading.daysAgo * DAY - (index % 3) * HOUR,
                    idempotencyKey = "play-store-${reading.daysAgo}",
                    measurementInputs = measurements,
                    maintenanceActions = reading.actions,
                    observation = reading.observation?.let(demo.observations::getValue).orEmpty(),
                ),
            )
        }
        // The demo shows the app in use, not its first-week invitation to create an account.
        (repository as RoomAquariumRepository).markAccountInvitationOffered()
        val tomorrow = Instant.ofEpochMilli(clock + DAY).atZone(ZoneId.systemDefault()).toLocalDate()
        repository.createTask(
            TaskInput(
                aquariumId = setup.aquarium.id,
                title = demo.task,
                recurrence = TaskRecurrence.WEEKLY,
                firstDueDate = TaskDate(tomorrow.year, tomorrow.monthValue, tomorrow.dayOfMonth),
                minuteOfDay = 18 * 60,
                timeZoneId = ZoneId.systemDefault().id,
                maintenanceActionType = MaintenanceActionType.FILTER_MAINTENANCE,
            ),
        )
    }

    private fun awaitText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun capture(language: String, name: String) {
        compose.waitForIdle()
        // Lets the water, the light rays and the fish settle into a frame.
        SystemClock.sleep(1_500)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertTrue("capture must not be empty", bitmap.width >= 320 && bitmap.height >= 320)
        TestStorage().openOutputFile("play-store/$language/$name.png").use { stream ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
    }

    private class Demo(
        val aquarium: String,
        val task: String,
        val annualPrice: String,
        val lifetimePrice: String,
        val observations: Map<String, String>,
        val decimalSeparator: Char = ',',

    )

    private class Reading(
        val daysAgo: Int,
        val temperature: String,
        val ph: String,
        val gh: String,
        val kh: String,
        val tds: String,
        val nitrate: String,
        val phosphate: String? = null,
        val actions: List<MaintenanceActionInput> = emptyList(),
        val observation: String? = null,
    )

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR

        val NEW_SESSION = listOf(
            Res.string.parameter_temperature to "24.8",
            Res.string.parameter_ph to "7.0",
            Res.string.parameter_gh to "7",
            Res.string.parameter_kh to "5",
            Res.string.parameter_tds to "190",
            Res.string.parameter_nitrate to "15",
        )

        private val change = MaintenanceActionInput(MaintenanceActionType.WATER_CHANGE, "25", "%")
        private val fertilize = MaintenanceActionInput(MaintenanceActionType.FERTILIZATION)
        private val prune = MaintenanceActionInput(MaintenanceActionType.PRUNING)
        private val filter = MaintenanceActionInput(MaintenanceActionType.FILTER_MAINTENANCE)

        // Oldest first; a weekly water change keeps nitrate in range until the last one was skipped.
        val READINGS = listOf(
            Reading(56, "24.6", "7.1", "7", "5", "205", "22", "0.4"),
            Reading(52, "25.0", "7.0", "7", "5", "220", "30", actions = listOf(change, fertilize)),
            Reading(49, "24.8", "7.1", "6", "4", "198", "21", "0.5"),
            Reading(45, "25.2", "6.9", "7", "5", "214", "29", actions = listOf(change, filter)),
            Reading(42, "24.9", "7.0", "7", "5", "196", "20", "0.4"),
            Reading(38, "24.5", "7.1", "7", "5", "218", "31", actions = listOf(change, fertilize)),
            Reading(35, "25.1", "7.0", "6", "4", "201", "19", "0.3", observation = "shrimp"),
            Reading(31, "24.7", "7.2", "7", "5", "222", "28", actions = listOf(change, prune), observation = "pruning"),
            Reading(28, "24.9", "7.0", "7", "5", "199", "21", "0.4"),
            Reading(24, "25.0", "7.1", "7", "5", "217", "30", actions = listOf(change, fertilize)),
            Reading(21, "24.6", "7.0", "6", "4", "195", "20", "0.5"),
            Reading(17, "25.2", "6.9", "7", "5", "212", "29", actions = listOf(change, filter), observation = "growth"),
            Reading(14, "24.8", "7.0", "7", "5", "200", "19", "0.4"),
            Reading(10, "24.9", "7.1", "7", "5", "219", "27", actions = listOf(change, fertilize)),
            Reading(7, "25.1", "7.0", "7", "5", "208", "23", "0.5"),
            Reading(3, "24.7", "7.1", "7", "5", "221", "36"),
            Reading(0, "25.0", "7.0", "7", "5", "226", "44", "0.6", observation = "nitrate"),
        )

        val DEMO = linkedMapOf(
            "fr" to Demo(
                aquarium = "Salon",
                task = "Rincer la mousse du filtre",
                annualPrice = "14,99 €",
                lifetimePrice = "34,99 €",
                observations = mapOf(
                    "shrimp" to "Deux crevettes grainées",
                    "pruning" to "Taille des tiges, boutures replantées",
                    "growth" to "Nouvelles feuilles sur la cryptocoryne",
                    "nitrate" to "Changement d'eau oublié la semaine dernière",
                ),
            ),
            "en" to Demo(
                aquarium = "Living room",
                task = "Rinse the filter sponge",
                annualPrice = "€14.99",
                lifetimePrice = "€34.99",
                decimalSeparator = '.',
                observations = mapOf(
                    "shrimp" to "Two berried shrimp",
                    "pruning" to "Trimmed the stems, replanted the cuttings",
                    "growth" to "New leaves on the cryptocoryne",
                    "nitrate" to "Skipped last week's water change",
                ),
            ),
            "de" to Demo(
                aquarium = "Wohnzimmer",
                task = "Filterschwamm ausspülen",
                annualPrice = "14,99 €",
                lifetimePrice = "34,99 €",
                observations = mapOf(
                    "shrimp" to "Zwei tragende Garnelen",
                    "pruning" to "Stängel gekürzt, Stecklinge neu gepflanzt",
                    "growth" to "Neue Blätter am Cryptocoryne",
                    "nitrate" to "Wasserwechsel letzte Woche vergessen",
                ),
            ),
            "it" to Demo(
                aquarium = "Soggiorno",
                task = "Sciacquare la spugna del filtro",
                annualPrice = "14,99 €",
                lifetimePrice = "34,99 €",
                observations = mapOf(
                    "shrimp" to "Due caridine con le uova",
                    "pruning" to "Steli potati, talee ripiantate",
                    "growth" to "Foglie nuove sulla criptocoryne",
                    "nitrate" to "Cambio d'acqua saltato la settimana scorsa",
                ),
            ),
            "es" to Demo(
                aquarium = "Salón",
                task = "Enjuagar la esponja del filtro",
                annualPrice = "14,99 €",
                lifetimePrice = "34,99 €",
                observations = mapOf(
                    "shrimp" to "Dos gambas con huevos",
                    "pruning" to "Tallos podados, esquejes replantados",
                    "growth" to "Hojas nuevas en la criptocoryne",
                    "nitrate" to "Se saltó el cambio de agua de la semana pasada",
                ),
            ),
        )
    }
}
