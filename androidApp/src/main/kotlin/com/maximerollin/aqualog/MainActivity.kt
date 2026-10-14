package com.maximerollin.aqualog

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.lifecycleScope
import com.maximerollin.aqualog.ui.theme.AquaLogTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels {
        val application = application as AquaLogApplication
        HomeViewModel.factory(
            repository = application.aquariumRepository,
            taskReminderScheduler = application.taskReminderScheduler,
            accountCoordinator = application.accountCoordinator,
            accountControlCoordinator = application.accountControlCoordinator,
            onSyncRetryRequested = { AndroidSyncScheduler.requestImmediate(application) },
        )
    }

    private val createExportDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) {
            homeViewModel.cancelPortableDataOperation()
        } else {
            writePortableExport(uri)
        }
    }

    private val openImportDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            homeViewModel.cancelPortableDataOperation()
        } else {
            readPortableImport(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AquaLogTheme {
                AquaLogApp(
                    viewModel = homeViewModel,
                    onExportData = {
                        homeViewModel.startPortableDataOperation()
                        createExportDocument.launch("aqualog-export-v1.json")
                    },
                    onImportData = {
                        homeViewModel.startPortableDataOperation()
                        openImportDocument.launch(arrayOf("application/json"))
                    },
                    onOpenExternalUrl = { url ->
                        if (url.startsWith("https://")) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                )
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

    private fun writePortableExport(uri: Uri) {
        lifecycleScope.launch {
            val succeeded = try {
                withContext(Dispatchers.IO) {
                    val payload = (application as AquaLogApplication).portableDataService.export()
                    requireNotNull(contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use {
                        it.write(payload)
                    }
                }
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (succeeded) homeViewModel.completePortableExport() else homeViewModel.failPortableDataAccess()
        }
    }

    private fun readPortableImport(uri: Uri) {
        lifecycleScope.launch {
            val payload = try {
                withContext(Dispatchers.IO) { readPortablePayload(uri) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                homeViewModel.failPortableDataAccess()
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                (application as AquaLogApplication).portableDataService.importData(payload)
            }
            homeViewModel.completePortableImport(result)
        }
    }

    private fun readPortablePayload(uri: Uri): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        requireNotNull(contentResolver.openInputStream(uri)).use { input ->
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_PORTABLE_DATA_BYTES) { "Portable data file is too large" }
                output.write(buffer, 0, read)
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    companion object {
        private const val MAX_PORTABLE_DATA_BYTES = 10 * 1024 * 1024
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AquaLogApp(
    viewModel: HomeViewModel,
    onExportData: () -> Unit,
    onImportData: () -> Unit,
    onOpenExternalUrl: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { contentPadding ->
        AquaLogContent(
            state = uiState,
            viewModel = viewModel,
            onExportData = onExportData,
            onImportData = onImportData,
            onOpenExternalUrl = onOpenExternalUrl,
            modifier = Modifier.padding(contentPadding),
        )
    }
}
