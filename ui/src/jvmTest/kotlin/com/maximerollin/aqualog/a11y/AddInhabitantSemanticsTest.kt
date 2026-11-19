package com.maximerollin.aqualog.a11y

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.habitants.AddInhabitantActions
import com.maximerollin.aqualog.habitants.AddInhabitantContent
import com.maximerollin.aqualog.habitants.AddInhabitantUiState
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.Inhabitant
import com.maximerollin.aqualog.shared.LatestReading
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.encyclopedia.DrawingFamily
import com.maximerollin.aqualog.shared.encyclopedia.PresentGroup
import com.maximerollin.aqualog.shared.encyclopedia.decodeSpecies
import com.maximerollin.aqualog.ui.theme.AquaLogTheme
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * « Ajouter un habitant » under the water (lot 4b, Task 3): the search with its groups, rows and pills, and the custom form.
 * Salon, 120 L, planted / shrimp, pH 7.0, two Ancistrus present: the galaxy rasbora fits, a second Ancistrus is a territorial
 * precaution, the Labidochromis (150 L, pH 7.5 to 8.5) has two problems.
 */
@OptIn(ExperimentalTestApi::class)
class AddInhabitantSemanticsTest {
    private val encyclopedia = decodeSpecies(File("src/commonMain/composeResources/files/encyclopedia/species.json").readText())
    private val galaxy = encyclopedia.single { it.id == "rasbora-galaxy" }
    private val ancistrus = encyclopedia.single { it.id == "ancistrus" }
    private val labidochromis = encyclopedia.single { it.id == "labidochromis-jaune" }

