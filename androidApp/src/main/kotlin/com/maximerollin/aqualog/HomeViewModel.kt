package com.maximerollin.aqualog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.AquariumRepository
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.ParameterDefinitionDraft
import com.maximerollin.aqualog.shared.VolumeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep {
    WELCOME,
    PROFILE,
    AQUARIUM,
    PARAMETERS,
    PRACTICE,
    PAYWALL,
}

data class ParameterEditorState(
    val parameter: BuiltInParameter,
    val isActive: Boolean,
    val unit: String,
    val precision: String,
    val indicativeMinimum: String,
    val indicativeMaximum: String,
) {
    fun toDraft(position: Int): ParameterDefinitionDraft? {
        val parsedPrecision = precision.toIntOrNull() ?: return null
        val minimum = indicativeMinimum.toOptionalDecimal() ?: return null
        val maximum = indicativeMaximum.toOptionalDecimal() ?: return null
        if (unit.isBlank() || parsedPrecision !in 0..3) return null
        if (minimum.value != null && maximum.value != null && minimum.value > maximum.value) return null
        return ParameterDefinitionDraft(
            parameter = parameter,
            isActive = isActive,
            position = position,
            unit = unit.trim(),
            precision = parsedPrecision,
            indicativeMinimum = minimum.value,
            indicativeMaximum = maximum.value,
        )
    }

    companion object {
        fun from(draft: ParameterDefinitionDraft) = ParameterEditorState(
            parameter = draft.parameter,
            isActive = draft.isActive,
            unit = draft.unit,
            precision = draft.precision.toString(),
            indicativeMinimum = draft.indicativeMinimum.displayInput(),
            indicativeMaximum = draft.indicativeMaximum.displayInput(),
        )
    }
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val setup: AquariumSetup? = null,
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val profile: AquariumProfile = AquariumProfile.ESTABLISHED,
    val aquariumName: String = "",
    val volumeInput: String = "",
    val volumeUnit: VolumeUnit = VolumeUnit.LITERS,
    val parameters: List<ParameterEditorState> = OnboardingPresets
        .parametersFor(AquariumProfile.ESTABLISHED)
        .map(ParameterEditorState::from),
    val practiceValues: Map<BuiltInParameter, String> = emptyMap(),
    val isSaving: Boolean = false,
    val showAquariumValidationError: Boolean = false,
    val showParameterValidationError: Boolean = false,
)

class HomeViewModel(
    private val aquariumRepository: AquariumRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            aquariumRepository.observeCurrentSetup().collect { setup ->
                mutableUiState.update {
                    it.copy(isLoading = false, setup = setup, isSaving = false)
                }
            }
        }
    }

    fun startOnboarding() = moveTo(OnboardingStep.PROFILE)

    fun selectProfile(profile: AquariumProfile) {
        mutableUiState.update {
            it.copy(
                profile = profile,
                parameters = OnboardingPresets.parametersFor(profile).map(ParameterEditorState::from),
                practiceValues = emptyMap(),
            )
        }
    }

    fun continueToAquarium() = moveTo(OnboardingStep.AQUARIUM)

    fun updateAquariumName(name: String) {
        mutableUiState.update { it.copy(aquariumName = name, showAquariumValidationError = false) }
    }

    fun updateVolume(volume: String) {
        mutableUiState.update { it.copy(volumeInput = volume, showAquariumValidationError = false) }
    }

    fun selectVolumeUnit(unit: VolumeUnit) {
        mutableUiState.update { it.copy(volumeUnit = unit) }
    }

    fun continueToParameters() {
        val state = mutableUiState.value
        val volume = state.volumeInput.toDecimalOrNull()
        if (state.aquariumName.isBlank() || volume == null || volume <= 0.0 || !volume.isFinite()) {
            mutableUiState.update { it.copy(showAquariumValidationError = true) }
            return
        }
        moveTo(OnboardingStep.PARAMETERS)
    }

    fun setParameterActive(parameter: BuiltInParameter, isActive: Boolean) =
        updateParameter(parameter) { it.copy(isActive = isActive) }

    fun updateParameterUnit(parameter: BuiltInParameter, unit: String) =
        updateParameter(parameter) { it.copy(unit = unit) }

    fun updateParameterPrecision(parameter: BuiltInParameter, precision: String) =
        updateParameter(parameter) { it.copy(precision = precision) }

    fun updateParameterMinimum(parameter: BuiltInParameter, minimum: String) =
        updateParameter(parameter) { it.copy(indicativeMinimum = minimum) }

    fun updateParameterMaximum(parameter: BuiltInParameter, maximum: String) =
        updateParameter(parameter) { it.copy(indicativeMaximum = maximum) }

    fun moveParameter(parameter: BuiltInParameter, offset: Int) {
        mutableUiState.update { state ->
            val currentIndex = state.parameters.indexOfFirst { it.parameter == parameter }
            val targetIndex = (currentIndex + offset).coerceIn(state.parameters.indices)
            if (currentIndex == -1 || currentIndex == targetIndex) state else {
                val reordered = state.parameters.toMutableList().apply {
                    add(targetIndex, removeAt(currentIndex))
                }
                state.copy(parameters = reordered, showParameterValidationError = false)
            }
        }
    }

    fun continueToPractice() {
        val parameters = configuredParameters() ?: run {
            mutableUiState.update { it.copy(showParameterValidationError = true) }
            return
        }
        if (parameters.none(ParameterDefinitionDraft::isActive)) {
            mutableUiState.update { it.copy(showParameterValidationError = true) }
            return
        }
        moveTo(OnboardingStep.PRACTICE)
    }

    fun updatePracticeValue(parameter: BuiltInParameter, value: String) {
        mutableUiState.update {
            it.copy(practiceValues = it.practiceValues + (parameter to value))
        }
    }

    fun continueToPaywall() = moveTo(OnboardingStep.PAYWALL)

    fun finishForFree() {
        val state = mutableUiState.value
        if (state.isSaving) return
        val volume = state.volumeInput.toDecimalOrNull() ?: return
        val parameters = configuredParameters() ?: return
        mutableUiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            runCatching {
                aquariumRepository.createConfiguredAquarium(
                    name = state.aquariumName,
                    volume = volume,
                    volumeUnit = state.volumeUnit,
                    profile = state.profile,
                    parameters = parameters,
                )
            }.onFailure {
                mutableUiState.update { current -> current.copy(isSaving = false) }
            }
        }
    }

    private fun moveTo(step: OnboardingStep) {
        mutableUiState.update { it.copy(step = step) }
    }

    private fun updateParameter(
        parameter: BuiltInParameter,
        transform: (ParameterEditorState) -> ParameterEditorState,
    ) {
        mutableUiState.update { state ->
            state.copy(
                parameters = state.parameters.map {
                    if (it.parameter == parameter) transform(it) else it
                },
                showParameterValidationError = false,
            )
        }
    }

    private fun configuredParameters(): List<ParameterDefinitionDraft>? =
        mutableUiState.value.parameters.mapIndexed { index, parameter ->
            parameter.toDraft(index) ?: return null
        }

    companion object {
        fun factory(repository: AquariumRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(repository) as T
            }
    }
}

private data class OptionalDecimal(val value: Double?)

private fun String.toOptionalDecimal(): OptionalDecimal? =
    if (isBlank()) OptionalDecimal(null) else toDecimalOrNull()?.let(::OptionalDecimal)

private fun String.toDecimalOrNull(): Double? = replace(',', '.').toDoubleOrNull()

private fun Double?.displayInput(): String = when {
    this == null -> ""
    this % 1.0 == 0.0 -> toLong().toString()
    else -> toString()
}
