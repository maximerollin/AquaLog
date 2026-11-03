package com.maximerollin.aqualog

import android.app.LocaleManager
import android.app.UiModeManager
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.geometry.Rect
import androidx.test.platform.app.InstrumentationRegistry
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
