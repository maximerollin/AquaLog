package com.maximerollin.aqualog.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.maximerollin.aqualog.AquaLogApp
import com.maximerollin.aqualog.ExternalLinks
import com.maximerollin.aqualog.HomeViewModel
import com.maximerollin.aqualog.shared.RoomAquariumRepository
import com.maximerollin.aqualog.shared.createAquaLogDatabase
import com.maximerollin.aqualog.shared.createJvmDatabaseBuilder
import com.maximerollin.aqualog.ui.theme.AquaLogTheme
import java.io.File
import java.util.UUID

// Desktop dev harness: the real UI on a local database; account, billing, sync and telemetry stay disabled.
fun main() = application {
    val viewModel = remember {
        val databaseFile = File(System.getProperty("user.home"), ".aqualog-desktop/aqualog.db").apply { parentFile.mkdirs() }
        HomeViewModel(
            RoomAquariumRepository(
                database = createAquaLogDatabase(createJvmDatabaseBuilder(databaseFile.absolutePath)),
                generateId = { UUID.randomUUID().toString() },
                currentTimeMillis = System::currentTimeMillis,
            ),
        )
    }
    Window(
        title = "AquaLog",
        state = rememberWindowState(size = DpSize(width = 412.dp, height = 915.dp)),
        onCloseRequest = ::exitApplication,
    ) {
        AquaLogTheme {
            AquaLogApp(viewModel, onExportData = {}, onImportData = {}, externalLinks = ExternalLinks())
        }
    }
}
