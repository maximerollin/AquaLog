package com.maximerollin.aqualog.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import com.maximerollin.aqualog.AquaLogApp
import com.maximerollin.aqualog.ExternalLinks
import com.maximerollin.aqualog.resources.*
import com.maximerollin.aqualog.ui.theme.AquaLogTheme
import java.nio.file.Files
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString

class DesktopAppTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `real UI opens on the welcome screen with a fresh local database`() = runComposeUiTest {
        val viewModel = desktopViewModel(Files.createTempDirectory("aqualog").resolve("aqualog.db").toFile())
        setContent {
            AquaLogTheme { AquaLogApp(viewModel, onExportData = {}, onImportData = {}, externalLinks = ExternalLinks()) }
        }
        val start = runBlocking { getString(Res.string.get_started) }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(start).fetchSemanticsNodes().isNotEmpty() }
    }
}
