package com.maximerollin.aqualog.a11y

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.AccountSettingsUiState
import com.maximerollin.aqualog.AquariumEditorDialog
import com.maximerollin.aqualog.AquariumEditorUiState
import com.maximerollin.aqualog.HomeUiState
import com.maximerollin.aqualog.MoreActions
import com.maximerollin.aqualog.MoreContent
import com.maximerollin.aqualog.PortableDataMessage
import com.maximerollin.aqualog.PortableDataUiState
import com.maximerollin.aqualog.ProPurchaseStatus
import com.maximerollin.aqualog.ProUiState
import com.maximerollin.aqualog.TaskUiState
import com.maximerollin.aqualog.shared.AccountDeletionPhase
import com.maximerollin.aqualog.shared.AccountIdentity
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.Inhabitant
import com.maximerollin.aqualog.shared.PendingAccountDeletion
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.TaskOccurrence
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.encyclopedia.DrawingFamily
import com.maximerollin.aqualog.ui.theme.AquaLogTheme
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** More from a fake state: Free and Pro, switching Aquarium, and the account blocks that move with it (lot 4a, Task 2). */
@OptIn(ExperimentalTestApi::class)
class MoreSemanticsTest {
    private val salon = Aquarium("a", "Salon", 120.0, VolumeUnit.LITERS, 0L, AquariumProfile.PLANTED_SHRIMP)
    private val bureau = Aquarium("b", "Bureau", 60.0, VolumeUnit.LITERS, 0L)
    private val free = HomeUiState(
        isLoading = false,
        setup = AquariumSetup(salon, emptyList()),
        aquariums = listOf(salon, bureau),
        inhabitants = listOf(Inhabitant("i", salon.id, null, "Néon", DrawingFamily.TETRA, 16, 0L)),
        tasks = TaskUiState(
            pendingOccurrences = listOf(
                occurrence("o1", salon.id), occurrence("o2", salon.id), occurrence("o3", bureau.id),
            ),
        ),
    )
    private val pro = free.copy(pro = ProUiState(purchaseStatus = ProPurchaseStatus.ACTIVE))

