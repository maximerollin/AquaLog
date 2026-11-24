package com.maximerollin.aqualog

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction

/**
 * Clicks the clickable node labelled [text] once it is on screen. Each onboarding step moves through the ViewModel, so the
 * next step's button can appear a frame or two after the previous click: Compose idling does not wait for that, and an
 * immediate lookup failed now and then in CI (« could not find any node … 'Try a mini Session' »).
 *
 * The click is the node's own OnClick action, not an injected touch: after typing, the keyboard slides in and the bottom
 * bar rides up with it (imePadding), so a touch aimed at the button's centre could land beside it on a slow emulator and
 * the step never advanced.
 */
internal fun ComposeTestRule.clickWhenShown(text: String, timeoutMillis: Long = 10_000) {
    val button = hasText(text) and hasClickAction()
    waitUntil(timeoutMillis) { onAllNodes(button).fetchSemanticsNodes().isNotEmpty() }
    onNode(button).performSemanticsAction(SemanticsActions.OnClick)
}

/**
 * Saves the open Session through the button's OnClick action. Tests save right after typing, while the keyboard slides
 * in and moves the bottom bar: an injected touch could miss the button, and the Session was never saved.
 */
internal fun ComposeTestRule.saveSession() {
    onNodeWithTag("save-session").performSemanticsAction(SemanticsActions.OnClick)
}
