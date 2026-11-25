package com.maximerollin.aqualog.a11y

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.habitants.AddInhabitantActions
import com.maximerollin.aqualog.habitants.AddInhabitantContent
import com.maximerollin.aqualog.habitants.AddInhabitantUiState
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.LatestReading
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.encyclopedia.decodeSpecies
import com.maximerollin.aqualog.habitants.CHECK_LABEL_TAG
import com.maximerollin.aqualog.habitants.CHECK_VALUE_TAG
import com.maximerollin.aqualog.habitants.FICHE_DRAWING_TAG
import com.maximerollin.aqualog.ui.theme.AquaLogTheme
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The species fiche (lot 4b, Task 4): the drawing on the water, the verdict card, the fact tiles and the sticky add bar.
 * Néon bleu (pH 6.0 to 7.5, groups of 10) in Salon, 120 L: at pH 7.6 it is a precaution (pH a little high), at pH 8.0 a problem.
 */
@OptIn(ExperimentalTestApi::class)
class SpeciesFicheSemanticsTest {
    private val encyclopedia = decodeSpecies(File("src/commonMain/composeResources/files/encyclopedia/species.json").readText())
    private val neon = encyclopedia.single { it.id == "neon" }

    private val salon = Aquarium("a", "Salon", 120.0, VolumeUnit.LITERS, 0L, AquariumProfile.ESTABLISHED)
    private val ph = ParameterDefinition("ph", salon.id, BuiltInParameter.PH, true, 0, "pH", 1, 6.5, 7.5)
    private val setup = AquariumSetup(salon, listOf(ph))
    private val fiche = AddInhabitantUiState(salon.id, species = listOf(neon), ficheSpeciesId = neon.id, count = 10)

