package com.maximerollin.aqualog.a11y

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.home.HabitantsSheetBody
import com.maximerollin.aqualog.shared.Inhabitant
import com.maximerollin.aqualog.shared.encyclopedia.DrawingFamily
import com.maximerollin.aqualog.shared.encyclopedia.decodeSpecies
import com.maximerollin.aqualog.ui.theme.AquaLogTheme
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Habitants sheet body in French: header count, rows with thumbnails, « Retirer » and « Ajouter » (lot 4b, Task 2). */
@OptIn(ExperimentalTestApi::class)
class HabitantsSheetSemanticsTest {
    private val encyclopedia = decodeSpecies(File("src/commonMain/composeResources/files/encyclopedia/species.json").readText())
    private val cherry = Inhabitant("i1", "a", "red-cherry", null, DrawingFamily.SHRIMP, 10, 0L)
    private val oto = Inhabitant("i2", "a", "otocinclus", null, DrawingFamily.SHRIMP, 4, 0L)
    private val custom = Inhabitant("i3", "a", null, "Mon poisson", DrawingFamily.TETRA, 2, 0L)

    private fun french(block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.FRENCH)
        try { block() } finally { Locale.setDefault(previous) }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.sheet(groups: List<Inhabitant>, onAdd: () -> Unit = {}) {
        setContent {
            AquaLogTheme(false) {
                Box(Modifier.size(360.dp, 760.dp)) {
                    HabitantsSheetBody(groups, encyclopedia, "fr", "Salon", onRemove = { _, _, _ -> }, onAdd = onAdd)
                }
            }
        }
        waitForIdle()
    }

    @Test
    fun `header counts the inhabitants in the aquarium`() = french {
        runComposeUiTest {
            sheet(listOf(cherry, oto))
            onNodeWithText("14 dans Salon").assertIsDisplayed()
        }
    }

    @Test
    fun `row speaks name and count and scientific name`() = french {
        runComposeUiTest {
            sheet(listOf(cherry, oto))
            onNodeWithText("Crevette red cherry\u00A0×\u00A010").assertIsDisplayed()
            onNodeWithText("Neocaridina davidi").assertIsDisplayed()
        }
    }

    @Test
    fun `inhabitant without species has no scientific line`() = french {
        runComposeUiTest {
            sheet(listOf(cherry, custom))
            onNodeWithText("Mon poisson\u00A0×\u00A02").assertIsDisplayed()
            val scientific = encyclopedia.map { it.scientificName }.toSet()
            val shown = scientific.sumOf { onAllNodesWithText(it).fetchSemanticsNodes().size }
            assertEquals(1, shown)
        }
    }

    @Test
    fun `remove is a named 48 dp button per row`() = french {
        runComposeUiTest {
            sheet(listOf(cherry, oto))
            onNodeWithContentDescription("Retirer des Crevette red cherry…").assertHeightIsAtLeast(48.dp).performClick()
            onNodeWithText("Retirer 1").assertIsDisplayed()
        }
    }

    @Test
    fun `add button opens add`() = french {
        runComposeUiTest {
            var added = 0
            sheet(listOf(cherry), onAdd = { added++ })
            onNodeWithText("Ajouter un habitant").performClick()
            assertEquals(1, added)
        }
    }
}
