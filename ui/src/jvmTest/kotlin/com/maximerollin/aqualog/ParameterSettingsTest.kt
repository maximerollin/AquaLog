package com.maximerollin.aqualog

import androidx.lifecycle.viewModelScope
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.RoomAquariumRepository
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.createAquaLogDatabase
import com.maximerollin.aqualog.shared.createJvmDatabaseBuilder
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Parameters chosen in onboarding can be changed afterwards, and a Session measured before still edits. */
@OptIn(ExperimentalCoroutinesApi::class)
class ParameterSettingsTest {
    @BeforeTest
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun `leaving the settings saves a switched Parameter and its range, an invalid row keeps them open`() = runTest {
        val directory = Files.createTempDirectory("aqualog-parameter-settings")
        val database = createAquaLogDatabase(createJvmDatabaseBuilder(directory.resolve("aqualog.db").toString()))
        val repository = RoomAquariumRepository(database, { UUID.randomUUID().toString() }, { 1_700_000_000_000L })
        val created = repository.createConfiguredAquarium(
            "Salon", 120.0, VolumeUnit.LITERS, AquariumProfile.ESTABLISHED,
            OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
        )
        val viewModel = HomeViewModel(repository)
        try {
            viewModel.uiState.first { it.setup != null }
            // A pH measured while it is active, to edit once pH is switched off.
            viewModel.openRapidSession()
            await { viewModel.uiState.value.rapidSession?.context != null }
            val ph = created.parameters.first { it.parameter == BuiltInParameter.PH }
            viewModel.updateMeasurement(ph.id, "7.2")
            viewModel.saveRapidSession(confirmed = true)
            await { viewModel.uiState.value.rapidSession == null }

            viewModel.openParameterSettings()
            val inactive = viewModel.uiState.value.parameters.first { !it.isActive }.parameter
            viewModel.setParameterActive(inactive, true)
            viewModel.setParameterActive(BuiltInParameter.PH, false)
            viewModel.updateParameterMinimum(BuiltInParameter.NITRATE, "30")
            viewModel.updateParameterMaximum(BuiltInParameter.NITRATE, "10")
            viewModel.closeParameterSettings()
            assertTrue(viewModel.uiState.value.showParameterSettings)
            assertTrue(viewModel.uiState.value.showParameterValidationError)

            viewModel.updateParameterMinimum(BuiltInParameter.NITRATE, "5")
            viewModel.closeParameterSettings()
            await { !viewModel.uiState.value.showParameterSettings }

            val stored = repository.loadSetup(created.aquarium.id)!!.parameters.associateBy { it.parameter }
            assertTrue(stored.getValue(inactive).isActive)
            assertFalse(stored.getValue(BuiltInParameter.PH).isActive)
            assertEquals(5.0, stored.getValue(BuiltInParameter.NITRATE).indicativeMinimum)
            assertEquals(10.0, stored.getValue(BuiltInParameter.NITRATE).indicativeMaximum)

            viewModel.openSessionDetails()
            await { viewModel.uiState.value.sessionDetail != null }
            val sessionId = viewModel.uiState.value.sessionDetail!!.session.id
            viewModel.editSession()
            await { viewModel.uiState.value.rapidSession?.let { it.editingSessionId != null && it.context != null } == true }
            viewModel.saveRapidSession(confirmed = true)
            await { viewModel.uiState.value.rapidSession == null }
            val measurements = repository.observeSession(sessionId).first()!!.measurements
            assertEquals(7.2, measurements.single { it.parameterDefinitionId == ph.id }.value)
        } finally {
            viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
            database.close()
        }
    }

    /** Real time: the view model's work runs on Room's own dispatchers. */
    private suspend fun await(condition: () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (!condition()) delay(10) }
    }
}
