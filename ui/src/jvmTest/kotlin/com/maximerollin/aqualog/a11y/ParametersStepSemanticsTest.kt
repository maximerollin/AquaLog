package com.maximerollin.aqualog.a11y

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.ParameterEditorState
import com.maximerollin.aqualog.labelResource
import com.maximerollin.aqualog.onboarding.OnboardingActions
import com.maximerollin.aqualog.onboarding.ParametersContent
import com.maximerollin.aqualog.resources.Res
import com.maximerollin.aqualog.resources.enabled_for_parameter
import com.maximerollin.aqualog.resources.invalid_parameters
import com.maximerollin.aqualog.resources.move_parameter_down
import com.maximerollin.aqualog.resources.move_parameter_up
import com.maximerollin.aqualog.resources.parameter_minimum
import com.maximerollin.aqualog.resources.parameter_range_invalid
import com.maximerollin.aqualog.resources.parameter_range_none
import com.maximerollin.aqualog.resources.range_sheet_increase
import com.maximerollin.aqualog.resources.range_sheet_invalid
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.ui.theme.AquaLogTheme
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString

/** The Parameters step: one card of rows, drag and TalkBack actions to reorder, and the range sheet (lot 5, Task 4). */
@OptIn(ExperimentalTestApi::class)
class ParametersStepSemanticsTest {
    private val calls = mutableListOf<String>()
    private val actions = OnboardingActions(
        setParameterActive = { p, active -> calls += "setParameterActive($p, $active)" },
        moveParameter = { p, offset -> calls += "moveParameter($p, $offset)" },
        updateParameterUnit = { p, v -> calls += "updateParameterUnit($p, $v)" },
        updateParameterPrecision = { p, v -> calls += "updateParameterPrecision($p, $v)" },
        updateParameterMinimum = { p, v -> calls += "updateParameterMinimum($p, $v)" },
        updateParameterMaximum = { p, v -> calls += "updateParameterMaximum($p, $v)" },
    )

    private fun parameters(phMinimum: String = "6,5", phMaximum: String = "7,5") = listOf(
        ParameterEditorState(BuiltInParameter.TEMPERATURE, true, "°C", "1", "24", "26"),
        ParameterEditorState(BuiltInParameter.PH, true, "pH", "1", phMinimum, phMaximum),
        ParameterEditorState(BuiltInParameter.NITRATE, true, "mg/L", "0", "", "40"),
    )

