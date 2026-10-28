package com.maximerollin.aqualog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maximerollin.aqualog.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Links configured by the host app (a blank URL means not configured) and how to open them. */
class ExternalLinks(
    val privacyUrl: String = "",
    val supportUrl: String = "",
    val open: (String) -> Unit = {},
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AquaLogApp(
    viewModel: HomeViewModel,
    onExportData: () -> Unit,
    onImportData: () -> Unit,
    externalLinks: ExternalLinks,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_water),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(stringResource(Res.string.app_name))
                }
            })
        },
    ) { contentPadding ->
        AquaLogContent(
            state = uiState,
            viewModel = viewModel,
            onExportData = onExportData,
            onImportData = onImportData,
            externalLinks = externalLinks,
            modifier = Modifier.padding(contentPadding).consumeWindowInsets(contentPadding),
        )
    }
}
