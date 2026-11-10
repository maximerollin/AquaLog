package com.maximerollin.aqualog.a11y

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.maximerollin.aqualog.home.WeeklySummaryCard
import com.maximerollin.aqualog.shared.assistance.WeeklySummary
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class WeeklySummarySemanticsTest {
    @Test
    fun `the weekly summary title is a heading`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            runComposeUiTest {
                setContent { MaterialTheme { WeeklySummaryCard(WeeklySummary(2, 3, 0, emptyList(), emptyList()), isPro = false, onOpenPro = {}) } }
                val config = onNodeWithText("Weekly summary").fetchSemanticsNode().config
                assertNotNull(config.getOrNull(SemanticsProperties.Heading))
            }
        } finally {
            Locale.setDefault(previous)
        }
    }
}
