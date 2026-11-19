package com.maximerollin.aqualog.a11y

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import com.maximerollin.aqualog.photo.PhotoCropScreen
import com.maximerollin.aqualog.photo.SquareCrop
import com.maximerollin.aqualog.photo.centredCrop
import com.maximerollin.aqualog.ui.theme.AquaLogTheme
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The photo crop on dark water (lot 4b, Task 5). */
@OptIn(ExperimentalTestApi::class)
class PhotoCropSemanticsTest {
    private fun french(block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.FRENCH)
        try { block() } finally { Locale.setDefault(previous) }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.crop(
        onConfirm: (SquareCrop) -> Unit = {},
        onCancel: () -> Unit = {},
        dark: Boolean = false,
    ) {
        setContent { AquaLogTheme(dark) { PhotoCropScreen(ImageBitmap(400, 300), onConfirm, onCancel) } }
        waitForIdle()
    }

    @Test
    fun `confirm sends the centred crop when untouched`() = french {
        runComposeUiTest {
            var sent: SquareCrop? = null
            crop(onConfirm = { sent = it })
            onNodeWithText("Utiliser cette photo").performClick()
            assertEquals(centredCrop(400, 300), sent)
        }
    }

    @Test
    fun `cancel and the glass close both cancel`() = french {
        runComposeUiTest {
            var cancelled = 0
            crop(onCancel = { cancelled++ })
            onAllNodesWithText("Annuler")[0].performClick()
            assertEquals(1, cancelled)
            onAllNodesWithContentDescription("Annuler")[0].performClick()
            assertEquals(2, cancelled)
        }
    }

    @Test
    fun `grid overlays the preview without semantics`() = french {
        runComposeUiTest {
            crop()
            val grid = onNodeWithTag("crop-grid").fetchSemanticsNode()
            val preview = onNodeWithContentDescription("Aperçu du recadrage", substring = true).fetchSemanticsNode()
            assertEquals(preview.boundsInRoot, grid.boundsInRoot)
            assertFalse(grid.config.contains(SemanticsProperties.ContentDescription))
        }
    }

    @Test
    fun `hint and title are shown`() = french {
        runComposeUiTest {
            crop()
            onNodeWithText("Recadrer la photo").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            assertTrue(onAllNodesWithText("Pincez pour zoomer", substring = true).fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test
    fun `confirm label keeps 4·5 to 1 on its white button in dark theme`() = french {
        runComposeUiTest {
            crop(dark = true)
            val label = onNodeWithText("Utiliser cette photo", useUnmergedTree = true).fetchSemanticsNode()
            val layouts = mutableListOf<TextLayoutResult>()
            label.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val color = layouts.single().layoutInput.style.color
            val (light, dark) = listOf(color.luminance(), Color.White.luminance()).sortedDescending()
            val contrast = (light + .05f) / (dark + .05f)
            assertTrue(contrast >= 4.5f, "label $color on white: $contrast")
        }
    }
}
