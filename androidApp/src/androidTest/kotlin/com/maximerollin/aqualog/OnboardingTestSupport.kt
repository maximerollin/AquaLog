package com.maximerollin.aqualog

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick

/**
 * Clicks the clickable node labelled [text] once it is on screen. Each onboarding step moves through the ViewModel, so the
 * next step's button can appear a frame or two after the previous click: Compose idling does not wait for that, and an
 * immediate lookup failed now and then in CI (« could not find any node … 'Try a mini Session' »).
 */
internal fun ComposeTestRule.clickWhenShown(text: String, timeoutMillis: Long = 10_000) {
    val button = hasText(text) and hasClickAction()
    waitUntil(timeoutMillis) { onAllNodes(button).fetchSemanticsNodes().isNotEmpty() }
    onNode(button).performClick()
}
