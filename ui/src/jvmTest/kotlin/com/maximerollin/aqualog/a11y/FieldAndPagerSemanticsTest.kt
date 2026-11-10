package com.maximerollin.aqualog.a11y

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.maximerollin.aqualog.habitants.SpeciesSearchField
import com.maximerollin.aqualog.home.PagerDots
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class FieldAndPagerSemanticsTest {
    @Test
    fun `the search field keeps its name and its typed text`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            runComposeUiTest {
                setContent { MaterialTheme { SpeciesSearchField("neon", {}) } }
                val config = onNodeWithTag("species-search").fetchSemanticsNode().config
                assertNull(config.getOrNull(SemanticsProperties.ContentDescription))
                assertEquals("neon", config.getOrNull(SemanticsProperties.EditableText)?.text)
                val texts = config[SemanticsProperties.Text].map { it.text }
                assertEquals(1, texts.count { it.startsWith("Search") }, "the label must appear exactly once: $texts")
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `the pager dots read as one page position`() = runComposeUiTest {
        setContent { MaterialTheme { PagerDots(3, 1) } }
        val node = onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)).fetchSemanticsNode()
        assertEquals("2 / 3", node.config.getOrNull(SemanticsProperties.StateDescription))
    }
}
