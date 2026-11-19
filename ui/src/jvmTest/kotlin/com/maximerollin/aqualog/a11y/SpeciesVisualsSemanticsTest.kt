package com.maximerollin.aqualog.a11y

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.habitants.FitPill
import com.maximerollin.aqualog.habitants.SpeciesThumb
import com.maximerollin.aqualog.habitants.fitPillText
import com.maximerollin.aqualog.habitants.fitVerdict
import com.maximerollin.aqualog.resources.Res
import com.maximerollin.aqualog.resources.family_tetra
import com.maximerollin.aqualog.shared.encyclopedia.Assessment
import com.maximerollin.aqualog.shared.encyclopedia.Fit
import com.maximerollin.aqualog.shared.encyclopedia.Reason
import com.maximerollin.aqualog.shared.encyclopedia.decodeSpecies
import com.maximerollin.aqualog.ui.theme.AquaLogTheme
import com.maximerollin.aqualog.ui.theme.SearchPill
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The shared pieces of lot 4b: thumbnail, fit pill, search pill. French, so words are checked in a real catalog. */
@OptIn(ExperimentalTestApi::class)
class SpeciesVisualsSemanticsTest {
    private val species = decodeSpecies(File("src/commonMain/composeResources/files/encyclopedia/species.json").readText()).first()

    private fun assessment(fit: Fit, problems: Int, reason: Reason?) = Assessment(species, fit, emptyList(), problems, reason, false)

    private fun french(block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.FRENCH)
        try { block() } finally { Locale.setDefault(previous) }
    }

    @Test
    fun `fit pill says its words without a symbol`() = french {
        runComposeUiTest {
            setContent { AquaLogTheme(false) { FitPill("Compatible", Fit.GOOD) } }
            onNodeWithText("Compatible").assertIsDisplayed()
            val symbols = listOf("✓", "⚠", "✗")
            val withSymbol = SemanticsMatcher("text with a symbol") { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { t -> symbols.any { it in t.text } } == true
            }
            assertTrue(onAllNodes(withSymbol).fetchSemanticsNodes().isEmpty())
        }
    }

    @Test
    fun `pill text for two problems is the plural`() = french {
        runComposeUiTest {
            var text = ""
            setContent { AquaLogTheme(false) { text = fitPillText(assessment(Fit.BAD, 2, Reason.TANK_TOO_SMALL)) } }
            assertEquals("2 problèmes", text)
        }
    }

    @Test
    fun `pill text for a caution is its reason`() = french {
        runComposeUiTest {
            var text = ""
            setContent { AquaLogTheme(false) { text = fitPillText(assessment(Fit.CAUTION, 0, Reason.WATER_TOO_HARD)) } }
            assertEquals("Eau trop dure", text)
        }
    }

    @Test
    fun `pill text without problems or reason is the good one`() = french {
        runComposeUiTest {
            var text = ""
            setContent { AquaLogTheme(false) { text = fitPillText(assessment(Fit.GOOD, 0, null)) } }
            assertEquals("Compatible", text)
        }
    }

    @Test
    fun `verdict names the aquarium without a symbol`() = french {
        runComposeUiTest {
            var text = ""
            setContent { AquaLogTheme(false) { text = fitVerdict(Fit.CAUTION, "Salon") } }
            assertEquals("Avec précautions pour Salon", text)
            assertFalse(text.any { it in "✓⚠✗" })
        }
    }

    @Test
    fun `search pill is named by its placeholder when empty`() = french {
        runComposeUiTest {
            var query by mutableStateOf("")
            var last: String? = null
            setContent {
                AquaLogTheme(false) {
                    SearchPill(query, { last = it; query = it }, "Rechercher une espèce…", testTag = "species-search")
                }
            }
            onNode(hasSetTextAction() and hasText("Rechercher une espèce…")).assertIsDisplayed()
            onNodeWithTag("species-search").assertHeightIsAtLeast(48.dp)
            onNodeWithTag("species-search").performTextInput("oto")
            assertEquals("oto", last)
        }
    }

    @Test
    fun `species thumb is silent`() = french {
        runComposeUiTest {
            setContent {
                AquaLogTheme(false) {
                    Row(Modifier.testTag("row")) {
                        SpeciesThumb(Res.drawable.family_tetra, Color.Red)
                        Text("Néon")
                    }
                }
            }
            val described = SemanticsMatcher("has a contentDescription") { it.config.contains(SemanticsProperties.ContentDescription) }
            assertTrue(onAllNodes(described).fetchSemanticsNodes().isEmpty())
        }
    }
}
