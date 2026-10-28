package com.maximerollin.aqualog.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.maximerollin.aqualog.ui.theme.AquaLogTheme
import com.maximerollin.aqualog.resources.*
import org.jetbrains.compose.resources.stringResource

fun main() = application {
    Window(
        title = "AquaLog",
        state = rememberWindowState(size = DpSize(width = 412.dp, height = 915.dp)),
        onCloseRequest = ::exitApplication,
    ) {
        AquaLogTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.app_name), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
