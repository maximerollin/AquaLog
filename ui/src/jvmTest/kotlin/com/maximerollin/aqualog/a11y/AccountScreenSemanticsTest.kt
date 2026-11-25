package com.maximerollin.aqualog.a11y

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.ACCOUNT_CLOUD_TAG
import com.maximerollin.aqualog.AccountActions
import com.maximerollin.aqualog.AccountContent
import com.maximerollin.aqualog.AccountDeletionCleanupContent
import com.maximerollin.aqualog.AccountError
import com.maximerollin.aqualog.AccountSafeMessage
import com.maximerollin.aqualog.AccountSettingsUiState
import com.maximerollin.aqualog.AccountStep
import com.maximerollin.aqualog.AccountUiState
import com.maximerollin.aqualog.shared.AccountDeletionPhase
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.PendingAccountDeletion
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.ui.theme.AquaLogTheme
import com.maximerollin.aqualog.ui.theme.FULL_SCREEN_BACKING_TAG
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The account steps and the account-deletion cleanup, full frame on the Aquarium's water (lot 4a, Task 6). */
@OptIn(ExperimentalTestApi::class)
class AccountScreenSemanticsTest {
    private val salon = Aquarium("a", "Salon", 120.0, VolumeUnit.LITERS, 0L)
    private val calls = mutableListOf<String>()
    private val actions = AccountActions(
        onOpenMethods = { calls += "methods" },
        onDismiss = { calls += "dismiss" },
        onSignInWithGoogle = { calls += "google" },
        onOpenMagicEmail = { calls += "email" },
        onEmailChange = { calls += "email:$it" },
        onRequestMagicLink = { calls += "send" },
        onRetryMigration = { calls += "retry" },
    )

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try { block() } finally { Locale.setDefault(previous) }
    }

    private fun french(block: () -> Unit) = withLocale(Locale.FRENCH, block)

    private val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun ComposeUiTest.account(
        state: AccountUiState,
        aquarium: Aquarium? = salon,
        fontScale: Float = 1f,
    ) {
        setContent {
            AquaLogTheme(false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Box(Modifier.size(360.dp, 760.dp).testTag("frame")) {
                        AccountContent(state, aquarium, actions)
                    }
                }
            }
        }
        waitForIdle()
    }

    /** A full-width action of at least 48 dp, inside the backed column. */
    private fun ComposeUiTest.assertFullWidthAction(label: String) {
        val node = onNode(hasText(label) and hasClickAction())
        node.performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val backing = onNodeWithTag(FULL_SCREEN_BACKING_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width >= backing.width - 2 * 24 * density.density - 1f, "« $label » $bounds in $backing")
        assertTrue(bounds.top >= backing.top, "« $label » $bounds in $backing")
    }

    @Test
    fun `invitation keeps its two actions`() = french {
        runComposeUiTest {
            account(AccountUiState(AccountStep.INVITATION))
            onNode(hasText("Mettez à l’abri votre aquarium local") and isHeading()).assertIsDisplayed()
            onNode(hasText("Votre première session est enregistrée localement.", substring = true)).assertIsDisplayed()
            assertFullWidthAction("Créer un compte")
            assertFullWidthAction("Pas maintenant")
            onNode(hasText("Créer un compte") and button).performClick()
            onNode(hasText("Pas maintenant") and button).performClick()
            assertEquals(listOf("methods", "dismiss"), calls)
        }
    }

    @Test
    fun `methods keep google, email and cancel`() = french {
        runComposeUiTest {
            account(AccountUiState(AccountStep.METHODS))
            onNode(hasText("Créez votre compte") and isHeading()).assertIsDisplayed()
            listOf("Continuer avec Google", "Continuer avec l’e-mail", "Annuler").forEach {
                assertFullWidthAction(it)
                onNode(hasText(it) and button).performClick()
            }
            assertEquals(listOf("google", "email", "dismiss"), calls)
        }
    }

    @Test
    fun `magic email field is named and typed`() = french {
        runComposeUiTest {
            // The field is controlled: like the ViewModel, the test feeds the typed address back.
            setContent {
                AquaLogTheme(false) {
                    var email by remember { mutableStateOf("") }
                    Box(Modifier.size(360.dp, 760.dp)) {
                        AccountContent(
                            AccountUiState(AccountStep.MAGIC_EMAIL, email = email),
                            salon,
                            AccountActions(
                                onEmailChange = { email = it; calls += "email:$it" },
                                onRequestMagicLink = { calls += "send" },
                                onOpenMethods = { calls += "methods" },
                            ),
                        )
                    }
                }
            }
            onNode(hasText("Lien de connexion par e-mail") and isHeading()).assertIsDisplayed()
            onNode(hasSetTextAction() and hasText("Adresse e-mail")).performTextInput("nemo@example.com")
            onNode(hasText("Envoyer le lien de connexion") and button).performClick()
            onNode(hasText("Retour") and button).performScrollTo().performClick()
            assertEquals(listOf("email:nemo@example.com", "send", "methods"), calls)
        }
    }

    @Test
    fun `sent and waiting steps go back to the Aquarium`() = french {
        for (step in listOf(AccountStep.MAGIC_SENT, AccountStep.WAITING_BROWSER)) {
            calls.clear()
            runComposeUiTest {
                account(AccountUiState(step))
                assertFullWidthAction("Retour à l’aquarium")
                onNode(hasText("Retour à l’aquarium") and button).performClick()
                assertEquals(listOf("dismiss"), calls, "$step")
            }
        }
    }

    @Test
    fun `working shows a spinner on the water`() = french {
        runComposeUiTest {
            account(AccountUiState(AccountStep.WORKING))
            onNodeWithTag(FULL_SCREEN_BACKING_TAG).assertExists()
            onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
                .assertIsDisplayed()
        }
    }

    @Test
    fun `error keeps its message and retries the migration only after a migration failure`() = french {
        runComposeUiTest {
            account(AccountUiState(AccountStep.ERROR, error = AccountError.EXPIRED_LINK))
            onNode(hasText("Vos données locales sont en sécurité") and isHeading()).assertIsDisplayed()
            onNode(hasText("Ce lien a expiré. Demandez un nouveau lien de connexion et réessayez.")).assertIsDisplayed()
            onNode(hasText("Réessayer") and button).performClick()
            onNode(hasText("Retour à l’aquarium") and button).performScrollTo().performClick()
            assertEquals(listOf("methods", "dismiss"), calls)
        }
        calls.clear()
        runComposeUiTest {
            account(AccountUiState(AccountStep.ERROR, error = AccountError.MIGRATION))
            onNode(hasText("Réessayer") and button).performClick()
            assertEquals(listOf("retry"), calls)
        }
    }

    @Test
    fun `the cloud is decorative and the text sits on the backing`() = french {
        runComposeUiTest {
            account(AccountUiState(AccountStep.INVITATION))
            val cloud = onNode(hasTestTag(ACCOUNT_CLOUD_TAG), useUnmergedTree = true).fetchSemanticsNode()
            assertFalse(SemanticsProperties.ContentDescription in cloud.config, "the cloud is not read")
            assertTrue(cloud.children.none { SemanticsProperties.ContentDescription in it.config }, "the cloud is not read")
            val backing = onNodeWithTag(FULL_SCREEN_BACKING_TAG).fetchSemanticsNode().boundsInRoot
            val title = onNode(hasText("Mettez à l’abri votre aquarium local")).fetchSemanticsNode().boundsInRoot
            assertTrue(title.top >= backing.top + 48 * density.density - 1f, "title $title below the fade of $backing")
            assertTrue(cloud.boundsInRoot.top >= backing.top, "cloud ${cloud.boundsInRoot} on $backing")
        }
    }

    @Test
    fun `no aquarium still draws the frame`() = french {
        runComposeUiTest {
            account(AccountUiState(AccountStep.INVITATION), aquarium = null)
            onNodeWithTag(FULL_SCREEN_BACKING_TAG).assertExists()
            onNode(hasText("Mettez à l’abri votre aquarium local") and isHeading()).assertIsDisplayed()
        }
    }

    @Test
    fun `content scrolls at 150 percent in German`() = withLocale(Locale.GERMAN) {
        runComposeUiTest {
            account(AccountUiState(AccountStep.METHODS), fontScale = 1.5f)
            onNode(hasText("Abbrechen") and button).performScrollTo().assertIsDisplayed()
            onNode(hasText("Erstellen Sie Ihr Konto") and isHeading()).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `cleanup keeps its detail and its one action`() = french {
        var finished = 0
        val state = AccountSettingsUiState(
            pendingDeletion = PendingAccountDeletion("op", AccountDeletionPhase.CLOUD_DELETED),
            safeMessage = AccountSafeMessage.DELETION_RESUME_FAILED,
        )
        runComposeUiTest {
            setContent {
                AquaLogTheme(false) {
                    Box(Modifier.size(360.dp, 760.dp)) {
                        AccountDeletionCleanupContent(state, salon, onFinish = { finished++ })
                    }
                }
            }
            onNodeWithTag(FULL_SCREEN_BACKING_TAG).assertExists()
            onNode(hasText("Accès au compte supprimé") and isHeading()).assertIsDisplayed()
            onNode(hasText("La suppression dans le cloud est confirmée", substring = true)).assertIsDisplayed()
            onNode(hasText("La suppression du compte n’a pas pu reprendre", substring = true)).assertIsDisplayed()
            assertFullWidthAction("Terminer le nettoyage local")
            onNode(hasText("Terminer le nettoyage local") and button).performClick()
            assertEquals(1, finished)
        }
        runComposeUiTest {
            setContent {
                AquaLogTheme(false) {
                    AccountDeletionCleanupContent(state.copy(isWorking = true), null, onFinish = { finished++ })
                }
            }
            onNode(hasText("Terminer le nettoyage local") and button).assertIsNotEnabled()
        }
    }
}
