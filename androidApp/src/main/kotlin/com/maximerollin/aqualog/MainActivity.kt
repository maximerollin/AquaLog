package com.maximerollin.aqualog

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maximerollin.aqualog.ui.theme.AquaLogTheme

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels {
        val application = application as AquaLogApplication
        HomeViewModel.factory(application.aquariumRepository, application.accountCoordinator)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AquaLogTheme {
                AquaLogApp(homeViewModel)
            }
        }
        handleAuthenticationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthenticationIntent(intent)
    }

    private fun handleAuthenticationIntent(intent: Intent?) {
        val callbackUrl = intent?.dataString ?: return
        setIntent(Intent(this, MainActivity::class.java))
        homeViewModel.completeAccountCallback(callbackUrl)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AquaLogApp(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { contentPadding ->
        AquaLogContent(
            state = uiState,
            viewModel = viewModel,
            modifier = Modifier.padding(contentPadding),
        )
    }
}
