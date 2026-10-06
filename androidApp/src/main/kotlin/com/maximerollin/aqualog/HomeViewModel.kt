package com.maximerollin.aqualog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.AquariumRepository
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.EventType
import com.maximerollin.aqualog.shared.MaintenanceActionInput
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.ParameterDefinitionDraft
import com.maximerollin.aqualog.shared.RapidSessionContext
import com.maximerollin.aqualog.shared.RapidSessionInput
import com.maximerollin.aqualog.shared.RecordedSession
import com.maximerollin.aqualog.shared.SessionEditInput
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.evaluateMeasurement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

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
    val rapidSession: RapidSessionUiState? = null,
    val latestSession: RecordedSession? = null,
    val sessionDetail: RecordedSession? = null,
    val showDeleteConfirmation: Boolean = false,
)

data class RapidSessionUiState(
    val context: RapidSessionContext? = null,
    val idempotencyKey: String,
    val aquariumId: String,
    val occurredAtEpochMillis: Long,
    val editingSessionId: String? = null,
    val measurementInputs: Map<String, String> = emptyMap(),
    val actions: Map<MaintenanceActionType, MaintenanceActionInput> = emptyMap(),
    val editingAction: MaintenanceActionType? = null,
    val showObservation: Boolean = false,
    val observation: String = "",
    val showIncident: Boolean = false,
    val incident: String = "",
    val isSaving: Boolean = false,
    val saveError: Boolean = false,
) {
    val hasContent: Boolean
        get() = measurementInputs.values.any(String::isNotBlank) ||
            actions.isNotEmpty() || observation.isNotBlank() || incident.isNotBlank()

    val allMeasurementsValid: Boolean
        get() = context?.activeParameters?.all { definition ->
            evaluateMeasurement(measurementInputs[definition.id].orEmpty(), definition).isValid
        } ?: false

    val canSave: Boolean get() = hasContent && allMeasurementsValid && !isSaving
}

