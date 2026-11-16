package com.maximerollin.aqualog.a11y

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.viewModelScope
import com.maximerollin.aqualog.HomeUiState
import com.maximerollin.aqualog.HomeViewModel
import com.maximerollin.aqualog.TimelineFiltersSheet
import com.maximerollin.aqualog.TimelineSessionCard
import com.maximerollin.aqualog.TimelineUiState
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.MaintenanceAction
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.Measurement
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.RoomAquariumRepository
import com.maximerollin.aqualog.shared.Session
import com.maximerollin.aqualog.shared.TimelineMeasurement
import com.maximerollin.aqualog.shared.TimelineSession
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.createAquaLogDatabase
import com.maximerollin.aqualog.shared.createJvmDatabaseBuilder
import com.maximerollin.aqualog.ui.theme.AquaLogTheme
import com.maximerollin.aqualog.ui.theme.LEVEL_MARK_TAG
import java.nio.file.Files
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TimelineSemanticsTest {
    private val aquarium = Aquarium("a1", "Salon", 120.0, VolumeUnit.LITERS, 0L, AquariumProfile.ESTABLISHED)

    private fun definition(parameter: BuiltInParameter, position: Int, min: Double, max: Double) = ParameterDefinition(
        id = "d-${parameter.storageValue}", aquariumId = aquarium.id, parameter = parameter, isActive = true,
        position = position, unit = OnboardingPresets.presetUnit(parameter), precision = 1,
        indicativeMinimum = min, indicativeMaximum = max,
    )

    private val ph = definition(BuiltInParameter.PH, 0, 6.5, 7.5)
    private val gh = definition(BuiltInParameter.GH, 1, 4.0, 12.0)
    private val kh = definition(BuiltInParameter.KH, 2, 3.0, 8.0)

    /** pH above its range, GH and KH in range, and a water change. */
    private val entry = TimelineSession(
        aquarium = aquarium,
        session = Session("s1", aquarium.id, 1_700_000_000_000L, 1_700_000_000_000L),
        measurements = listOf(kh to 5.0, ph to 7.8, gh to 8.0).mapIndexed { index, (definition, value) ->
            TimelineMeasurement(Measurement("m$index", "s1", definition.id, value), definition)
        },
        maintenanceActions = listOf(MaintenanceAction("ma1", "s1", MaintenanceActionType.WATER_CHANGE, 20.0, "%", null)),
        events = emptyList(),
    )

    private fun withEnglish(block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try { block() } finally { Locale.setDefault(previous) }
    }

    @Test
    fun `a session card is one button that speaks its readings`() = withEnglish {
        runComposeUiTest {
            setContent { AquaLogTheme(false) { TimelineSessionCard(entry, showAquarium = false, onClick = {}) } }
            val card = onNodeWithContentDescription("Open Session details")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .fetchSemanticsNode()
            val spoken = card.config[SemanticsProperties.StateDescription]
            assertTrue("7.8" in spoken && "outside your indicative range" in spoken, spoken)
        }
    }

    @Test
    fun `readings show as pills with the out of range one marked`() = withEnglish {
        runComposeUiTest {
            setContent { AquaLogTheme(false) { TimelineSessionCard(entry, showAquarium = false, onClick = {}) } }
            // Three readings, one of them (pH 7.8) above its range.
            assertEquals(1, onAllNodesWithTag(LEVEL_MARK_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `the filter sheet no longer offers the period`() = withEnglish {
        val directory = Files.createTempDirectory("aqualog-timeline-filters")
        val database = createAquaLogDatabase(createJvmDatabaseBuilder(directory.resolve("aqualog.db").toString()))
        val viewModel = HomeViewModel(RoomAquariumRepository(database, { UUID.randomUUID().toString() }, { 1_700_000_000_000L }))
        try {
            val state = HomeUiState(
                isLoading = false,
                aquariums = listOf(aquarium),
                timeline = TimelineUiState(filtersExpanded = true),
            )
            runComposeUiTest {
                setContent { AquaLogTheme(false) { TimelineFiltersSheet(state, viewModel) } }
                waitForIdle()
                onNodeWithText("All Aquariums").assertExists()
                assertEquals(0, onAllNodesWithText("7 days").fetchSemanticsNodes().size)
            }
        } finally {
            viewModel.viewModelScope.cancel()
            database.close()
        }
    }
}
