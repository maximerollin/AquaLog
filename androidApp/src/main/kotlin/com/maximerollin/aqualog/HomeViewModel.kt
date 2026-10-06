package com.maximerollin.aqualog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.AccountActivationCoordinator
import com.maximerollin.aqualog.shared.AccountActivationResult
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
import com.maximerollin.aqualog.shared.TimelineFilter
import com.maximerollin.aqualog.shared.TimelineSession
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.evaluateMeasurement
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Serializable
import java.util.UUID

enum class OnboardingStep {
    WELCOME,
    PROFILE,
    AQUARIUM,
    PARAMETERS,
    PRACTICE,
    PAYWALL,
}

enum class AccountStep {
    HIDDEN,
    INVITATION,
    METHODS,
    MAGIC_EMAIL,
    MAGIC_SENT,
    WAITING_BROWSER,
    WORKING,
    ERROR,
}

enum class AccountError { AUTHENTICATION, EXPIRED_LINK, MIGRATION }

data class AccountUiState(
    val step: AccountStep = AccountStep.HIDDEN,
    val email: String = "",
    val error: AccountError? = null,
)

enum class MainDestination {
    HOME,
    HISTORY,
    SETTINGS,
}

enum class TimelinePeriod(val days: Int?) {
    ALL(null),
    SEVEN_DAYS(7),
    THIRTY_DAYS(30),
    NINETY_DAYS(90),
}

data class TimelineUiState(
    val isLoading: Boolean = false,
    val isOffline: Boolean = true,
    val entries: List<TimelineSession> = emptyList(),
    val period: TimelinePeriod = TimelinePeriod.ALL,
    val aquariumId: String? = null,
    val parameterDefinitionId: String? = null,
    val maintenanceActionType: MaintenanceActionType? = null,
    val selectedSessionId: String? = null,
    val hasRecoverableError: Boolean = false,
)

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
    val hasPendingAccountInvitation: Boolean = false,
    val account: AccountUiState = AccountUiState(),
    val sessionDetail: RecordedSession? = null,
    val sessionDetailReturnsToTimeline: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val destination: MainDestination = MainDestination.HOME,
    val timeline: TimelineUiState = TimelineUiState(),
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

private data class RapidSessionDraftSnapshot(
    val idempotencyKey: String,
    val aquariumId: String,
    val occurredAtEpochMillis: Long,
    val editingSessionId: String?,
    val measurementInputs: Map<String, String>,
    val actions: List<MaintenanceActionDraftSnapshot>,
    val editingAction: String?,
    val showObservation: Boolean,
    val observation: String,
    val showIncident: Boolean,
    val incident: String,
) : Serializable

private data class MaintenanceActionDraftSnapshot(
    val type: String,
    val quantity: String,
    val unit: String,
    val product: String,
) : Serializable

