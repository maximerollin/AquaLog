package com.maximerollin.aqualog.a11y

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.maximerollin.aqualog.FeedbackDialog
import com.maximerollin.aqualog.FeedbackUiState
import com.maximerollin.aqualog.shared.FeedbackKind
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FeedbackDialogSemanticsTest {
    private fun withDialog(state: FeedbackUiState, check: androidx.compose.ui.test.ComposeUiTest.() -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            runComposeUiTest {
                setContent { MaterialTheme { FeedbackDialog(state, {}, {}, {}, {}) } }
                waitForIdle()
                check()
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `the message field announces its label and its typed text once, without a content description`() {
        withDialog(FeedbackUiState(isOpen = true, message = "Hello fish")) {
            val config = onNodeWithTag("feedback-message").fetchSemanticsNode().config
            assertEquals(null, config.getOrNull(SemanticsProperties.ContentDescription), "a description would replace the typed text")
            assertEquals("Hello fish", config.getOrNull(SemanticsProperties.EditableText)?.text)
            assertEquals(listOf("Feedback message"), config[SemanticsProperties.Text].map { it.text }.filter { it == "Feedback message" })
        }
    }

    @Test
    fun `the visual label is not a second node for the screen reader`() {
        withDialog(FeedbackUiState(isOpen = true)) {
            val labelNodes = onAllNodesWithText("Feedback message", useUnmergedTree = false).fetchSemanticsNodes()
            assertEquals(1, labelNodes.size, "the label must be read once")
            assertEquals("feedback-message", labelNodes.single().config.getOrNull(SemanticsProperties.TestTag), "the one node is the field itself")
        }
    }

    @Test
    fun `the selected chip has radio role, selected state and no check glyph`() {
        withDialog(FeedbackUiState(isOpen = true, kind = FeedbackKind.BUG)) {
            val chip = onNode(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true) and hasRole())
            val config = chip.fetchSemanticsNode().config
            assertEquals(androidx.compose.ui.semantics.Role.RadioButton, config[SemanticsProperties.Role])
            assertFalse(config[SemanticsProperties.Text].any { it.text.contains("✓") }, "the glyph is decorative")
            assertTrue(config[SemanticsProperties.Text].any { it.text == "Bug" })
        }
    }

    private fun hasRole() = SemanticsMatcher("has role") { it.config.contains(SemanticsProperties.Role) }
}