    private fun occurrence(id: String, aquariumId: String) =
        TaskOccurrence(id, "t-$id", aquariumId, "Changement d’eau", TaskDate(2026, 11, 20), null, "UTC", null, null)

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try { block() } finally { Locale.setDefault(previous) }
    }

    private fun french(block: () -> Unit) = withLocale(Locale.FRENCH, block)

    private fun ComposeUiTest.more(state: HomeUiState, actions: MoreActions = MoreActions(), fontScale: Float = 1f) {
        setContent {
            AquaLogTheme(false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Box(Modifier.size(360.dp, 760.dp).testTag("frame")) { MoreContent(state, actions) }
                }
            }
        }
        waitForIdle()
    }

    /** The node with [text], scrolled into the list first: rows below the fold are not composed. */
    private fun ComposeUiTest.row(text: String): SemanticsNodeInteraction {
        onNodeWithTag("settings-list").performScrollToNode(hasText(text))
        return onNode(hasText(text))
    }

    @Test
    fun `free shows the Pro banner and opens the offer`() = french {
        runComposeUiTest {
            var opened = 0
            more(free, MoreActions(onOpenPaywall = { opened++ }))
            val banner = row("Découvrir Pro")
            banner.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            banner.performClick()
            assertEquals(1, opened)
            assertEquals(0, onAllNodesWithText("Pro actif").fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `pro shows Pro actif instead of the banner`() = french {
        runComposeUiTest {
            var opened = 0
            more(pro, MoreActions(onOpenPaywall = { opened++ }))
            row("Pro actif").performClick()
            assertEquals(1, opened)
            assertEquals(0, onAllNodesWithText("Découvrir Pro").fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `archive is disabled in free with its reason`() = french {
        runComposeUiTest {
            more(free)
            row("Archiver l’aquarium").assertIsNotEnabled()
            row(
                "La version gratuite conserve toutes les données existantes et autorise un seul aquarium. Pro débloque des aquariums supplémentaires.",
            ).assertExists()
        }
    }

    @Test
    fun `archive is enabled for pro with two aquariums`() = french {
        runComposeUiTest {
            var archived = 0
            more(pro, MoreActions(onArchive = { archived++ }))
            row("Archiver l’aquarium").assertIsEnabled().performClick()
            assertEquals(1, archived)
        }
    }

    @Test
    fun `change aquarium chip opens the picker`() = french {
        runComposeUiTest {
            val selected = mutableListOf<String>()
            more(free, MoreActions(onSelectAquarium = { selected += it }))
            onNode(hasText("Changer d’aquarium")).performClick()
            waitForIdle()
            val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
            onNode(hasText("Salon") and isSelectable() and radio).assertIsSelected()
            onNode(hasText("Bureau") and isSelectable() and radio).assertIsNotSelected().performClick()
            waitForIdle()
            assertEquals(listOf("b"), selected)
        }
    }

    @Test
    fun `add chip and pencil reach the aquarium editor`() = french {
        runComposeUiTest {
            var added = 0
            var edited = 0
            more(free, MoreActions(onAddAquarium = { added++ }, onEditAquarium = { edited++ }))
            onNode(hasText("Ajouter un aquarium")).performClick()
            onNode(hasContentDescription("Modifier l’aquarium")).performClick()
            assertEquals(1, added)
            assertEquals(1, edited)
        }
    }

    @Test
    fun `the header shows the aquarium and its context line`() = french {
        runComposeUiTest {
            more(free)
            onNode(hasText("Salon") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertIsDisplayed()
            onNode(hasText("120 L · Planté / crevettes · 16 habitants")).assertIsDisplayed()
        }
    }

    @Test
    fun `the aquarium group counts inhabitants and this aquarium's tasks`() = french {
        runComposeUiTest {
            var tasks = 0
            more(free, MoreActions(onOpenTasks = { tasks++ }))
            row("Habitants").assert(hasText("16"))
            row("Tâches et rappels").assert(hasText("2")).performClick()
            assertEquals(1, tasks)
        }
    }

    @Test
    fun `pending deletion shows its retry rows`() = french {
        runComposeUiTest {
            var retried = 0
            more(
                free.copy(
                    accountSettings = AccountSettingsUiState(
                        identity = AccountIdentity("u", "marie@example.org"),
                        pendingDeletion = PendingAccountDeletion("k", AccountDeletionPhase.CLOUD_PENDING),
                    ),
                ),
                MoreActions(onRetryAccountDeletion = { retried++ }),
            )
            row("Suppression du compte incomplète").assertExists()
            row("Réessayer la suppression du compte").performClick()
            assertEquals(1, retried)
            // A pending deletion takes the sign-out and delete rows away: « Actions du compte » shows the local-only line instead.
            row(
                "Aucun compte cloud n’est lié. Aquagenda reste entièrement utilisable avec les données stockées sur cet appareil.",
            ).assertExists()
            assertEquals(0, onAllNodesWithText("Se déconnecter").fetchSemanticsNodes().size)
            assertEquals(0, onAllNodesWithText("Supprimer le compte").fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `pending deletion that needs a sign in offers google and the magic link`() = french {
        runComposeUiTest {
            more(
                free.copy(
                    accountSettings = AccountSettingsUiState(
                        identity = AccountIdentity("u", "marie@example.org"),
                        pendingDeletion = PendingAccountDeletion("k", AccountDeletionPhase.CLOUD_PENDING),
                        requiresDeletionReauthentication = true,
                    ),
                ),
            )
            row("Continuer avec Google").assertExists()
            assertEquals(0, onAllNodesWithText("Réessayer la suppression du compte").fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `signed in shows sign out and delete`() = french {
        runComposeUiTest {
            more(free.copy(accountSettings = AccountSettingsUiState(identity = AccountIdentity("u", "marie@example.org"))))
            row("marie@example.org").assertExists()
            row("Se déconnecter").assertIsEnabled()
            row("Supprimer le compte").assertIsEnabled()
        }
    }

    @Test
    fun `signed out shows the local only line, no sign out`() = french {
        runComposeUiTest {
            more(free)
            row("Local uniquement").assertExists()
            row(
                "Aucun compte cloud n’est lié. Aquagenda reste entièrement utilisable avec les données stockées sur cet appareil.",
            ).assertExists()
            assertEquals(0, onAllNodesWithText("Se déconnecter").fetchSemanticsNodes().size)
            assertEquals(0, onAllNodesWithText("Supprimer le compte").fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `working shows the spinner, the error and disables export`() = french {
        runComposeUiTest {
            more(
                free.copy(
                    portableData = PortableDataUiState(isWorking = true, message = PortableDataMessage.STORAGE_ERROR),
                    accountSettings = AccountSettingsUiState(safeDetail = "Réseau indisponible"),
                ),
            )
            row("Android n’a pas pu lire ou écrire ce document. Essayez un autre emplacement.").assertExists()
            row("Exporter les données").assertIsNotEnabled()
            row("Restaurer depuis un export").assertIsNotEnabled()
            onNodeWithTag("settings-list").performScrollToNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            row("Réseau indisponible").assertExists()
        }
    }

    @Test
    fun `the aquarium editor has filled fields and a unit segment`() = french {
        runComposeUiTest {
            val units = mutableListOf<VolumeUnit>()
            setContent {
                AquaLogTheme(false) {
                    AquariumEditorDialog(
                        AquariumEditorUiState(aquariumId = "a", name = "Salon", volume = "120", volumeUnit = VolumeUnit.US_GALLONS),
                        onNameChange = {}, onVolumeChange = {}, onUnitChange = { units += it }, onSave = {}, onDismiss = {},
                    )
                }
            }
            waitForIdle()
            // Each field is named by its label, carried as text on the editable node with its value.
            onNode(hasText("Nom de l’aquarium") and hasText("Salon") and hasSetTextAction()).assertExists()
            onNode(hasText("Volume") and hasText("120") and hasSetTextAction()).assertExists()
            val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
            onNode(hasText("Gallons américains") and radio).assertIsSelected()
            onNode(hasText("Litres") and radio).assertIsNotSelected().performClick()
            assertEquals(listOf(VolumeUnit.LITERS), units)
        }
    }

    @Test
    fun `german at 150 percent keeps the header chips whole`() = withLocale(Locale.GERMAN) {
        runComposeUiTest {
            more(free, fontScale = 1.5f)
            val frame = onNodeWithTag("frame").fetchSemanticsNode().boundsInRoot
            for (label in listOf("Aquarium wechseln", "Aquarium hinzufügen")) {
                val chip = onNode(hasText(label)).assertIsDisplayed().fetchSemanticsNode()
                assertTrue(chip.boundsInRoot.left >= frame.left && chip.boundsInRoot.right <= frame.right, "$label cut: ${chip.boundsInRoot}")
                val text = onNode(hasText(label), useUnmergedTree = true).fetchSemanticsNode()
                val layouts = mutableListOf<TextLayoutResult>()
                text.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                assertEquals(1, layouts.single().lineCount, "$label wraps inside its chip")
            }
        }
    }
}