class HomeViewModel(
    private val aquariumRepository: AquariumRepository,
    private val accountCoordinator: AccountActivationCoordinator? = null,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val generateInteractionId: () -> String = { UUID.randomUUID().toString() },
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        HomeUiState(
            rapidSession = savedStateHandle
                .get<RapidSessionDraftSnapshot>(rapidSessionDraftKey)
                ?.toUiState(),
        ),
    )
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()
    private var timelineJob: Job? = null

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
                    restoreRapidSessionContext(setup)
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
        if (accountCoordinator != null) {
            viewModelScope.launch {
                if (accountCoordinator.hasPendingMigration()) {
                    updateAccount { it.copy(step = AccountStep.ERROR, error = AccountError.MIGRATION) }
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
        setRapidSession(draft)
        viewModelScope.launch {
            val context = aquariumRepository.loadRapidSessionContext(setup.aquarium.id)
            mutableUiState.update { state ->
                val current = state.rapidSession
                if (current?.idempotencyKey != draft.idempotencyKey) state else {
                    val hydrated = current.copy(
                        context = context,
                        measurementInputs = context.measurementInputs,
                    )
                    savedStateHandle[rapidSessionDraftKey] = hydrated.toSnapshot()
                    state.copy(rapidSession = hydrated)
                }
            }
        }
    }

    fun closeRapidSession() {
        setRapidSession(null)
    }

    fun openSessionDetails() {
        mutableUiState.update { state ->
            state.copy(
                sessionDetail = state.latestSession,
                sessionDetailReturnsToTimeline = false,
            )
        }
    }

    fun closeSessionDetails() {
        mutableUiState.update {
            it.copy(
                sessionDetail = null,
                sessionDetailReturnsToTimeline = false,
                showDeleteConfirmation = false,
            )
        }
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
        setRapidSession(draft)
        viewModelScope.launch {
            val context = aquariumRepository.loadRapidSessionContext(setup.aquarium.id)
            val observations = recorded.events.associateBy { it.type }
            mutableUiState.update { state ->
                val current = state.rapidSession
                if (current?.editingSessionId != recorded.session.id) state else {
                    val hydrated = current.copy(
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
                    )
                    savedStateHandle[rapidSessionDraftKey] = hydrated.toSnapshot()
                    state.copy(rapidSession = hydrated)
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

    fun selectDestination(destination: MainDestination) {
        mutableUiState.update { it.copy(destination = destination) }
        if (destination == MainDestination.HISTORY && timelineJob == null) {
            observeTimeline()
        }
    }

    fun selectTimelinePeriod(period: TimelinePeriod) = updateTimelineFilter {
        it.copy(period = period)
    }

    fun selectTimelineAquarium(aquariumId: String?) = updateTimelineFilter {
        it.copy(aquariumId = aquariumId)
    }

    fun selectTimelineParameter(parameterDefinitionId: String?) = updateTimelineFilter {
        it.copy(parameterDefinitionId = parameterDefinitionId)
    }

    fun selectTimelineAction(type: MaintenanceActionType?) = updateTimelineFilter {
        it.copy(maintenanceActionType = type)
    }

    fun openTimelineSession(sessionId: String) {
        mutableUiState.update { state ->
            val entry = state.timeline.entries.firstOrNull { it.session.id == sessionId }
                ?: return@update state
            state.copy(
                sessionDetail = RecordedSession(
                    session = entry.session,
                    measurements = entry.measurements.map { it.measurement },
                    maintenanceActions = entry.maintenanceActions,
                    events = entry.events,
                ),
                sessionDetailReturnsToTimeline = true,
            )
        }
    }

    fun closeTimelineSession() = closeSessionDetails()

    fun retryTimeline() = observeTimeline()

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
                savedStateHandle[rapidSessionDraftKey] = null
                mutableUiState.update {
                    it.copy(
                        rapidSession = null,
                        latestSession = recorded,
                        sessionDetail = if (draft.editingSessionId == null) null else recorded,
                    )
                }
                if (accountCoordinator?.shouldInviteToAccount() == true) {
                    mutableUiState.update { it.copy(hasPendingAccountInvitation = true) }
                }
            }.onFailure {
                updateRapidSession { it.copy(isSaving = false, saveError = true) }
            }
        }
    }

    fun continueAfterSessionConfirmation() {
        val coordinator = accountCoordinator ?: return
        if (!mutableUiState.value.hasPendingAccountInvitation) return
        mutableUiState.update { it.copy(hasPendingAccountInvitation = false) }
        viewModelScope.launch {
            runCatching { coordinator.markInvitationOffered() }
                .onSuccess {
                    mutableUiState.update {
                        it.copy(account = AccountUiState(AccountStep.INVITATION))
                    }
                }
                .onFailure {
                    mutableUiState.update { it.copy(hasPendingAccountInvitation = true) }
                }
        }
    }

    fun openAccountMethods() = updateAccount { it.copy(step = AccountStep.METHODS, error = null) }

    fun dismissAccount() = updateAccount { AccountUiState() }

    fun openMagicEmail() = updateAccount { it.copy(step = AccountStep.MAGIC_EMAIL, error = null) }

    fun updateAccountEmail(value: String) = updateAccount { it.copy(email = value, error = null) }

    fun signInWithGoogle() = launchAccountAction(AccountError.AUTHENTICATION) {
        requireNotNull(accountCoordinator).signInWithGoogle()
    }

    fun requestMagicLink() {
        val email = mutableUiState.value.account.email
        launchAccountAction(AccountError.AUTHENTICATION) {
            requireNotNull(accountCoordinator).requestMagicLink(email)
        }
    }

    fun completeAccountCallback(callbackUrl: String) = launchAccountAction(AccountError.AUTHENTICATION) {
        requireNotNull(accountCoordinator).completeMagicLink(callbackUrl)
    }

    fun retryAccountMigration() = launchAccountAction(AccountError.MIGRATION) {
        requireNotNull(accountCoordinator).resumePendingMigration()
    }

    private fun launchAccountAction(
        failure: AccountError,
        action: suspend () -> AccountActivationResult,
    ) {
        if (accountCoordinator == null) return
        updateAccount { it.copy(step = AccountStep.WORKING, error = null) }
        viewModelScope.launch {
            handleAccountResult(
                result = runCatching { action() }.getOrElse { AccountActivationResult.Failed("failed") },
                failure = failure,
            )
        }
    }

    private fun handleAccountResult(result: AccountActivationResult, failure: AccountError) {
        updateAccount { current ->
            when (result) {
                AccountActivationResult.Activated -> AccountUiState()
                AccountActivationResult.AwaitingAuthentication -> current.copy(step = AccountStep.WAITING_BROWSER)
                AccountActivationResult.Cancelled -> current.copy(step = AccountStep.METHODS, error = null)
                AccountActivationResult.ExpiredLink -> current.copy(step = AccountStep.ERROR, error = AccountError.EXPIRED_LINK)
                AccountActivationResult.MagicLinkSent -> current.copy(step = AccountStep.MAGIC_SENT)
                AccountActivationResult.IgnoredCallback -> AccountUiState()
                is AccountActivationResult.Failed -> current.copy(step = AccountStep.ERROR, error = failure)
                is AccountActivationResult.MigrationFailed -> current.copy(
                    step = AccountStep.ERROR,
                    error = AccountError.MIGRATION,
                )
            }
        }
    }

    private fun updateAccount(transform: (AccountUiState) -> AccountUiState) {
        mutableUiState.update { it.copy(account = transform(it.account)) }
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
            state.rapidSession?.let {
                val updated = transform(it)
                savedStateHandle[rapidSessionDraftKey] = updated.toSnapshot()
                state.copy(rapidSession = updated)
            } ?: state
        }
    }

    private fun setRapidSession(draft: RapidSessionUiState?) {
        savedStateHandle[rapidSessionDraftKey] = draft?.toSnapshot()
        mutableUiState.update { it.copy(rapidSession = draft) }
    }

    private suspend fun restoreRapidSessionContext(setup: AquariumSetup) {
        val draft = mutableUiState.value.rapidSession ?: return
        if (draft.context != null || draft.aquariumId != setup.aquarium.id) return
        val context = aquariumRepository.loadRapidSessionContext(setup.aquarium.id)
        mutableUiState.update { state ->
            val current = state.rapidSession
            if (current == null || current.idempotencyKey != draft.idempotencyKey) state else {
                val hydrated = current.copy(
                    context = context,
                    measurementInputs = context.measurementInputs + current.measurementInputs,
                )
                savedStateHandle[rapidSessionDraftKey] = hydrated.toSnapshot()
                state.copy(rapidSession = hydrated)
            }
        }
    }

    private fun updateTimelineFilter(transform: (TimelineUiState) -> TimelineUiState) {
        mutableUiState.update { state ->
            state.copy(timeline = transform(state.timeline).copy(selectedSessionId = null))
        }
        observeTimeline()
    }

    private fun observeTimeline() {
        timelineJob?.cancel()
        val timeline = mutableUiState.value.timeline
        val since = timeline.period.days?.let { days ->
            currentTimeMillis() - days * 24L * 60L * 60L * 1_000L
        }
        mutableUiState.update { state ->
            state.copy(
                timeline = state.timeline.copy(
                    isLoading = true,
                    hasRecoverableError = false,
                ),
            )
        }
        timelineJob = viewModelScope.launch {
            aquariumRepository.observeTimeline(
                TimelineFilter(
                    sinceEpochMillisInclusive = since,
                    aquariumId = timeline.aquariumId,
                    parameterDefinitionId = timeline.parameterDefinitionId,
                    maintenanceActionType = timeline.maintenanceActionType,
                ),
            ).catch {
                mutableUiState.update { state ->
                    state.copy(
                        timeline = state.timeline.copy(
                            isLoading = false,
                            hasRecoverableError = true,
                        ),
                    )
                }
            }.collect { entries ->
                mutableUiState.update { state ->
                    state.copy(
                        timeline = state.timeline.copy(
                            isLoading = false,
                            entries = entries,
                            hasRecoverableError = false,
                        ),
                    )
                }
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
        private const val rapidSessionDraftKey = "rapid-session-draft"

        fun factory(
            repository: AquariumRepository,
            accountCoordinator: AccountActivationCoordinator? = null,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    HomeViewModel(
                        aquariumRepository = repository,
                        accountCoordinator = accountCoordinator,
                        savedStateHandle = extras.createSavedStateHandle(),
                    ) as T
            }
    }
}

private fun RapidSessionUiState.toSnapshot() = RapidSessionDraftSnapshot(
    idempotencyKey = idempotencyKey,
    aquariumId = aquariumId,
    occurredAtEpochMillis = occurredAtEpochMillis,
    editingSessionId = editingSessionId,
    measurementInputs = measurementInputs,
    actions = actions.values.map {
        MaintenanceActionDraftSnapshot(
            type = it.type.name,
            quantity = it.quantity,
            unit = it.unit,
            product = it.product,
        )
    },
    editingAction = editingAction?.name,
    showObservation = showObservation,
    observation = observation,
    showIncident = showIncident,
    incident = incident,
)

private fun RapidSessionDraftSnapshot.toUiState(): RapidSessionUiState {
    val restoredActions = actions.mapNotNull { snapshot ->
        runCatching { MaintenanceActionType.valueOf(snapshot.type) }.getOrNull()?.let { type ->
            type to MaintenanceActionInput(
                type = type,
                quantity = snapshot.quantity,
                unit = snapshot.unit,
                product = snapshot.product,
            )
        }
    }.toMap()
    return RapidSessionUiState(
        idempotencyKey = idempotencyKey,
        aquariumId = aquariumId,
        occurredAtEpochMillis = occurredAtEpochMillis,
        editingSessionId = editingSessionId,
        measurementInputs = measurementInputs,
        actions = restoredActions,
        editingAction = editingAction?.let { name ->
            runCatching { MaintenanceActionType.valueOf(name) }.getOrNull()
        },
        showObservation = showObservation,
        observation = observation,
        showIncident = showIncident,
        incident = incident,
    )
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
