package com.maximerollin.aqualog.a11y

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.HomeValue
import com.maximerollin.aqualog.LocalizedFormatters
import com.maximerollin.aqualog.MainDestination
import com.maximerollin.aqualog.Trend
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.assistance.ReadingLevel
import com.maximerollin.aqualog.ui.icon.AquagendaIcons
import com.maximerollin.aqualog.ui.icon.Plus
import com.maximerollin.aqualog.ui.theme.AquaLogTheme
import com.maximerollin.aqualog.ui.theme.FloatingNavBar
import com.maximerollin.aqualog.ui.theme.LEVEL_MARK_TAG
import com.maximerollin.aqualog.ui.theme.GlassIconButton
import com.maximerollin.aqualog.ui.theme.PaperSheetTop
import com.maximerollin.aqualog.ui.theme.ValueRow
import com.maximerollin.aqualog.ui.theme.ValueTile
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ImmersiveSemanticsTest {
    private companion object {
        val DEFINITION_PH = ParameterDefinition(
            id = "ph", aquariumId = "a", parameter = BuiltInParameter.PH, isActive = true, position = 0,
            unit = "pH", precision = 1, indicativeMinimum = 6.5, indicativeMaximum = 7.5,
        )
        val TODAY: LocalDate = LocalDate.of(2026, 11, 10)
        val TODAY_MILLIS = TODAY.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        const val DAY_MILLIS = 86_400_000L
    }

    private val formatter = LocalizedFormatters(Locale.ENGLISH)

    private fun withEnglish(block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try { block() } finally { Locale.setDefault(previous) }
    }

    private fun values(count: Int, level: ReadingLevel = ReadingLevel.OK): List<HomeValue> =
        List(count) { HomeValue(DEFINITION_PH, 7.0 + it / 10.0, 0L, level, false) }

    @Test
    fun `tabs are icon-only, named and selectable`() = withEnglish {
        runComposeUiTest {
            setContent { AquaLogTheme(false) { FloatingNavBar(MainDestination.TANK, {}, {}, true) } }
            val tabs = onAllNodes(SemanticsMatcher("tab") { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab })
                .fetchSemanticsNodes()
            assertEquals(4, tabs.size)
            assertEquals(
                listOf("Tank", "History", "Trends", "More"),
                tabs.map { it.config[SemanticsProperties.ContentDescription].single() },
            )
            assertEquals(listOf(true, false, false, false), tabs.map { it.config[SemanticsProperties.Selected] })
            tabs.forEach {
                assertTrue(it.size.width >= 48 * density.density, "width ${it.size.width}")
                assertTrue(it.size.height >= 48 * density.density, "height ${it.size.height}")
            }
        }
    }

    @Test
    fun `measure is disabled while the shown aquarium is not the current one`() = withEnglish {
        runComposeUiTest {
            setContent { AquaLogTheme(false) { FloatingNavBar(MainDestination.TANK, {}, {}, false) } }
            val config = onNodeWithContentDescription("Measure").fetchSemanticsNode().config
            assertTrue(config.contains(SemanticsProperties.Disabled))
        }
    }

    @Test
    fun `no chevron when three values fit`() = withEnglish {
        runComposeUiTest {
            setContent { AquaLogTheme(false) { ValueRow(values(3), formatter, TODAY, Modifier.width(360.dp)) } }
            onNodeWithContentDescription("Previous values").assertDoesNotExist()
            onNodeWithContentDescription("Next values").assertDoesNotExist()
        }
    }

    @Test
    fun `only the next chevron at the start of a long row`() = withEnglish {
        runComposeUiTest {
            setContent { AquaLogTheme(false) { ValueRow(values(7), formatter, TODAY, Modifier.width(360.dp)) } }
            onNodeWithContentDescription("Next values").assertExists()
            onNodeWithContentDescription("Previous values").assertDoesNotExist()
        }
    }

    @Test
    fun `next chevron scrolls and reveals the previous one`() = withEnglish {
        runComposeUiTest {
            setContent { AquaLogTheme(false) { ValueRow(values(7), formatter, TODAY, Modifier.width(360.dp)) } }
            onNodeWithContentDescription("Next values").performClick()
            waitForIdle()
            onNodeWithContentDescription("Previous values").assertExists()
        }
    }

    @Test
    fun `a value tile reads as one node`() = withEnglish {
        runComposeUiTest {
            setContent {
                AquaLogTheme(false) {
                    ValueTile(HomeValue(DEFINITION_PH, 7.8, 0L, ReadingLevel.ATTENTION, false), formatter, TODAY)
                }
            }
            val nodes = onAllNodes(SemanticsMatcher("tile") { it.config.contains(SemanticsProperties.Text) })
                .fetchSemanticsNodes()
            assertEquals(1, nodes.size)
            val text = nodes.single().config[SemanticsProperties.Text].joinToString(" ") { it.text }
            assertTrue("pH" in text && "7.8" in text && "outside" in text.lowercase(), text)
        }
    }

    @Test
    fun `glass button keeps a 48 dp target`() = withEnglish {
        runComposeUiTest {
            setContent { AquaLogTheme(false) { GlassIconButton(AquagendaIcons.Plus, "Back", {}) } }
            val size = onNodeWithContentDescription("Back").fetchSemanticsNode().size
            assertTrue(size.width >= 48 * density.density && size.height >= 48 * density.density, "$size")
        }
    }

    private fun tileText(value: HomeValue): String {
        var text = ""
        runComposeUiTest {
            setContent { AquaLogTheme(false) { ValueTile(value, formatter, TODAY) } }
            text = onAllNodes(SemanticsMatcher("tile") { it.config.contains(SemanticsProperties.Text) })
                .fetchSemanticsNodes().single().config[SemanticsProperties.Text].joinToString(" ") { it.text }
        }
        return text
    }

    @Test
    fun `a value older than the latest measurement says its age, and its trend is spoken`() = withEnglish {
        val stale = HomeValue(DEFINITION_PH, 7.2, TODAY_MILLIS - 30 * DAY_MILLIS, ReadingLevel.OK, true, Trend.UP)
        val text = tileText(stale)
        assertTrue("30 d ago" in text, text)
        assertTrue("rising" in text, text)
    }

    @Test
    fun `a value from the latest measurement has no age, a falling one says so`() = withEnglish {
        val fresh = HomeValue(DEFINITION_PH, 7.2, TODAY_MILLIS - 30 * DAY_MILLIS, ReadingLevel.OK, false, Trend.DOWN)
        val text = tileText(fresh)
        assertFalse("ago" in text, text)
        assertTrue("falling" in text, text)
    }

    @Test
    fun `an out-of-range tile carries a mark beyond colour, an in-range one does not`() = withEnglish {
        runComposeUiTest {
            setContent {
                AquaLogTheme(false) {
                    Column {
                        ValueTile(HomeValue(DEFINITION_PH, 7.8, 0L, ReadingLevel.ATTENTION, false), formatter, TODAY, Modifier.testTag("attention"))
                        ValueTile(HomeValue(DEFINITION_PH, 9.8, 0L, ReadingLevel.ALERT, false), formatter, TODAY, Modifier.testTag("alert"))
                        ValueTile(HomeValue(DEFINITION_PH, 7.0, 0L, ReadingLevel.OK, false), formatter, TODAY, Modifier.testTag("ok"))
                    }
                }
            }
            val marks = onAllNodes(hasTestTag(LEVEL_MARK_TAG), useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals(2, marks.size)
            val tiles = listOf("attention", "alert", "ok").associateWith { onNodeWithTag(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }
            assertTrue(marks.any { tiles.getValue("attention").contains(it.boundsInRoot.center) })
            assertTrue(marks.any { tiles.getValue("alert").contains(it.boundsInRoot.center) })
            assertFalse(marks.any { tiles.getValue("ok").contains(it.boundsInRoot.center) })
        }
    }

    @Test
    fun `a tap on the sheet's top edge does not reach the water under it`() = withEnglish {
        runComposeUiTest {
            var waterTaps = 0
            setContent {
                AquaLogTheme(false) {
                    Box(Modifier.width(360.dp)) {
                        Box(Modifier.fillMaxWidth().height(30.dp).clickable { waterTaps++ })
                        PaperSheetTop(Modifier.testTag("sheet-top"))
                    }
                }
            }
            onNodeWithTag("sheet-top").performTouchInput { click(center) }
            waitForIdle()
            assertEquals(0, waterTaps)
        }
    }
}