class HomeViewModel(
    private val aquariumRepository: AquariumRepository,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val generateInteractionId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            aquariumRepository.observeCurrentSetup().collectLatest { setup ->
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        setup = setup,
                        isSaving = false,
                        latestSession = if (setup == null) null else it.latestSession,
                    )
                }
                if (setup != null) {
                    aquariumRepository.observeLatestSession(setup.aquarium.id).collect { latest ->
                        mutableUiState.update { state ->
                            state.copy(
                                latestSession = latest,
                                sessionDetail = if (state.sessionDetail?.session?.id == latest?.session?.id) {
                                    latest
                                } else {
                                    state.sessionDetail
                                },
                            )
                        }
                    }
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

    fun openRapidSession() {
        val setup = mutableUiState.value.setup ?: return
        val draft = RapidSessionUiState(
            idempotencyKey = generateInteractionId(),
            aquariumId = setup.aquarium.id,
            occurredAtEpochMillis = currentTimeMillis(),
        )
        mutableUiState.update { it.copy(rapidSession = draft) }
        viewModelScope.launch {
            val context = aquariumRepository.loadRapidSessionContext(setup.aquarium.id)
            mutableUiState.update { state ->
                val current = state.rapidSession
                if (current?.idempotencyKey != draft.idempotencyKey) state else {
                    state.copy(
                        rapidSession = current.copy(
                            context = context,
                            measurementInputs = context.measurementInputs,
                        ),
                    )
                }
            }
        }
    }

    fun closeRapidSession() {
        mutableUiState.update { it.copy(rapidSession = null) }
    }

    fun openSessionDetails() {
        mutableUiState.update { state -> state.copy(sessionDetail = state.latestSession) }
    }

    fun closeSessionDetails() {
        mutableUiState.update { it.copy(sessionDetail = null, showDeleteConfirmation = false) }
    }

    fun editSession() {
        val recorded = mutableUiState.value.sessionDetail ?: return
        val setup = mutableUiState.value.setup ?: return
        val draft = RapidSessionUiState(
            idempotencyKey = recorded.session.id,
            aquariumId = recorded.session.aquariumId,
            occurredAtEpochMillis = recorded.session.occurredAtEpochMillis,
            editingSessionId = recorded.session.id,
        )
        mutableUiState.update { it.copy(rapidSession = draft) }
        viewModelScope.launch {
            val context = aquariumRepository.loadRapidSessionContext(setup.aquarium.id)
            val observations = recorded.events.associateBy { it.type }
            mutableUiState.update { state ->
                val current = state.rapidSession
                if (current?.editingSessionId != recorded.session.id) state else {
                    state.copy(
                        rapidSession = current.copy(
                            context = context,
                            measurementInputs = context.measurementInputs + recorded.measurements.associate {
                                it.parameterDefinitionId to it.value.displayInput()
                            },
                            actions = recorded.maintenanceActions.associate { action ->
                                action.type to MaintenanceActionInput(
                                    type = action.type,
                                    quantity = action.quantity.displayInput(),
                                    unit = action.unit.orEmpty(),
                                    product = action.product.orEmpty(),
                                )
                            },
                            showObservation = EventType.OBSERVATION in observations,
                            observation = observations[EventType.OBSERVATION]?.note.orEmpty(),
                            showIncident = EventType.INCIDENT in observations,
                            incident = observations[EventType.INCIDENT]?.note.orEmpty(),
                        ),
                    )
                }
            }
        }
    }

    fun requestSessionDeletion() {
        mutableUiState.update { it.copy(showDeleteConfirmation = true) }
    }

    fun cancelSessionDeletion() {
        mutableUiState.update { it.copy(showDeleteConfirmation = false) }
    }

    fun confirmSessionDeletion() {
        val sessionId = mutableUiState.value.sessionDetail?.session?.id ?: return
        mutableUiState.update { it.copy(showDeleteConfirmation = false) }
        viewModelScope.launch {
            aquariumRepository.deleteSession(sessionId)
            mutableUiState.update { it.copy(sessionDetail = null) }
        }
    }

    fun shiftSessionTimeByMinutes(minutes: Int) = updateRapidSession {
        it.copy(occurredAtEpochMillis = it.occurredAtEpochMillis + minutes * 60_000L)
    }

    fun updateMeasurement(parameterDefinitionId: String, value: String) = updateRapidSession {
        it.copy(
            measurementInputs = it.measurementInputs + (parameterDefinitionId to value),
            saveError = false,
        )
    }

    fun toggleMaintenanceAction(type: MaintenanceActionType) = updateRapidSession { state ->
        if (type in state.actions) {
            state.copy(actions = state.actions - type, editingAction = null)
        } else {
            val previous = state.context?.lastMaintenanceActions?.get(type)
            val default = when (type) {
                MaintenanceActionType.WATER_CHANGE -> MaintenanceActionInput(type, "20", "%")
                MaintenanceActionType.FERTILIZATION -> MaintenanceActionInput(type, "1", "mL")
                else -> MaintenanceActionInput(type)
            }
            state.copy(
                actions = state.actions + (
                    type to previous?.let {
                        MaintenanceActionInput(
                            type = type,
                            quantity = it.quantity.displayInput(),
                            unit = it.unit.orEmpty(),
                            product = it.product.orEmpty(),
                        )
                    }.orDefault(default)
                ),
                saveError = false,
            )
        }
    }

    fun editMaintenanceAction(type: MaintenanceActionType) = updateRapidSession {
        it.copy(editingAction = if (it.editingAction == type) null else type)
    }

    fun updateMaintenanceQuantity(type: MaintenanceActionType, value: String) = updateAction(type) {
        it.copy(quantity = value)
    }

    fun updateMaintenanceUnit(type: MaintenanceActionType, value: String) = updateAction(type) {
        it.copy(unit = value)
    }

    fun updateMaintenanceProduct(type: MaintenanceActionType, value: String) = updateAction(type) {
        it.copy(product = value)
    }

    fun toggleObservation() = updateRapidSession { it.copy(showObservation = !it.showObservation) }

    fun updateObservation(value: String) = updateRapidSession { it.copy(observation = value, saveError = false) }

    fun toggleIncident() = updateRapidSession { it.copy(showIncident = !it.showIncident) }

    fun updateIncident(value: String) = updateRapidSession { it.copy(incident = value, saveError = false) }

    fun saveRapidSession() {
        val draft = mutableUiState.value.rapidSession ?: return
        if (!draft.canSave) return
        mutableUiState.update { it.copy(rapidSession = draft.copy(isSaving = true, saveError = false)) }
        viewModelScope.launch {
            runCatching {
                if (draft.editingSessionId == null) {
                    aquariumRepository.saveRapidSession(
                        RapidSessionInput(
                            aquariumId = draft.aquariumId,
                            occurredAtEpochMillis = draft.occurredAtEpochMillis,
                            idempotencyKey = draft.idempotencyKey,
                            measurementInputs = draft.measurementInputs,
                            maintenanceActions = draft.actions.values.toList(),
                            observation = draft.observation,
                            incident = draft.incident,
                        ),
                    )
                } else {
                    aquariumRepository.updateSession(
                        SessionEditInput(
                            sessionId = draft.editingSessionId,
                            aquariumId = draft.aquariumId,
                            occurredAtEpochMillis = draft.occurredAtEpochMillis,
                            measurementInputs = draft.measurementInputs,
                            maintenanceActions = draft.actions.values.toList(),
                            observation = draft.observation,
                            incident = draft.incident,
                        ),
                    )
                }
            }.onSuccess { recorded ->
                mutableUiState.update {
                    it.copy(
                        rapidSession = null,
                        latestSession = recorded,
                        sessionDetail = if (draft.editingSessionId == null) null else recorded,
                    )
                }
            }.onFailure {
                updateRapidSession { it.copy(isSaving = false, saveError = true) }
            }
        }
    }

    private fun updateAction(
        type: MaintenanceActionType,
        transform: (MaintenanceActionInput) -> MaintenanceActionInput,
    ) = updateRapidSession { state ->
        val action = state.actions[type] ?: return@updateRapidSession state
        state.copy(actions = state.actions + (type to transform(action)), saveError = false)
    }

    private fun updateRapidSession(transform: (RapidSessionUiState) -> RapidSessionUiState) {
        mutableUiState.update { state ->
            state.rapidSession?.let { state.copy(rapidSession = transform(it)) } ?: state
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

private fun <T> T?.orDefault(default: T): T = this ?: default
