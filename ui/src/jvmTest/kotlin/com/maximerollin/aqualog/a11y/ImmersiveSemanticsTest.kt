package com.maximerollin.aqualog.a11y

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.HomeValue
import com.maximerollin.aqualog.LocalizedFormatters
import com.maximerollin.aqualog.MainDestination
import com.maximerollin.aqualog.Trend
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.ui.theme.WaterHeader
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.assistance.ReadingLevel
import com.maximerollin.aqualog.ui.icon.AquagendaIcons
import com.maximerollin.aqualog.ui.icon.Plus
import com.maximerollin.aqualog.ui.icon.X
import com.maximerollin.aqualog.ui.icon.Funnel
import com.maximerollin.aqualog.SessionTileState
import com.maximerollin.aqualog.ui.theme.PERIOD_PILL_TAG
import com.maximerollin.aqualog.ui.theme.PeriodSegmented
import com.maximerollin.aqualog.ui.theme.ValuePill
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
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
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithTag
import com.maximerollin.aqualog.ui.icon.Pencil
import com.maximerollin.aqualog.ui.icon.ArrowLeft
import com.maximerollin.aqualog.ui.theme.FilledField
import com.maximerollin.aqualog.ui.theme.LargeFontScale
import com.maximerollin.aqualog.ui.theme.GlassChip
import com.maximerollin.aqualog.ui.theme.PickerTile
import com.maximerollin.aqualog.ui.theme.SETTINGS_DIVIDER_TAG
import com.maximerollin.aqualog.ui.theme.SettingsGroup
import com.maximerollin.aqualog.ui.theme.SettingsRow
import com.maximerollin.aqualog.ui.theme.FULL_SCREEN_BACKING_TAG
import com.maximerollin.aqualog.ui.theme.WaterFullScreen
import androidx.compose.material3.Text

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

    @Test
    fun `water header title is a heading and close is a 48 dp glass button`() = withEnglish {
        runComposeUiTest {
            var closed = 0
            setContent {
                AquaLogTheme(false) {
                    WaterHeader(
                        Aquarium("a", "Salon", 120.0, VolumeUnit.LITERS, 0L), "New Session", "Salon",
                        action = { GlassIconButton(AquagendaIcons.X, "Cancel", { closed++ }) },
                    )
                }
            }
            val title = onNodeWithText("New Session").fetchSemanticsNode()
            assertTrue(title.config.contains(SemanticsProperties.Heading))
            val close = onNodeWithContentDescription("Cancel")
            val size = close.fetchSemanticsNode().size
            assertTrue(size.width >= 48 * density.density && size.height >= 48 * density.density, "$size")
            close.performClick()
            assertEquals(1, closed)
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

    @Test
    fun `period options are radio buttons with one selected and 48 dp`() = withEnglish {
        runComposeUiTest {
            var picked = -1
            setContent {
                AquaLogTheme(false) {
                    PeriodSegmented(listOf("All time", "7 days", "30 days", "90 days"), 2, { picked = it }, Modifier.width(360.dp))
                }
            }
            val radios = onAllNodes(SemanticsMatcher("radio") { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton })
                .fetchSemanticsNodes()
            assertEquals(4, radios.size)
            assertEquals(listOf(false, false, true, false), radios.map { it.config[SemanticsProperties.Selected] })
            radios.forEach { assertTrue(it.size.height >= 48 * density.density, "${it.size}") }
            onNodeWithText("7 days").performClick()
            assertEquals(1, picked)
        }
    }

    @Test
    fun `settings row value moves under its label from the 130 percent preset`() {
        for ((scale, stacked) in listOf(LargeFontScale - .01f to false, LargeFontScale to true)) {
            runComposeUiTest {
                setContent {
                    AquaLogTheme(false) {
                        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = scale)) {
                            Box(Modifier.width(360.dp)) { SettingsRow("Rythme", null, value = "Hebdomadaire") }
                        }
                    }
                }
                val label = onNodeWithText("Rythme", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val value = onNodeWithText("Hebdomadaire", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertEquals(stacked, value.top >= label.bottom, "at $scale: label $label, value $value")
            }
        }
    }

    @Test
    fun `german period labels stay on one line at 150 percent`() = runComposeUiTest {
        val labels = listOf("Gesamter Zeitraum", "7 Tage", "30 Tage", "90 Tage")
        setContent {
            AquaLogTheme(false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.5f)) {
                    PeriodSegmented(labels, 0, {}, Modifier.width(360.dp))
                }
            }
        }
        for (label in labels) {
            val node = onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes().first()
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            assertEquals(1, results.single().lineCount, label)
        }
    }

    @Test
    fun `water header shows its action`() = withEnglish {
        runComposeUiTest {
            setContent {
                AquaLogTheme(false) {
                    WaterHeader(
                        Aquarium("a", "Salon", 120.0, VolumeUnit.LITERS, 0L), "History", "Salon",
                        action = { GlassIconButton(AquagendaIcons.Funnel, "Filters", {}) },
                    )
                }
            }
            val size = onNodeWithContentDescription("Filters").fetchSemanticsNode().size
            assertTrue(size.width >= 48 * density.density && size.height >= 48 * density.density, "$size")
            onNodeWithContentDescription("Cancel").assertDoesNotExist()
        }
    }

    @Test
    fun `an out of range value pill carries the mark`() = withEnglish {
        runComposeUiTest {
            setContent {
                AquaLogTheme(false) {
                    Column {
                        ValuePill("pH", "7.8", null, SessionTileState.ABOVE, Modifier.testTag("out"))
                        ValuePill("pH", "7.0", null, SessionTileState.IN_RANGE, Modifier.testTag("in"))
                    }
                }
            }
            val marks = onAllNodes(hasTestTag(LEVEL_MARK_TAG), useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals(1, marks.size)
            val out = onNodeWithTag("out", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(out.contains(marks.single().boundsInRoot.center))
        }
    }

    @Test
    fun `period options fill the track in equal segments`() = withEnglish {
        runComposeUiTest {
            setContent {
                AquaLogTheme(false) {
                    PeriodSegmented(listOf("All time", "7 days", "30 days", "90 days"), 0, {}, Modifier.width(360.dp).testTag("track"))
                }
            }
            val track = onNodeWithTag("track").fetchSemanticsNode().boundsInRoot
            val radios = onAllNodes(SemanticsMatcher("radio") { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton })
                .fetchSemanticsNodes().map { it.boundsInRoot }.sortedBy { it.left }
            val tolerance = density.density
            assertEquals(track.left, radios.first().left, tolerance)
            assertEquals(track.right, radios.last().right, tolerance)
            radios.forEach { assertEquals(radios.first().width, it.width, tolerance) }
            // The selected pill fills its segment, inside the segment's 3 dp inset, not just its label.
            val pill = onNodeWithTag(PERIOD_PILL_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val inset = 3 * density.density
            val segment = radios.first()
            assertEquals(segment.left + inset, pill.left, tolerance)
            assertEquals(segment.right - inset, pill.right, tolerance)
            assertEquals(segment.top + inset, pill.top, tolerance)
            assertEquals(segment.bottom - inset, pill.bottom, tolerance)
        }
    }

    @Test
    fun `no period is selected at index -1`() = withEnglish {
        runComposeUiTest {
            setContent {
                AquaLogTheme(false) {
                    PeriodSegmented(listOf("7 days", "30 days", "90 days"), -1, {}, Modifier.width(360.dp))
                }
            }
            val radios = onAllNodes(SemanticsMatcher("radio") { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton })
                .fetchSemanticsNodes()
            assertEquals(3, radios.size)
            assertEquals(listOf(false, false, false), radios.map { it.config[SemanticsProperties.Selected] })
            onNodeWithTag(PERIOD_PILL_TAG, useUnmergedTree = true).assertDoesNotExist()
        }
    }

    private val aquariumSalon = Aquarium("a", "Salon", 120.0, VolumeUnit.LITERS, 0L)

    private fun role(node: androidx.compose.ui.semantics.SemanticsNode) = node.config.getOrNull(SemanticsProperties.Role)

    @Test
    fun `settings row is one button with label and value`() = withEnglish {
        runComposeUiTest {
            setContent {
                AquaLogTheme(false) {
                    SettingsRow("Rythme d’analyse", AquagendaIcons.Pencil, value = "Chaque semaine", onClick = {})
                }
            }
            val node = onNode(hasText("Rythme d’analyse") and hasClickAction()).fetchSemanticsNode()
            assertEquals(Role.Button, role(node))
            val spoken = node.config[SemanticsProperties.Text].joinToString(" ") { it.text }
            assertTrue(spoken.contains("Chaque semaine"), spoken)
            assertEquals(1, onAllNodes(hasText("Rythme d’analyse") and hasClickAction()).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `disabled settings row is not clickable`() = withEnglish {
        runComposeUiTest {
            setContent {
                AquaLogTheme(false) { SettingsRow("Archiver", AquagendaIcons.Pencil, onClick = {}, enabled = false) }
            }
            onNode(hasText("Archiver")).assertIsNotEnabled()
        }
    }

    @Test
    fun `a long value leaves the label on one line`() = withEnglish {
        runComposeUiTest {
            setContent {
                AquaLogTheme(false) {
                    Box(Modifier.width(328.dp)) {
                        SettingsRow("Local uniquement", AquagendaIcons.Pencil, value = "○ Synchronisation en attente")
                    }
                }
            }
            val layouts = mutableListOf<TextLayoutResult>()
            onNode(hasText("Local uniquement"), useUnmergedTree = true).fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            assertEquals(1, layouts.single().lineCount)
            onNode(hasText("○ Synchronisation en attente"), useUnmergedTree = true).assertExists()
        }
    }

    /** Contrast of the darkest pixel of [text] against [background]: the glyphs' own colour, as drawn. */
    private fun ComposeUiTest.inkContrast(text: String, background: Color): Float {
        val pixels = onNode(hasText(text), useUnmergedTree = true).captureToImage().toPixelMap()
        var darkest = background.luminance()
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) darkest = minOf(darkest, pixels[x, y].luminance())
        return (background.luminance() + .05f) / (darkest + .05f)
    }

    @Test
    fun `a disabled row dims its label but keeps its reason readable`() = withEnglish {
        runComposeUiTest {
            var surface = Color.Unspecified
            setContent {
                AquaLogTheme(false) {
                    surface = MaterialTheme.colorScheme.surface
                    // A large density gives the glyphs solid stems, so their darkest pixel is their colour.
                    CompositionLocalProvider(LocalDensity provides Density(3f)) {
                        Column(Modifier.background(surface)) {
                            SettingsRow("Archive", AquagendaIcons.Pencil, supporting = "Only one Aquarium in Free", onClick = {}, enabled = false)
                            SettingsRow("Rhythm", AquagendaIcons.Pencil, onClick = {})
                        }
                    }
                }
            }
            onNode(hasText("Archive")).assertIsNotEnabled()
            val reason = inkContrast("Only one Aquarium in Free", surface)
            assertTrue(reason >= 4.5f, "reason $reason")
            val dimmed = inkContrast("Archive", surface)
            val live = inkContrast("Rhythm", surface)
            assertTrue(dimmed < live * .7f, "label not dimmed: $dimmed vs $live")
        }
    }

    @Test
    fun `settings group draws a line between rows only`() = withEnglish {
        runComposeUiTest {
            setContent {
                AquaLogTheme(false) {
                    SettingsGroup {
                        SettingsRow("A", null, onClick = {})
                        SettingsRow("B", null, onClick = {})
                    }
                }
            }
            assertEquals(1, onAllNodesWithTag(SETTINGS_DIVIDER_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `settings group has no line for one row and two for three`() = withEnglish {
        for ((rows, lines) in listOf(1 to 0, 3 to 2)) {
            runComposeUiTest {
                setContent {
                    AquaLogTheme(false) { SettingsGroup { repeat(rows) { SettingsRow("R$it", null, onClick = {}) } } }
                }
                assertEquals(lines, onAllNodesWithTag(SETTINGS_DIVIDER_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
            }
        }
    }

    @Test
    fun `full-screen content sits at the bottom with the water free above it`() = withEnglish {
        runComposeUiTest {
            setContent {
                AquaLogTheme(false) {
                    Box(Modifier.width(360.dp).height(640.dp)) { WaterFullScreen(aquariumSalon) { Text("Bonjour") } }
                }
            }
            val root = onRoot().fetchSemanticsNode().boundsInRoot
            val text = onNodeWithText("Bonjour").fetchSemanticsNode().boundsInRoot
            val backing = onNodeWithTag(FULL_SCREEN_BACKING_TAG).fetchSemanticsNode().boundsInRoot
            assertTrue(backing.top > root.top + root.height / 2, "backing starts at the content, not the screen top: $root $backing")
            assertTrue(text.top >= backing.top + 48 * density.density, "48 dp free of text: $backing $text")
            assertTrue(text.top > root.top + root.height / 2, "$root $text")
            assertTrue(text.bottom <= root.bottom, "$root $text")
        }
    }

    @Test
    fun `filled field is named by its label when empty`() = withEnglish {
        runComposeUiTest {
            var typed = ""
            setContent { AquaLogTheme(false) { FilledField("", { typed = it }, "Titre de la tâche") } }
            // The label is text on the editable node itself (M3's label slot); a contentDescription would hide the typed text (#45).
            val config = onNode(hasSetTextAction()).fetchSemanticsNode().config
            assertEquals(listOf("Titre de la tâche"), config.getOrNull(SemanticsProperties.Text)?.map { it.text }, "$config")
            assertEquals(null, config.getOrNull(SemanticsProperties.ContentDescription), "$config")
            onAllNodesWithText("Titre de la tâche").assertCountEquals(1)
            onNode(hasSetTextAction()).performTextInput("x")
            assertEquals("x", typed)
        }
    }

    @Test
    fun `filled field keeps its label and its typed text on one node`() = withEnglish {
        runComposeUiTest {
            setContent { AquaLogTheme(false) { FilledField("Vitre", {}, "Titre de la tâche", testTag = "title") } }
            val config = onNode(hasSetTextAction()).fetchSemanticsNode().config
            assertEquals("title", config.getOrNull(SemanticsProperties.TestTag))
            assertEquals("Vitre", config.getOrNull(SemanticsProperties.EditableText)?.text)
            assertEquals(listOf("Titre de la tâche"), config.getOrNull(SemanticsProperties.Text)?.map { it.text })
            assertEquals(null, config.getOrNull(SemanticsProperties.ContentDescription))
            onNode(hasSetTextAction()).assertHeightIsAtLeast(56.dp)
        }
    }

    @Test
    fun `filled field in error exposes its message`() = withEnglish {
        runComposeUiTest {
            setContent { AquaLogTheme(false) { FilledField("", {}, "Titre de la tâche", isError = true, errorMessage = "Requis") } }
            val config = onNode(hasSetTextAction()).fetchSemanticsNode().config
            assertEquals("Requis", config.getOrNull(SemanticsProperties.Error))
        }
    }

    @Test
    fun `picker tile speaks label and value`() = withEnglish {
        runComposeUiTest {
            setContent { AquaLogTheme(false) { PickerTile("Première date d'échéance", "12 nov. 2026", {}) } }
            val node = onNode(hasClickAction()).fetchSemanticsNode()
            assertEquals(Role.Button, role(node))
            val spoken = node.config[SemanticsProperties.Text].joinToString(" ") { it.text }
            assertTrue(spoken.contains("Première date d'échéance") && spoken.contains("12 nov. 2026"), spoken)
        }
    }

    @Test
    fun `glass chip is a 48 dp button`() = withEnglish {
        runComposeUiTest {
            setContent { AquaLogTheme(false) { GlassChip("Changer", {}, icon = AquagendaIcons.Pencil) } }
            val chip = onNode(hasClickAction())
            chip.assertHeightIsAtLeast(48.dp)
            assertEquals(Role.Button, role(chip.fetchSemanticsNode()))
        }
    }

    @Test
    fun `water header leading slot comes before the title`() = withEnglish {
        runComposeUiTest {
            setContent {
                AquaLogTheme(false) {
                    WaterHeader(
                        aquariumSalon, "Rythme", "Salon",
                        leading = { GlassIconButton(AquagendaIcons.ArrowLeft, "Retour", {}) },
                    )
                }
            }
            val leading = onNodeWithContentDescription("Retour").fetchSemanticsNode().boundsInRoot
            val title = onNodeWithText("Rythme").fetchSemanticsNode().boundsInRoot
            assertTrue(leading.left < title.left, "$leading $title")
        }
    }

    @Test
    fun `water header honours its minimum height`() = withEnglish {
        runComposeUiTest {
            setContent { AquaLogTheme(false) { WaterHeader(aquariumSalon, "Plus", "Salon", minHeight = 190.dp) } }
            onRoot().assertHeightIsAtLeast(190.dp)
        }
    }
}