    /** The longest German name of the encyclopedia: three lines of title at 150 %. */
    private val ramirezi = encyclopedia.single { it.id == "ramirezi" }

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try { block() } finally { Locale.setDefault(previous) }
    }

    private fun french(block: () -> Unit) = withLocale(Locale.FRENCH, block)

    private fun ComposeUiTest.screen(
        actions: AddInhabitantActions = AddInhabitantActions(),
        setup: AquariumSetup = this@SpeciesFicheSemanticsTest.setup,
        phValue: Double = 7.6,
        language: String = "fr",
        fontScale: Float = 1f,
        add: AddInhabitantUiState = fiche,
    ) {
        setContent {
            AquaLogTheme(false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Box(Modifier.size(360.dp, 760.dp)) {
                        AddInhabitantContent(
                            add, setup, listOf(LatestReading(ph.id, phValue, 0L)), emptyList(), emptyList(), encyclopedia, language, actions,
                        )
                    }
                }
            }
        }
        waitForIdle()
    }

    private val inBar = hasAnyAncestor(hasTestTag("fiche-bottom-bar"))

    private fun ComposeUiTest.scrolledTo(matcher: SemanticsMatcher) = onNodeWithTag("species-fiche-list").performScrollToNode(matcher)

    private fun ComposeUiTest.scrolledToEnd() {
        val list = onNodeWithTag("species-fiche-list")
        val last = list.fetchSemanticsNode().config[SemanticsProperties.CollectionInfo].rowCount - 1
        list.performScrollToIndex(last)
    }

    @Test
    fun `title is a heading on the water with back`() = french {
        runComposeUiTest {
            var back = 0
            screen(actions = AddInhabitantActions(onBack = { back++ }))
            onNode(isHeading() and hasText("Néon bleu")).assertIsDisplayed()
            onNode(hasText("Paracheirodon innesi")).assertIsDisplayed()
            onNodeWithContentDescription("Retour").performClick()
            assertEquals(1, back)
        }
    }

    @Test
    fun `verdict card says its fit in words`() = french {
        runComposeUiTest {
            screen()
            val card = onNode(hasText("Avec précautions pour Salon")).fetchSemanticsNode()
            val texts = card.config[SemanticsProperties.Text].map { it.text }
            assertEquals(listOf("Avec précautions pour Salon", "pH trop haut."), texts)
            onAllNodes(hasText("⚠", substring = true), useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @Test
    fun `check row keeps its spoken summary`() = french {
        runComposeUiTest {
            screen()
            val row = hasContentDescription("pH", substring = true) and hasContentDescription("7,6", substring = true)
            scrolledTo(row)
            onNode(row).assertIsDisplayed()
        }
    }

    /** The label and the value of the check row that speaks [value] (the Aquarium's pH, 7,6). */
    private fun ComposeUiTest.checkParts(value: String): Pair<DpRect, DpRect> {
        val row = hasContentDescription(value, substring = true)
        scrolledTo(row)
        val label = onNode(hasTestTag(CHECK_LABEL_TAG) and hasAnyAncestor(row), useUnmergedTree = true).getBoundsInRoot()
        val shown = onNode(hasTestTag(CHECK_VALUE_TAG) and hasAnyAncestor(row), useUnmergedTree = true).getBoundsInRoot()
        return label to shown
    }

    @Test
    fun `check row value goes under its label at large fonts`() = withLocale(Locale.GERMAN) {
        runComposeUiTest {
            screen(language = "de", fontScale = 1.5f)
            val (label, value) = checkParts("7,6")
            assertTrue(value.top >= label.bottom, "value $value under label $label")
            assertTrue(value.left == label.left, "value $value starts with label $label")
        }
    }

    @Test
    fun `check row value sits beside its label at normal size`() = french {
        runComposeUiTest {
            screen()
            val (label, value) = checkParts("7,6")
            assertTrue(value.left >= label.right, "value $value beside label $label")
        }
    }

    @Test
    fun `add bar stays on screen while the list scrolls`() = french {
        runComposeUiTest {
            var confirmed = 0
            screen(actions = AddInhabitantActions(onConfirm = { confirmed++ }))
            scrolledToEnd()
            onNodeWithTag("fiche-bottom-bar").assertIsDisplayed()
            onNode(hasText("Ajouter 10 Néon bleu") and inBar).assertIsDisplayed().assertIsEnabled().performClick()
            assertEquals(1, confirmed)
        }
    }

    @Test
    fun `report link scrolls clear of the bar`() = french {
        runComposeUiTest {
            screen()
            val report = hasText("Signaler une erreur")
            scrolledTo(report)
            scrolledToEnd()
            val link = onNode(report).getBoundsInRoot()
            val bar = onNodeWithTag("fiche-bottom-bar").getBoundsInRoot()
            assertTrue(link.bottom <= bar.top, "link $link above bar $bar")
        }
    }

    @Test
    fun `bad fit says add anyway`() = french {
        runComposeUiTest {
            screen(phValue = 8.0)
            onNode(hasText("Ajouter quand même") and inBar).assertIsDisplayed()
        }
    }

    @Test
    fun `bar button wraps at large fonts`() = withLocale(Locale.GERMAN) {
        runComposeUiTest {
            screen(language = "de", fontScale = 1.5f)
            val text = onNode(hasText("10 Neonsalmler hinzufügen") and inBar, useUnmergedTree = true)
            text.assertIsDisplayed()
            val bounds = text.getBoundsInRoot()
            val bar = onNodeWithTag("fiche-bottom-bar").getBoundsInRoot()
            assertTrue(bounds.left >= bar.left && bounds.right <= bar.right, "text $bounds in bar $bar")
            assertTrue(bounds.top >= bar.top && bounds.bottom <= bar.bottom, "text $bounds in bar $bar")
            val layouts = mutableListOf<TextLayoutResult>()
            text.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val layout = layouts.single()
            assertFalse(layout.didOverflowHeight, "the button text loses lines")
            assertTrue((0 until layout.lineCount).none(layout::isLineEllipsized), "the button text is ellipsized")
        }
    }

    @Test
    fun `fact tiles stack at large fonts`() = french {
        runComposeUiTest {
            screen(fontScale = 1.5f)
            val size = hasText("Taille adulte", substring = true)
            val lifespan = hasText("Durée de vie", substring = true)
            scrolledTo(lifespan)
            val first = onNode(size, useUnmergedTree = true).getBoundsInRoot()
            val second = onNode(lifespan, useUnmergedTree = true).getBoundsInRoot()
            assertTrue(second.top >= first.bottom, "lifespan $second under size $first")
        }
    }

    @Test
    fun `fact tiles share a row at normal size`() = french {
        runComposeUiTest {
            screen()
            val size = hasText("Taille adulte", substring = true)
            scrolledTo(size)
            val first = onNode(size, useUnmergedTree = true).getBoundsInRoot()
            val second = onNode(hasText("Durée de vie", substring = true), useUnmergedTree = true).getBoundsInRoot()
            val third = onNode(hasText("Groupe minimum", substring = true), useUnmergedTree = true).getBoundsInRoot()
            assertTrue(second.left >= first.right && third.left >= second.right, "one row: $first $second $third")
        }
    }

    @Test
    fun `cycling banner shows on a fish fiche while cycling`() = french {
        runComposeUiTest {
            screen(setup = AquariumSetup(salon.copy(profile = AquariumProfile.CYCLING), listOf(ph)))
            val banner = hasText("Attendez que les nitrites soient à 0 avant d’ajouter des poissons.")
            scrolledTo(banner)
            onNode(banner).assertIsDisplayed()
        }
    }

    @Test
    fun `drawing keeps its floor under a long name at large fonts`() = withLocale(Locale.GERMAN) {
        runComposeUiTest {
            screen(language = "de", fontScale = 1.5f, add = fiche.copy(species = listOf(ramirezi), ficheSpeciesId = ramirezi.id))
            onNode(isHeading() and hasText("Südamerikanischer Schmetterlingsbuntbarsch")).assertExists()
            onNodeWithTag(FICHE_DRAWING_TAG).assertHeightIsAtLeast(110.dp)
        }
    }

    @Test
    fun `drawing keeps its floor at normal size`() = french {
        runComposeUiTest {
            screen()
            onNodeWithTag(FICHE_DRAWING_TAG).assertHeightIsAtLeast(110.dp)
        }
    }

    @Test
    fun `details on a good fit reveal the compared rows`() = french {
        runComposeUiTest {
            screen(phValue = 7.0)
            onNode(hasText("Va bien dans Salon")).assertExists()
            val row = hasContentDescription("pH", substring = true)
            onAllNodes(row).assertCountEquals(0)
            val details = hasText("Détails ›")
            scrolledTo(details)
            onNode(details).assertHeightIsAtLeast(48.dp).performClick()
            scrolledTo(row)
            onNode(row).assertIsDisplayed()
        }
    }

    @Test
    fun `measure opens a session`() = french {
        runComposeUiTest {
            var measured = 0
            screen(actions = AddInhabitantActions(onMeasure = { measured++ }))
            val measure = hasText("Faire une analyse")
            scrolledTo(measure)
            onNode(measure).performClick()
            assertEquals(1, measured)
        }
    }

    @Test
    fun `report link sends the species id and its name`() = french {
        runComposeUiTest {
            var reported: Pair<String, String>? = null
            screen(actions = AddInhabitantActions(onReportSpecies = { id, name -> reported = id to name }))
            val report = hasText("Signaler une erreur")
            scrolledTo(report)
            scrolledToEnd()
            onNode(report).performClick()
            assertEquals("neon" to "Néon bleu", reported)
        }
    }
}
