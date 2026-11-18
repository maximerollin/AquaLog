package com.maximerollin.aqualog.a11y

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.LocalizedFormatters
import com.maximerollin.aqualog.SessionDetailsActions
import com.maximerollin.aqualog.SessionDetailsContent
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.EventType
import com.maximerollin.aqualog.shared.MaintenanceAction
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.Measurement
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.RecordedSession
import com.maximerollin.aqualog.shared.Session
import com.maximerollin.aqualog.shared.SessionEvent
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.ui.theme.AquaLogTheme
import com.maximerollin.aqualog.ui.theme.LEVEL_MARK_TAG
import java.time.LocalDate
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Session details in tiles and cards under the water (lot 4a, Task 5). */
@OptIn(ExperimentalTestApi::class)
class SessionDetailsSemanticsTest {
    private val salon = Aquarium("a", "Salon", 120.0, VolumeUnit.LITERS, 0L)
    private val occurredAt = 1_763_380_800_000L // 17 Nov 2025, 12:00 UTC

    private fun parameter(parameter: BuiltInParameter, position: Int, unit: String, min: Double?, max: Double?) =
        ParameterDefinition(parameter.storageValue, salon.id, parameter, true, position, unit, 1, min, max)

    private val definitions = listOf(
        parameter(BuiltInParameter.PH, 0, "pH", 6.5, 7.5),
        parameter(BuiltInParameter.TEMPERATURE, 1, "°C", 22.0, 28.0),
        parameter(BuiltInParameter.GH, 2, "°dGH", 4.0, 12.0),
        parameter(BuiltInParameter.KH, 3, "°dKH", 3.0, 10.0),
        parameter(BuiltInParameter.NITRATE, 4, "mg/L", 0.0, 25.0),
        parameter(BuiltInParameter.AMMONIA, 5, "mg/L", 0.0, 0.1),
    )
    private val setup = AquariumSetup(salon, definitions)

    private fun recorded(
        values: Map<BuiltInParameter, Double> = mapOf(BuiltInParameter.PH to 7.8, BuiltInParameter.TEMPERATURE to 25.0),
        actions: List<MaintenanceAction> = listOf(MaintenanceAction("ma1", "s1", MaintenanceActionType.WATER_CHANGE, 20.0, "%", null)),
        events: List<SessionEvent> = listOf(SessionEvent("e1", "s1", EventType.OBSERVATION, "Eau claire")),
    ) = RecordedSession(
        Session("s1", salon.id, occurredAt, occurredAt),
        values.entries.mapIndexed { index, (parameter, value) -> Measurement("m$index", "s1", parameter.storageValue, value) },
        actions,
        events,
    )

