package com.maximerollin.aqualog

import androidx.lifecycle.viewModelScope
import com.maximerollin.aqualog.shared.AquariumCreationAccess
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

/** Editing a Session shows its decimals with the separator the person types: « 7,8 » in French, not « 7.8 ». */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionEditDecimalTest {
    @BeforeTest
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun `a comma locale gets the pH back with a comma`() = runTest {
        assertEquals("7,8", editedPh(decimalSeparator = ','))
    }

    @Test
    fun `a point locale keeps the point`() = runTest {
        assertEquals("7.8", editedPh(decimalSeparator = '.'))
    }

    /** Saves pH « 7,8 », opens the Session for editing and returns what its pH field shows. */
    private suspend fun editedPh(decimalSeparator: Char): String {
        val directory = Files.createTempDirectory("aqualog-edit-decimal")
        val database = createAquaLogDatabase(createJvmDatabaseBuilder(directory.resolve("aqualog.db").toString()))
        val repository = RoomAquariumRepository(database, { UUID.randomUUID().toString() }, { 1_700_000_000_000L })
        repository.createConfiguredAquarium(
            "Salon", 120.0, VolumeUnit.LITERS, AquariumProfile.ESTABLISHED,
            OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED), AquariumCreationAccess.PRO,
        )
        val viewModel = HomeViewModel(repository, decimalSeparator = { decimalSeparator })
        try {
            viewModel.uiState.first { it.setup != null && it.aquariums.isNotEmpty() }
            viewModel.openRapidSession()
            await { viewModel.uiState.value.rapidSession?.context != null }
            val ph = viewModel.uiState.value.rapidSession!!.context!!.activeParameters.first { it.parameter == BuiltInParameter.PH }
            viewModel.updateMeasurement(ph.id, "7,8")
            viewModel.saveRapidSession(confirmed = true)
            await { viewModel.uiState.value.rapidSession == null }
            viewModel.openSessionDetails()
            viewModel.editSession()
            await { viewModel.uiState.value.rapidSession?.let { it.editingSessionId != null && it.context != null } == true }
            return viewModel.uiState.value.rapidSession!!.measurementInputs.getValue(ph.id)
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