    private val salon = Aquarium("a", "Salon", 120.0, VolumeUnit.LITERS, 0L, AquariumProfile.PLANTED_SHRIMP)
    private val ph = ParameterDefinition("ph", salon.id, BuiltInParameter.PH, true, 0, "pH", 1, 6.5, 7.5)
    private val setup = AquariumSetup(salon, listOf(ph))
    private val readings = listOf(LatestReading(ph.id, 7.0, 0L))
    private val inhabitants = listOf(Inhabitant("i1", salon.id, ancistrus.id, null, DrawingFamily.CATFISH, 2, 0L))
    private val present = listOf(PresentGroup(ancistrus, DrawingFamily.CATFISH, 2))
    private val search = AddInhabitantUiState(salon.id, species = listOf(galaxy, ancistrus, labidochromis))

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try { block() } finally { Locale.setDefault(previous) }
    }

    private fun french(block: () -> Unit) = withLocale(Locale.FRENCH, block)

    private fun ComposeUiTest.screen(
        add: AddInhabitantUiState = search,
        actions: AddInhabitantActions = AddInhabitantActions(),
        setup: AquariumSetup = this@AddInhabitantSemanticsTest.setup,
        language: String = "fr",
        fontScale: Float = 1f,
    ) {
        setContent {
            AquaLogTheme(false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Box(Modifier.size(360.dp, 760.dp)) {
                        AddInhabitantContent(add, setup, readings, inhabitants, present, encyclopedia, language, actions)
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.scrolledTo(matcher: SemanticsMatcher) = onNodeWithTag("add-inhabitant-list").performScrollToNode(matcher)

    @Test
    fun `header names the screen and the aquarium context`() = french {
        runComposeUiTest {
            screen()
            onNode(isHeading() and hasText("Ajouter un habitant")).assertIsDisplayed()
            val context = onNode(hasText("Planté / crevette", substring = true)).fetchSemanticsNode()
                .config[SemanticsProperties.Text].single().text
            assertTrue(context.startsWith("120"), context)
            assertTrue(context.endsWith("2 habitants"), context)
        }
    }

    @Test
    fun `back is a glass button`() = french {
        runComposeUiTest {
            var back = 0
            screen(actions = AddInhabitantActions(onBack = { back++ }))
            onNodeWithContentDescription("Retour").assertHeightIsAtLeast(48.dp).performClick()
            assertEquals(1, back)
        }
    }

    @Test
    fun `groups are labelled and rows open the fiche`() = french {
        runComposeUiTest {
            var opened: String? = null
            screen(actions = AddInhabitantActions(onOpenFiche = { opened = it }))
            listOf("Vont bien dans ce bac", "Avec précautions", "Déconseillés").forEach { group ->
                val heading = isHeading() and hasText(group.uppercase())
                scrolledTo(heading)
                onNode(heading).assertIsDisplayed()
            }
            scrolledTo(hasText("Rasbora galaxy"))
            onNodeWithText("Rasbora galaxy").performClick()
            assertEquals("rasbora-galaxy", opened)
        }
    }

    @Test
    fun `row speaks name scientific name and pill as one button`() = french {
        runComposeUiTest {
            screen()
            val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
            val row = onNode(button and hasText("Rasbora galaxy"))
            row.assertHeightIsAtLeast(62.dp)
            val texts = row.fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }
            assertEquals(listOf("Rasbora galaxy", "Danio margaritatus", "Compatible"), texts)
        }
    }

    @Test
    fun `a precaution names its reason`() = french {
        runComposeUiTest {
            screen()
            scrolledTo(hasText("Ancistrus"))
            onNode(hasText("Ancistrus") and hasText("Territorial")).assertIsDisplayed()
        }
    }

    @Test
    fun `two problems read as a plural pill`() = french {
        runComposeUiTest {
            screen()
            scrolledTo(hasText("Labidochromis jaune"))
            onNode(hasText("Labidochromis jaune") and hasText("2 problèmes")).assertIsDisplayed()
        }
    }

    @Test
    fun `pill stacks under the name at large fonts`() = withLocale(Locale.GERMAN) {
        runComposeUiTest {
            screen(language = "de", fontScale = 1.5f)
            scrolledTo(hasText("Perlhuhnbärbling"))
            val name = onNodeWithText("Perlhuhnbärbling", useUnmergedTree = true).getBoundsInRoot()
            val pill = onNodeWithText("Passt", useUnmergedTree = true).getBoundsInRoot()
            // Under the names, not beside them: below the name and starting within its width.
            assertTrue(pill.top >= name.bottom && pill.left < name.right, "pill $pill under name $name")
        }
    }

    @Test
    fun `missing species row opens the custom form`() = french {
        runComposeUiTest {
            var custom = 0
            screen(actions = AddInhabitantActions(onOpenCustom = { custom++ }))
            val missing = hasText("Espèce absente ? L'ajouter à la main")
            scrolledTo(missing)
            onNode(missing).assertHeightIsAtLeast(48.dp).performClick()
            assertEquals(1, custom)
        }
    }

    @Test
    fun `encyclopedia failure keeps the custom row`() = french {
        runComposeUiTest {
            screen(add = AddInhabitantUiState(salon.id, species = emptyList(), loadFailed = true))
            val unavailable = hasText("La liste des espèces n'a pas pu être chargée. Vous pouvez en ajouter une à la main.")
            scrolledTo(unavailable)
            onNode(unavailable).assertIsDisplayed()
            val missing = hasText("Espèce absente ? L'ajouter à la main")
            scrolledTo(missing)
            onNode(missing).assertIsDisplayed()
        }
    }

    @Test
    fun `custom name is a filled field with its error`() = french {
        runComposeUiTest {
            screen(add = search.copy(custom = true, showCustomError = true))
            onNode(isHeading() and hasText("Ajouter à la main")).assertIsDisplayed()
            onNode(hasSetTextAction() and hasText("Nom de l'espèce")).assertIsDisplayed()
            onNodeWithText("Indiquez un nom et choisissez une famille.").assertIsDisplayed()
        }
    }

    @Test
    fun `cycling banner shows for a fish in a cycling aquarium`() = french {
        runComposeUiTest {
            val cycling = AquariumSetup(salon.copy(profile = AquariumProfile.CYCLING), listOf(ph))
            screen(add = search.copy(custom = true, customFamily = DrawingFamily.TETRA), setup = cycling)
            onNodeWithText("Attendez que les nitrites soient à 0 avant d'ajouter des poissons.").assertIsDisplayed()
        }
    }

    @Test
    fun `search pill is named by its hint and lets go of the keyboard on search`() = french {
        runComposeUiTest {
            screen()
            onNode(hasSetTextAction() and hasText("Rechercher une espèce…")).assertIsDisplayed()
            val field = onNodeWithTag("species-search")
            field.assertHeightIsAtLeast(48.dp).performClick()
            field.assertIsFocused()
            // The keyboard's search key closes it, so the results under it can be seen.
            field.performImeAction()
            field.assertIsNotFocused()
        }
    }
}