    private val sixReadings = mapOf(
        BuiltInParameter.PH to 7.0,
        BuiltInParameter.TEMPERATURE to 25.0,
        BuiltInParameter.GH to 8.0,
        BuiltInParameter.KH to 5.0,
        BuiltInParameter.NITRATE to 10.0,
        BuiltInParameter.AMMONIA to 0.0,
    )

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try { block() } finally { Locale.setDefault(previous) }
    }

    private fun french(block: () -> Unit) = withLocale(Locale.FRENCH, block)

    private val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun ComposeUiTest.details(
        recorded: RecordedSession = recorded(),
        returnToTimeline: Boolean = false,
        showDeleteConfirmation: Boolean = false,
        actions: SessionDetailsActions = SessionDetailsActions(),
        fontScale: Float = 1f,
    ) {
        setContent {
            AquaLogTheme(false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Box(Modifier.size(360.dp, 760.dp).testTag("frame")) {
                        SessionDetailsContent(
                            recorded, setup, returnToTimeline, showDeleteConfirmation, LocalDate.of(2025, 11, 18), actions,
                        )
                    }
                }
            }
        }
        waitForIdle()
    }

    /** The node matching [matcher], scrolled into the list first: rows below the fold are not composed. */
    private fun ComposeUiTest.row(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        onNodeWithTag("session-details-list").performScrollToNode(matcher)
        return onNode(matcher)
    }

    private fun ComposeUiTest.tileBounds(parameter: BuiltInParameter) =
        row(hasTestTag("session-reading-${parameter.storageValue}")).fetchSemanticsNode().boundsInRoot

    @Test
    fun `header names the date, the screen and the Aquarium`() = french {
        runComposeUiTest {
            details()
            val date = LocalizedFormatters(Locale.FRENCH).shortDateTime(occurredAt)
            onNode(hasText(date) and isHeading()).assertIsDisplayed()
            onNode(hasText("Détails de la Session · Salon")).assertIsDisplayed()
        }
    }

    @Test
    fun `readings are tiles spoken with their state`() = french {
        runComposeUiTest {
            details()
            // pH's unit is pH, so it is not said twice; 7,8 is above 7,5: out of the indicative range, in words.
            row(hasText("pH, 7,8, hors de votre plage indicative")).assertIsDisplayed()
            row(hasText("Température, 25, °C")).assertIsDisplayed()
            // The shape that carries the state beyond colour: on the pH tile only.
            onAllNodes(hasTestTag(LEVEL_MARK_TAG), useUnmergedTree = true).assertCountEquals(1)
            val tile = tileBounds(BuiltInParameter.PH)
            val mark = onNode(hasTestTag(LEVEL_MARK_TAG), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(tile.contains(mark.center), "mark $mark in tile $tile")
        }
    }

    @Test
    fun `three columns, two at 150 percent`() {
        val order = listOf(
            BuiltInParameter.PH, BuiltInParameter.TEMPERATURE, BuiltInParameter.GH,
            BuiltInParameter.KH, BuiltInParameter.NITRATE, BuiltInParameter.AMMONIA,
        )
        french {
            runComposeUiTest {
                details(recorded(values = sixReadings))
                val tiles = order.map { tileBounds(it) }
                assertEquals(1, tiles.take(3).map { it.top }.distinct().size, "row 1: $tiles")
                assertTrue(tiles[3].top > tiles[0].bottom, "4th tile on row 2: $tiles")
                assertEquals(tiles[3].top, tiles[5].top)
                val frame = onNodeWithTag("frame").fetchSemanticsNode().boundsInRoot
                tiles.forEach { assertTrue(it.left >= frame.left + 15f && it.right <= frame.right - 15f, "tile $it in $frame") }
            }
        }
        withLocale(Locale.GERMAN) {
            runComposeUiTest {
                details(recorded(values = sixReadings), fontScale = 1.5f)
                val tiles = order.map { tileBounds(it) }
                assertEquals(tiles[0].top, tiles[1].top, "two per row: $tiles")
                assertTrue(tiles[2].top >= tiles[0].bottom, "3rd tile on row 2: $tiles")
                assertEquals(tiles[2].top, tiles[3].top)
                assertTrue(tiles[4].top >= tiles[2].bottom, "5th tile on row 3: $tiles")
                assertEquals(tiles[4].top, tiles[5].top)
                val frame = onNodeWithTag("frame").fetchSemanticsNode().boundsInRoot
                tiles.forEach { assertTrue(it.left >= frame.left && it.right <= frame.right, "tile $it in $frame") }
            }
        }
    }

    @Test
    fun `back says where it returns`() = french {
        runComposeUiTest {
            var backs = 0
            details(returnToTimeline = true, actions = SessionDetailsActions(onBack = { backs++ }))
            onNode(hasContentDescription("Retour à la Chronologie"))
                .assert(button).assertHeightIsAtLeast(48.dp).performClick()
            assertEquals(1, backs)
        }
    }

    @Test
    fun `back from the home is plain back`() = french {
        runComposeUiTest {
            details(returnToTimeline = false)
            onNode(hasContentDescription("Retour")).assert(button)
            onAllNodes(hasContentDescription("Retour à la Chronologie")).assertCountEquals(0)
        }
    }

    @Test
    fun `maintenance and notes are read-only rows under their headings`() = french {
        runComposeUiTest {
            details(
                recorded(
                    events = listOf(
                        SessionEvent("e1", "s1", EventType.OBSERVATION, "Eau claire"),
                        SessionEvent("e2", "s1", EventType.INCIDENT, "Filtre bouché"),
                    ),
                ),
            )
            row(hasText("ACTIONS D'ENTRETIEN") and isHeading()).assertIsDisplayed()
            val water = row(hasText("Changement d'eau") and hasText("20 %"))
            water.assertIsDisplayed()
            assertTrue(!water.fetchSemanticsNode().config.contains(SemanticsProperties.Role), "a maintenance row is not a button")
            onAllNodes(hasText("Changement d'eau") and hasClickAction()).assertCountEquals(0)

            row(hasText("NOTES") and isHeading()).assertIsDisplayed()
            // Its kind, then the note: one node each, in reading order.
            val observation = row(hasText("Observation") and hasText("Eau claire")).fetchSemanticsNode()
            assertEquals(listOf("Observation", "Eau claire"), observation.config[SemanticsProperties.Text].map { it.text })
            row(hasText("Incident") and hasText("Filtre bouché")).assertIsDisplayed()
            onAllNodes(hasText("Eau claire") and hasClickAction()).assertCountEquals(0)
            val notesLabel = row(hasText("NOTES")).fetchSemanticsNode().boundsInRoot
            val waterTop = row(hasText("Changement d'eau") and hasText("20 %")).fetchSemanticsNode().boundsInRoot.top
            assertTrue(waterTop < notesLabel.top, "maintenance before notes")
        }
    }

    @Test
    fun `no maintenance nor notes, no empty groups`() = french {
        runComposeUiTest {
            details(recorded(actions = emptyList(), events = emptyList()))
            // The buttons close the list: once they are composed, everything above them was too.
            row(hasText("Modifier la session")).assertIsDisplayed()
            row(hasText("pH, 7,8, hors de votre plage indicative"))
            onAllNodes(hasText("ACTIONS D'ENTRETIEN")).assertCountEquals(0)
            onAllNodes(hasText("NOTES")).assertCountEquals(0)
        }
    }

    @Test
    fun `edit and delete stay as buttons`() = french {
        runComposeUiTest {
            var edits = 0
            var deletions = 0
            details(actions = SessionDetailsActions(onEdit = { edits++ }, onRequestDeletion = { deletions++ }))
            row(hasText("Modifier la session") and hasClickAction())
                .assert(button).assertHeightIsAtLeast(48.dp).performClick()
            assertEquals(1, edits)
            val delete = row(hasText("Supprimer la Session") and hasClickAction())
            delete.assert(button).assertHeightIsAtLeast(48.dp)
            val editBounds = row(hasText("Modifier la session")).fetchSemanticsNode().boundsInRoot
            assertTrue(delete.fetchSemanticsNode().boundsInRoot.top >= editBounds.bottom, "delete under edit")
            delete.performClick()
            assertEquals(1, deletions)
            assertEquals(1, edits)
        }
    }

    @Test
    fun `deletion asks first`() = french {
        runComposeUiTest {
            var confirmed = 0
            var cancelled = 0
            details(
                showDeleteConfirmation = true,
                actions = SessionDetailsActions(onConfirmDeletion = { confirmed++ }, onCancelDeletion = { cancelled++ }),
            )
            // The catalog puts a no-break space before « ? ».
            onNode(hasText("Supprimer cette session\u00A0?")).assertIsDisplayed()
            onNode(hasText("Supprimer") and hasClickAction()).performClick()
            assertEquals(1, confirmed)
            onNode(hasText("Annuler") and hasClickAction()).performClick()
            assertEquals(1, cancelled)
        }
    }
}
