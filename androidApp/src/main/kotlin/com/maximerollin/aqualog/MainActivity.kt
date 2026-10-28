package com.maximerollin.aqualog

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.maximerollin.aqualog.ui.theme.AquaLogTheme
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val proPurchaseCoordinator by lazy {
        (application as AquaLogApplication).proPurchaseCoordinator(this)
    }
    private val reviewCoordinator by lazy {
        (application as AquaLogApplication).reviewCoordinator(this)
    }
    private val homeViewModel: HomeViewModel by viewModels {
        val application = application as AquaLogApplication
        HomeViewModel.factory(
            repository = application.aquariumRepository,
            taskReminderScheduler = application.taskReminderScheduler,
            accountCoordinator = application.accountCoordinator,
            accountControlCoordinator = application.accountControlCoordinator,
            proPurchaseCoordinator = proPurchaseCoordinator,
            productTelemetry = application.productTelemetry,
            crashReporter = application.crashReporter,
            feedbackGateway = application.feedbackGateway,
            reviewCoordinator = reviewCoordinator,
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
        proPurchaseCoordinator
        setContent {
            AquaLogTheme {
                LightSystemBarsInLightTheme()
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
                    externalLinks = ExternalLinks(BuildConfig.PRIVACY_URL, BuildConfig.SUPPORT_URL) { url ->
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
private fun LightSystemBarsInLightTheme() {
    val dark = isSystemInDarkTheme()
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
}
