package com.maximerollin.aqualog.a11y

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximerollin.aqualog.LocalizedFormatters
import com.maximerollin.aqualog.MeasureTile
import com.maximerollin.aqualog.MeasureTileRow
import com.maximerollin.aqualog.SESSION_TILE_STATE_TAG
import com.maximerollin.aqualog.SESSION_TILE_UNIT_TAG
import com.maximerollin.aqualog.sessionColumns
import com.maximerollin.aqualog.sessionImeAction
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.ui.theme.AquaLogTheme
import com.maximerollin.aqualog.ui.theme.LEVEL_MARK_TAG
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SessionTilesSemanticsTest {
    private fun definition(parameter: BuiltInParameter, min: Double? = null, max: Double? = null) = ParameterDefinition(
        id = "d-${parameter.storageValue}", aquariumId = "a1", parameter = parameter, isActive = true,
        position = 0, unit = OnboardingPresets.presetUnit(parameter), precision = 1,
        indicativeMinimum = min, indicativeMaximum = max,
    )

    private val ph = definition(BuiltInParameter.PH, 6.5, 7.5)
    private val gh = definition(BuiltInParameter.GH, 4.0, 12.0)
    private val kh = definition(BuiltInParameter.KH, 3.0, 8.0)
    private val nitrite = definition(BuiltInParameter.NITRITE, 0.0, 0.0)
    private val temperature = definition(BuiltInParameter.TEMPERATURE, 24.0, 27.0)

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try { block() } finally { Locale.setDefault(previous) }
    }

    private fun withEnglish(block: () -> Unit) = withLocale(Locale.ENGLISH, block)

    /** The grid as the Session screen builds it: rows of [columns] tiles, « Next » moves focus, the last tile is « Done ». */
    @Composable
    private fun Grid(definitions: List<ParameterDefinition>, inputs: Map<String, String>, columns: Int, formatter: LocalizedFormatters) {
        val focusManager = LocalFocusManager.current
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            definitions.chunked(columns).forEach { row ->
                val tiles: List<@Composable (Modifier) -> Unit> = row.map { definition ->
                    @Composable { tileModifier: Modifier ->
                        MeasureTile(
                            definition = definition,
                            input = inputs[definition.id].orEmpty(),
                            profile = AquariumProfile.ESTABLISHED,
                            formatter = formatter,
                            imeAction = sessionImeAction(definitions.indexOf(definition), definitions.lastIndex),
                            onValueChange = {},
                            onNext = { focusManager.moveFocus(FocusDirection.Next) },
                            onDone = { focusManager.clearFocus() },
                            modifier = tileModifier.testTag("tile-${definition.id}"),
                        )
                    }
                }
                MeasureTileRow(tiles, columns)
            }
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.english(inputs: Map<String, String>, definitions: List<ParameterDefinition> = listOf(ph)) =
        setContent { AquaLogTheme(false) { Grid(definitions, inputs, 2, LocalizedFormatters(Locale.ENGLISH)) } }

    private fun SemanticsNode.stateDescription(): String = config[SemanticsProperties.StateDescription]

    /** The words of the visible lines that were merged into the field node: none, since the state description says them. */
    private fun SemanticsNode.mergedTexts(): List<String> = config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    @Test
    fun `a tile is one editable node named after its parameter`() = withEnglish {
        runComposeUiTest {
            english(emptyMap())
            val field = onNodeWithContentDescription("Measurement for pH").assert(hasSetTextAction()).fetchSemanticsNode()
            // pH's unit is its name, so it is not said again; the state, then the range.
            assertEquals("Tap to enter, 6.5–7.5", field.stateDescription())
            assertTrue(field.mergedTexts().none { "Tap to enter" in it || "6.5" in it }, "${field.mergedTexts()}")
            onNodeWithTag(SESSION_TILE_STATE_TAG, useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun `above the range is said in words and marked`() = withEnglish {
        runComposeUiTest {
            english(mapOf(ph.id to "7,8"))
            val field = onNodeWithContentDescription("Measurement for pH").fetchSemanticsNode()
            assertEquals("Above the range, 6.5–7.5", field.stateDescription())
            assertTrue(field.mergedTexts().none { "Above" in it }, "${field.mergedTexts()}")
            onNodeWithTag(LEVEL_MARK_TAG, useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun `invalid input reads as an error`() = withEnglish {
        runComposeUiTest {
            english(mapOf(ph.id to "7,8,1"))
            val field = onNodeWithContentDescription("Measurement for pH").fetchSemanticsNode()
            assertTrue(field.config[SemanticsProperties.Error].startsWith("Enter a decimal number"))
            // The error says it once: neither the state description nor the merged visible line repeats it.
            assertEquals("6.5–7.5", field.stateDescription())
            assertTrue(field.mergedTexts().none { "decimal" in it }, "${field.mergedTexts()}")
            onNodeWithTag(SESSION_TILE_STATE_TAG, useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun `the unit follows the value, not the far edge`() = withEnglish {
        runComposeUiTest {
            lateinit var measurer: TextMeasurer
            lateinit var baseStyle: TextStyle
            var inputs by mutableStateOf(mapOf(temperature.id to "24"))
            setContent {
                measurer = rememberTextMeasurer()
                AquaLogTheme(false) {
                    baseStyle = LocalTextStyle.current
                    Grid(listOf(temperature), inputs, 2, LocalizedFormatters(Locale.ENGLISH))
                }
            }
            fun assertUnitAfter(shown: String) {
                val tile = onNodeWithTag("tile-${temperature.id}").fetchSemanticsNode().boundsInRoot
                val unit = onNodeWithTag(SESSION_TILE_UNIT_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                // The value is drawn in the field's own style, the « — » placeholder as a themed Text.
                val style = if (shown == "—") {
                    baseStyle.merge(TextStyle(fontSize = 28.sp, lineHeight = 34.sp))
                } else {
                    TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
                }
                val valueWidth = measurer.measure(shown, style).size.width
                // The tile's 14 dp padding, the value, then a 4 dp gap.
                val expected = tile.left + with(density) { 14.dp.toPx() + 4.dp.toPx() } + valueWidth
                assertEquals(expected, unit.left, with(density) { 2.dp.toPx() }, "« $shown » then the unit at $unit in $tile")
            }
            assertUnitAfter("24")
            inputs = mapOf(temperature.id to "24.55")
            waitForIdle()
            assertUnitAfter("24.55")
            // Empty, the unit follows the « — » placeholder.
            inputs = emptyMap()
            waitForIdle()
            assertUnitAfter("—")
        }
    }

    @Test
    fun `next moves right then down, last is done`() = withEnglish {
        runComposeUiTest {
            english(emptyMap(), listOf(ph, gh, kh))
            val phNode = onNodeWithContentDescription("Measurement for pH")
            val ghNode = onNodeWithContentDescription("Measurement for GH")
            val khNode = onNodeWithContentDescription("Measurement for KH")
            // GH is beside pH, KH alone on the second row.
            val phBounds = phNode.fetchSemanticsNode().boundsInRoot
            val ghBounds = ghNode.fetchSemanticsNode().boundsInRoot
            val khBounds = khNode.fetchSemanticsNode().boundsInRoot
            assertEquals(phBounds.top, ghBounds.top)
            assertEquals(phBounds.height, ghBounds.height)
            assertTrue(khBounds.top >= phBounds.bottom, "pH $phBounds, KH $khBounds")
            phNode.requestFocus()
            phNode.assertIsFocused()
            phNode.performImeAction()
            ghNode.assertIsFocused()
            ghNode.performImeAction()
            khNode.assertIsFocused()
            khNode.assert(SemanticsMatcher.expectValue(SemanticsProperties.ImeAction, ImeAction.Done))
            // A lone last tile keeps half the width.
            assertEquals(phBounds.width, khBounds.width, 1f)
        }
    }

    @Test
    fun `german at 150 percent stacks the tiles`() = withLocale(Locale.GERMAN) {
        runComposeUiTest {
            val columns = sessionColumns(1.5f, 360.dp)
            assertEquals(1, columns)
            // The longest state lines: the invalid-input sentence and « Weit über dem Richtbereich ».
            val inputs = mapOf(ph.id to "7,8,1", nitrite.id to "0,6")
            lateinit var measurer: TextMeasurer
            lateinit var baseStyle: TextStyle
            setContent {
                AquaLogTheme(false) {
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.5f)) {
                        measurer = rememberTextMeasurer()
                        baseStyle = LocalTextStyle.current
                        Box(Modifier.width(360.dp)) {
                            Grid(listOf(ph, nitrite), inputs, columns, LocalizedFormatters(Locale.GERMAN))
                        }
                    }
                }
            }
            val first = onNodeWithTag("tile-${ph.id}").fetchSemanticsNode().boundsInRoot
            val second = onNodeWithTag("tile-${nitrite.id}").fetchSemanticsNode().boundsInRoot
            assertTrue(second.top >= first.bottom, "tile 1 $first, tile 2 $second")
            // Stacked, both tiles take the whole width: the lone tile is not left at half width.
            assertEquals(first.width, second.width)
            assertEquals(360 * density.density, first.width, 1f)
            // The state lines are silent (their words are in the state description), so they are found by tag, top to bottom.
            val lines = onAllNodesWithTag(SESSION_TILE_STATE_TAG, useUnmergedTree = true).fetchSemanticsNodes()
                .map { it.boundsInRoot }.sortedBy { it.top }
            assertEquals(2, lines.size)
            val texts = listOf("Geben Sie eine Dezimalzahl mit einem Punkt oder Komma ein.", "Weit über dem Richtbereich")
            for ((index, text) in texts.withIndex()) {
                val line = lines[index]
                val tile = if (index == 0) first else second
                // Not clipped: the line's box is as tall as the text needs at its width, and lies inside its tile.
                val needed = measurer.measure(
                    text, baseStyle.merge(TextStyle(fontSize = 12.sp, lineHeight = 16.sp)),
                    constraints = Constraints(maxWidth = line.width.toInt()),
                ).size.height
                assertTrue(line.height + 0.5f >= needed, "« $text » needs $needed px, has ${line.height}")
                assertTrue(line.left >= tile.left && line.right <= tile.right && line.bottom <= tile.bottom, "« $text » $line outside $tile")
            }
        }
    }
}
