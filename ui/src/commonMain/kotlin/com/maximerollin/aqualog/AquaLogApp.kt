package com.maximerollin.aqualog

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Links configured by the host app (a blank URL means not configured) and how to open them. */
class ExternalLinks(
    val privacyUrl: String = "",
    val supportUrl: String = "",
    val open: (String) -> Unit = {},
)

@Composable
fun AquaLogApp(
    viewModel: HomeViewModel,
    onExportData: () -> Unit,
    onImportData: () -> Unit,
    externalLinks: ExternalLinks,
    rateApp: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // A notification tap brings back a home computed on an earlier day.
    LifecycleStartEffect(viewModel) {
        viewModel.refreshForToday()
        onStopOrDispose { }
    }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { contentPadding ->
        AquaLogContent(
            state = uiState,
            viewModel = viewModel,
            onExportData = onExportData,
            onImportData = onImportData,
            externalLinks = externalLinks,
            rateApp = rateApp,
            modifier = Modifier.padding(contentPadding).consumeWindowInsets(contentPadding),
        )
    }
}