    private fun inLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try { block() } finally { Locale.setDefault(previous) }
    }

    private fun french(block: () -> Unit) = inLocale(Locale.FRENCH, block)

    private fun str(block: suspend () -> String) = runBlocking { block() }

    private fun label(parameter: BuiltInParameter) = str { getString(parameter.labelResource()) }

    private fun ComposeUiTest.show(
        parameters: List<ParameterEditorState> = parameters(),
        showError: Boolean = false,
        fontScale: Float = 1f,
    ) {
        setContent {
            AquaLogTheme(false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Box(Modifier.size(360.dp, 760.dp)) {
                        ParametersContent(parameters, AquariumProfile.ESTABLISHED, showError, actions)
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.row(parameter: BuiltInParameter) = onNodeWithTag("parameter-row-${parameter.storageValue}")

    private fun ComposeUiTest.customActions(parameter: BuiltInParameter): List<String> =
        row(parameter).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }

    private fun ComposeUiTest.rowTexts(parameter: BuiltInParameter): List<String> =
        row(parameter).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    @Test
    fun `row exposes move actions by position`() = french {
        runComposeUiTest {
            show()
            val temperature = label(BuiltInParameter.TEMPERATURE)
            val ph = label(BuiltInParameter.PH)
            val nitrate = label(BuiltInParameter.NITRATE)
            assertEquals(listOf(str { getString(Res.string.move_parameter_down, temperature) }), customActions(BuiltInParameter.TEMPERATURE))
            assertEquals(
                listOf(str { getString(Res.string.move_parameter_up, ph) }, str { getString(Res.string.move_parameter_down, ph) }),
                customActions(BuiltInParameter.PH),
            )
            assertEquals(listOf(str { getString(Res.string.move_parameter_up, nitrate) }), customActions(BuiltInParameter.NITRATE))
        }
    }

    @Test
    fun `move action calls moveParameter`() = french {
        runComposeUiTest {
            show()
            row(BuiltInParameter.PH).performCustomAccessibilityActionWithLabel(
                str { getString(Res.string.move_parameter_up, label(BuiltInParameter.PH)) },
            )
            assertTrue("moveParameter(PH, -1)" in calls, calls.toString())
        }
    }

    @Test
    fun `dragging the handle down moves the row`() = french {
        runComposeUiTest {
            show()
            val rowHeight = row(BuiltInParameter.PH).fetchSemanticsNode().size.height
            onNodeWithTag("parameter-handle-ph", useUnmergedTree = true).performTouchInput {
                down(center)
                moveBy(Offset(0f, rowHeight * 0.6f))
                up()
            }
            assertTrue("moveParameter(PH, 1)" in calls, calls.toString())
            assertEquals(1, calls.count { it.startsWith("moveParameter") }, calls.toString())
        }
    }

    @Test
    fun `tapping a row opens the range sheet`() = french {
        runComposeUiTest {
            show()
            row(BuiltInParameter.PH).performClick()
            waitForIdle()
            onNodeWithTag("range-sheet").assertIsDisplayed()
            onNode(isHeading() and hasText(label(BuiltInParameter.PH))).assertIsDisplayed()
        }
    }

    @Test
    fun `range sheet plus updates the minimum`() = french {
        runComposeUiTest {
            show()
            row(BuiltInParameter.PH).performClick()
            waitForIdle()
            val increase = str { getString(Res.string.range_sheet_increase, getString(Res.string.parameter_minimum)) }
            onNodeWithContentDescription(increase).performClick()
            assertTrue("updateParameterMinimum(PH, 6,6)" in calls, calls.toString())
        }
    }

    @Test
    fun `typing in the sheet still works`() = french {
        runComposeUiTest {
            show()
            row(BuiltInParameter.PH).performClick()
            waitForIdle()
            onNodeWithTag("range-max").performTextReplacement("8")
            assertTrue("updateParameterMaximum(PH, 8)" in calls, calls.toString())
        }
    }

    @Test
    fun `invalid range shows in sheet and pill`() = french {
        runComposeUiTest {
            show(parameters(phMinimum = "8", phMaximum = "6"))
            val invalid = str { getString(Res.string.parameter_range_invalid) }
            assertTrue(invalid in rowTexts(BuiltInParameter.PH), rowTexts(BuiltInParameter.PH).toString())
            row(BuiltInParameter.PH).performClick()
            waitForIdle()
            onNodeWithText(str { getString(Res.string.range_sheet_invalid) }).assertIsDisplayed()
        }
    }

    @Test
    fun `range pill shows the range and unit`() = french {
        runComposeUiTest {
            show()
            assertTrue("6,5–7,5 pH" in rowTexts(BuiltInParameter.PH), rowTexts(BuiltInParameter.PH).toString())
            assertTrue("≤ 40 mg/L" in rowTexts(BuiltInParameter.NITRATE), rowTexts(BuiltInParameter.NITRATE).toString())
        }
    }

    @Test
    fun `no range pill reads Aucune plage`() = french {
        runComposeUiTest {
            show(parameters(phMinimum = "", phMaximum = ""))
            val none = str { getString(Res.string.parameter_range_none) }
            assertTrue(none in rowTexts(BuiltInParameter.PH), rowTexts(BuiltInParameter.PH).toString())
        }
    }

    @Test
    fun `error shows under the card`() = french {
        runComposeUiTest {
            show(showError = true)
            val message = str { getString(Res.string.invalid_parameters) }
            onNodeWithTag("onboarding-list").performScrollToNode(hasText(message))
            onNodeWithText(message).assertIsDisplayed()
        }
    }

    @Test
    fun `switch is named for its parameter`() = french {
        runComposeUiTest {
            show()
            val description = str { getString(Res.string.enabled_for_parameter, label(BuiltInParameter.PH)) }
            onNode(hasContentDescription(description) and isToggleable()).performClick()
            assertTrue("setParameterActive(PH, false)" in calls, calls.toString())
        }
    }

    @Test
    fun `German 150 percent puts the pill under the name`() = inLocale(Locale.GERMAN) {
        runComposeUiTest {
            show(fontScale = 1.5f)
            val inRow = hasAnyAncestor(hasTestTag("parameter-row-ph"))
            val name = onNode(inRow and hasText(label(BuiltInParameter.PH)), useUnmergedTree = true).getBoundsInRoot()
            val pill = onNode(inRow and hasText("–", substring = true), useUnmergedTree = true).getBoundsInRoot()
            assertTrue(pill.top >= name.bottom, "pill ${pill.top} under name ${name.bottom}")
        }
    }
}
