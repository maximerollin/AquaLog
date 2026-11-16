package com.maximerollin.aqualog

import android.app.LocaleManager
import android.app.UiModeManager
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.ui.theme.FloatingNavBarClearance
import org.junit.Assert.assertTrue

private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
private val targetContext get() = instrumentation.targetContext

internal fun assertMinimumTouchTarget(bounds: Rect) {
    val density = targetContext.resources.displayMetrics.density
    assertTrue("touch width must be >= 48dp, was ${bounds.width / density}", bounds.width / density >= 47.9f)
    assertTrue("touch height must be >= 48dp, was ${bounds.height / density}", bounds.height / density >= 47.9f)
}

/** App language, plus dark theme and font scale 1.5 when [darkLarge]; undo with [restoreDisplay]. */
internal fun configure(language: String, darkLarge: Boolean) {
    targetContext.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language)
    targetContext.getSystemService(UiModeManager::class.java).setApplicationNightMode(
        if (darkLarge) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO,
    )
    setFontScale(if (darkLarge) 1.5f else 1f)
}

internal fun restoreDisplay() {
    targetContext.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.getEmptyLocaleList()
    targetContext.getSystemService(UiModeManager::class.java).setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO)
    setFontScale(1f)
}

// Wait for the command and the resulting configuration, so a stale scale never lands mid-test.
internal fun setFontScale(scale: Float) {
    ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand("settings put system font_scale $scale"),
    ).use { it.readBytes() }
    val deadline = SystemClock.uptimeMillis() + 5_000
    while (targetContext.applicationContext.resources.configuration.fontScale != scale) {
        check(SystemClock.uptimeMillis() < deadline) { "Font scale $scale was not applied" }
        SystemClock.sleep(50)
    }
}

/** Icon-only controls (floating bar, Measure) expose their name as a content description. */
internal fun hasLabel(label: String): SemanticsMatcher = hasText(label) or hasContentDescription(label)

internal fun ComposeTestRule.awaitLabel(label: String) =
    waitUntil(timeoutMillis = 10_000) { onAllNodes(hasLabel(label)).fetchSemanticsNodes().isNotEmpty() }

/** Scrolls the screen's one scrollable to [text], once it is laid out: a screen just navigated to can lag on CI. */
internal fun ComposeTestRule.scrollToText(text: String) {
    waitUntil(timeoutMillis = 10_000) { onAllNodes(hasScrollAction()).fetchSemanticsNodes().size == 1 }
    onNode(hasScrollAction()).performScrollToNode(hasText(text))
}

/** The home's horizontal row of value tiles: the only horizontal scrollable inside `home-list`. */
internal fun homeValueRow(): SemanticsMatcher =
    SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) and hasAnyAncestor(hasTestTag("home-list"))

/**
 * Scrolls the home to [matcher], then on until it clears the floating bar. `performScrollToNode` stops as soon as the
 * node is inside `home-list`, which reaches under the bar, so a tap there would land on a tab instead.
 */
internal fun ComposeTestRule.scrollHomeAboveBar(matcher: SemanticsMatcher) {
    val list = onNodeWithTag("home-list")
    list.performScrollToNode(matcher)
    waitForIdle()
    val listBottom = list.fetchSemanticsNode().boundsInRoot.bottom
    val targetBottom = onNode(matcher).fetchSemanticsNode().boundsInRoot.bottom
    val clearance = FloatingNavBarClearance * targetContext.resources.displayMetrics.density
    val hidden = targetBottom - (listBottom - clearance)
    if (hidden > 0f) list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, hidden) }
    waitForIdle()
}
