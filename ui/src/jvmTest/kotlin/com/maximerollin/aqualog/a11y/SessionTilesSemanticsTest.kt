package com.maximerollin.aqualog.a11y

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.LocalizedFormatters
import com.maximerollin.aqualog.MeasureTile
import com.maximerollin.aqualog.MeasureTileRow
import com.maximerollin.aqualog.sessionColumns
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.ui.theme.AquaLogTheme
import com.maximerollin.aqualog.ui.theme.LEVEL_MARK_TAG
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
                            imeAction = if (definition == definitions.last()) ImeAction.Done else ImeAction.Next,
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

    @Test
    fun `a tile is one editable node named after its parameter`() = withEnglish {
        runComposeUiTest {
            english(emptyMap())
            onNodeWithContentDescription("Measurement for pH")
                .assert(hasSetTextAction())
                .assert(hasText("Tap to enter", substring = true))
        }
    }

    @Test
    fun `above the range is said in words and marked`() = withEnglish {
        runComposeUiTest {
            english(mapOf(ph.id to "7,8"))
            val state = onNodeWithContentDescription("Measurement for pH").fetchSemanticsNode()
                .config[SemanticsProperties.StateDescription]
            assertTrue("Above the range" in state, state)
            onNodeWithTag(LEVEL_MARK_TAG, useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun `invalid input reads as an error`() = withEnglish {
        runComposeUiTest {
            english(mapOf(ph.id to "7,8,1"))
            val config = onNodeWithContentDescription("Measurement for pH").fetchSemanticsNode().config
            assertTrue(config.contains(SemanticsProperties.Error))
            onNodeWithText("Enter a decimal number", substring = true, useUnmergedTree = true).assertExists()
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
            // The longest state lines: the invalid-input sentence and « Weit über dem Richtbereich ».
            val inputs = mapOf(ph.id to "7,8,1", nitrite.id to "0,6")
            setContent {
                AquaLogTheme(false) {
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.5f)) {
                        BoxWithConstraints(Modifier.width(360.dp)) {
                            val columns = sessionColumns(LocalDensity.current.fontScale, maxWidth)
                            Grid(listOf(ph, nitrite), inputs, columns, LocalizedFormatters(Locale.GERMAN))
                        }
                    }
                }
            }
            val first = onNodeWithTag("tile-${ph.id}").fetchSemanticsNode().boundsInRoot
            val second = onNodeWithTag("tile-${nitrite.id}").fetchSemanticsNode().boundsInRoot
            assertTrue(second.top >= first.bottom, "tile 1 $first, tile 2 $second")
            for (text in listOf("Geben Sie eine Dezimalzahl mit einem Punkt oder Komma ein.", "Weit über dem Richtbereich")) {
                val node = onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
                val tile = if (text.startsWith("Geben")) first else second
                val layouts = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                val layout = layouts.single()
                // Not hasVisualOverflow: for a one-line text that hugs its width, the semantics layout reports the paragraph
                // at the full max width and flags a width overflow that is not there. Clipping means a line past the box.
                assertFalse(layout.didOverflowHeight, "« $text » is cut at the bottom")
                assertTrue((0 until layout.lineCount).all { layout.getLineRight(it) <= layout.size.width }, "« $text » is cut at the end")
                assertTrue(node.boundsInRoot.bottom <= tile.bottom, "« $text » ${node.boundsInRoot} outside $tile")
            }
        }
    }
}
