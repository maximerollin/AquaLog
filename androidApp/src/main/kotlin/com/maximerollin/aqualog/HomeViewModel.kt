package com.maximerollin.aqualog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumCreationAccess
import com.maximerollin.aqualog.shared.AccountActivationCoordinator
import com.maximerollin.aqualog.shared.AccountActivationResult
import com.maximerollin.aqualog.shared.AccountControlCoordinator
import com.maximerollin.aqualog.shared.AccountDeletionPhase
import com.maximerollin.aqualog.shared.AccountDeletionReauthenticationResult
import com.maximerollin.aqualog.shared.AccountControlFailure
import com.maximerollin.aqualog.shared.AccountControlResult
import com.maximerollin.aqualog.shared.AccountIdentity
import com.maximerollin.aqualog.shared.LocalCopyDecision
import com.maximerollin.aqualog.shared.PendingAccountDeletion
import com.maximerollin.aqualog.shared.AquariumRepository
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.AnalyticsSyncFailureCategory
import com.maximerollin.aqualog.shared.ANNUAL_PRO_PRODUCT_ID
import com.maximerollin.aqualog.shared.CrashReporter
import com.maximerollin.aqualog.shared.DisabledCrashReporter
import com.maximerollin.aqualog.shared.DisabledFeedbackGateway
import com.maximerollin.aqualog.shared.DisabledProductTelemetry
import com.maximerollin.aqualog.shared.EventType
import com.maximerollin.aqualog.shared.FeedbackCoordinator
import com.maximerollin.aqualog.shared.FeedbackGateway
import com.maximerollin.aqualog.shared.GraphPeriod
import com.maximerollin.aqualog.shared.MaintenanceActionInput
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.ParameterDefinitionDraft
import com.maximerollin.aqualog.shared.AnnualProOffer
import com.maximerollin.aqualog.shared.LifetimeProOffer
import com.maximerollin.aqualog.shared.LIFETIME_PRO_PRODUCT_ID
import com.maximerollin.aqualog.shared.ProEntitlement
import com.maximerollin.aqualog.shared.ProPurchaseCoordinator
import com.maximerollin.aqualog.shared.ProPurchaseOutcome
import com.maximerollin.aqualog.shared.PortableImportProblem
import com.maximerollin.aqualog.shared.PortableImportResult
import com.maximerollin.aqualog.shared.ProductEvent
import com.maximerollin.aqualog.shared.ProductTelemetry
import com.maximerollin.aqualog.shared.ProProduct
import com.maximerollin.aqualog.shared.PaywallSource
import com.maximerollin.aqualog.shared.RapidSessionContext
import com.maximerollin.aqualog.shared.RapidSessionInput
import com.maximerollin.aqualog.shared.RecordedSession
import com.maximerollin.aqualog.shared.ReviewCoordinator
import com.maximerollin.aqualog.shared.SessionEditInput
import com.maximerollin.aqualog.shared.SessionUsage
import com.maximerollin.aqualog.shared.SyncFailureCategory
import com.maximerollin.aqualog.shared.SyncLocalDataSource
import com.maximerollin.aqualog.shared.SyncOutcome
import com.maximerollin.aqualog.shared.SyncState
import com.maximerollin.aqualog.shared.SyncStatus
import com.maximerollin.aqualog.shared.TimelineFilter
import com.maximerollin.aqualog.shared.TimelineSession
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.TaskInput
import com.maximerollin.aqualog.shared.TaskUsage
import com.maximerollin.aqualog.shared.TaskOccurrence
import com.maximerollin.aqualog.shared.TaskRecurrence
import com.maximerollin.aqualog.shared.TaskResolution
import com.maximerollin.aqualog.shared.TaskResolutionInput
import com.maximerollin.aqualog.shared.TrendPeriod
import com.maximerollin.aqualog.shared.TrendSnapshot
import com.maximerollin.aqualog.shared.TrendWindow
import com.maximerollin.aqualog.shared.AdvancedTrendAnalysis
import com.maximerollin.aqualog.shared.AdvancedTrendAnalysisResult
import com.maximerollin.aqualog.shared.asTrendSnapshot
import com.maximerollin.aqualog.shared.TechnicalComponent
import com.maximerollin.aqualog.shared.TechnicalFailure
import com.maximerollin.aqualog.shared.TechnicalFailureKind
import com.maximerollin.aqualog.shared.OnboardingMilestone
import com.maximerollin.aqualog.shared.VoluntaryFeedback
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.evaluateMeasurement
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Serializable
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
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

enum class AccountInvitationPrompt {
    NONE,
    AFTER_SESSION_CONFIRMATION,
    REMINDER,
}

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

enum class HistorySection {
    CHRONOLOGY,
    TRENDS,
}

data class TimelineUiState(
    val isLoading: Boolean = false,
    val isOffline: Boolean = true,
    val entries: List<TimelineSession> = emptyList(),
    val resolvedTaskOccurrences: List<TaskOccurrence> = emptyList(),
    val period: TimelinePeriod = TimelinePeriod.ALL,
    val aquariumId: String? = null,
    val parameterDefinitionId: String? = null,
    val maintenanceActionType: MaintenanceActionType? = null,
    val selectedSessionId: String? = null,
    val hasRecoverableError: Boolean = false,
)

data class TaskUiState(
    val isOpen: Boolean = false,
    val title: String = "",
    val dueDate: String = "",
    val time: String = "",
    val recurrence: TaskRecurrence = TaskRecurrence.ONCE,
    val pendingOccurrences: List<TaskOccurrence> = emptyList(),
    val showValidationError: Boolean = false,
    val isSaving: Boolean = false,
    val batteryOptimizationActive: Boolean = false,
)

data class TrendsUiState(
    val isLoading: Boolean = false,
    val period: TrendPeriod = TrendPeriod.THIRTY_DAYS,
    val snapshot: TrendSnapshot? = null,
    val selectedParameterDefinitionId: String? = null,
    val hasRecoverableError: Boolean = false,
    val advancedEnabled: Boolean = false,
    val customStartDate: String = "",
    val customEndDate: String = "",
    val customDateError: Boolean = false,
    val selectedInterventionSessionId: String? = null,
    val advancedAnalysis: AdvancedTrendAnalysisResult? = null,
)

enum class ProPurchaseStatus {
    IDLE,
    AWAITING_PLAY_CONFIRMATION,
    PENDING,
    VERIFYING,
    ACTIVE,
    CANCELLED,
    ERROR,
}

enum class ProAuthenticationContinuation {
    START_PURCHASE,
    START_LIFETIME_PURCHASE,
    RESTORE_PURCHASE,
}

internal fun resumeProAuthenticationContinuation(
    continuation: ProAuthenticationContinuation?,
    startAnnualPurchase: () -> Unit,
    startLifetimePurchase: () -> Unit,
    restorePurchase: () -> Unit,
) {
    when (continuation) {
        ProAuthenticationContinuation.START_PURCHASE -> startAnnualPurchase()
        ProAuthenticationContinuation.START_LIFETIME_PURCHASE -> startLifetimePurchase()
        ProAuthenticationContinuation.RESTORE_PURCHASE -> restorePurchase()
        null -> Unit
    }
}

data class ProUiState(
    val showPaywall: Boolean = false,
    val openedFromOnboarding: Boolean = false,
    val isLoadingOffer: Boolean = false,
    val offer: AnnualProOffer? = null,
val lifetimeOffer: LifetimeProOffer? = null,
    val purchaseStatus: ProPurchaseStatus = ProPurchaseStatus.IDLE,
    val message: ProMessage? = null,
    val actionAfterAuthentication: ProAuthenticationContinuation? = null,
)

enum class ProMessage {
    PLAY_UNAVAILABLE, PURCHASE_PENDING, PURCHASE_CANCELLED, AUTHENTICATION_REQUIRED,
    ACTIVE, VERIFICATION_FAILED, SAVE_FAILED,
}

enum class PortableDataMessage {
    EXPORT_READY,
    IMPORT_COMPLETE,
    STORAGE_ERROR,
}

data class PortableDataUiState(
    val isWorking: Boolean = false,
    val message: PortableDataMessage? = null,
    val importProblem: PortableImportProblem? = null,
    val showImportConfirmation: Boolean = false,
)

data class FeedbackUiState(
    val isOpen: Boolean = false,
    val message: String = "",
    val wasSubmitted: Boolean = false,
    val isUnavailable: Boolean = false,
)

enum class AccountSettingsDialog { NONE, LOGOUT, DELETE, PRIVACY, SUPPORT }

data class AccountSettingsUiState(
    val identity: AccountIdentity? = null,
    val pendingDeletion: PendingAccountDeletion? = null,
    val isWorking: Boolean = false,
    val dialog: AccountSettingsDialog = AccountSettingsDialog.NONE,
    val failure: AccountControlFailure? = null,
    val safeDetail: String? = null,
    val safeMessage: AccountSafeMessage? = null,
    val requiresDeletionReauthentication: Boolean = false,
) {
    val showDeletionReauthenticationAction: Boolean
        get() = pendingDeletion?.phase == AccountDeletionPhase.CLOUD_PENDING && requiresDeletionReauthentication
}

enum class AccountSafeMessage { SIGN_OUT_FAILED, DELETION_RESUME_FAILED, AUTHENTICATION_LINK_FAILED }

internal enum class AuthenticationCallbackTarget { ACCOUNT_ACTIVATION, ACCOUNT_DELETION }

internal suspend fun resolveAuthenticationCallbackTarget(
    loadPendingDeletion: suspend () -> PendingAccountDeletion?,
): AuthenticationCallbackTarget =
    if (loadPendingDeletion()?.phase == AccountDeletionPhase.CLOUD_PENDING) {
        AuthenticationCallbackTarget.ACCOUNT_DELETION
    } else {
        AuthenticationCallbackTarget.ACCOUNT_ACTIVATION
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
    val aquariums: List<Aquarium> = emptyList(),
    val aquariumEditor: AquariumEditorUiState? = null,
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
    val accountInvitationPrompt: AccountInvitationPrompt = AccountInvitationPrompt.NONE,
    val account: AccountUiState = AccountUiState(),
    val sessionDetail: RecordedSession? = null,
    val sessionDetailReturnsToTimeline: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val destination: MainDestination = MainDestination.HOME,
    val historySection: HistorySection = HistorySection.CHRONOLOGY,
    val timeline: TimelineUiState = TimelineUiState(),
    val trends: TrendsUiState = TrendsUiState(),
    val pro: ProUiState = ProUiState(),
    val syncState: SyncState = SyncState(SyncStatus.SYNCED, 0),
    val tasks: TaskUiState = TaskUiState(),
    val portableData: PortableDataUiState = PortableDataUiState(),
    val feedback: FeedbackUiState = FeedbackUiState(),
    val accountSettings: AccountSettingsUiState = AccountSettingsUiState(),
)

data class AquariumEditorUiState(
    val aquariumId: String? = null,
    val name: String = "",
    val volume: String = "",
    val volumeUnit: VolumeUnit = VolumeUnit.LITERS,
    val isSaving: Boolean = false,
    val showValidationError: Boolean = false,
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
    private val taskReminderScheduler: TaskReminderScheduler = NoOpTaskReminderScheduler,
    private val accountCoordinator: AccountActivationCoordinator? = null,
    private val accountControlCoordinator: AccountControlCoordinator? = null,
    private val proPurchaseCoordinator: ProPurchaseCoordinator? = null,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val currentTimeZoneId: () -> String = { ZoneId.systemDefault().id },
    private val generateInteractionId: () -> String = { UUID.randomUUID().toString() },
    private val onSyncRetryRequested: () -> Unit = {},
    private val productTelemetry: ProductTelemetry = DisabledProductTelemetry,
    private val crashReporter: CrashReporter = DisabledCrashReporter,
    feedbackGateway: FeedbackGateway = DisabledFeedbackGateway,
    private val reviewCoordinator: ReviewCoordinator? = null,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val feedbackCoordinator = FeedbackCoordinator(feedbackGateway)
    private val mutableUiState = MutableStateFlow(
        HomeUiState(
            rapidSession = savedStateHandle
                .get<RapidSessionDraftSnapshot>(rapidSessionDraftKey)
                ?.toUiState(),
        ),
    )
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()
    private var timelineJob: Job? = null
    private var trendsJob: Job? = null
    private var lastObservedSyncState: SyncState? = null

    init {
        viewModelScope.launch {
            aquariumRepository.observeAquariums().collect { aquariums ->
                mutableUiState.update { it.copy(aquariums = aquariums) }
            }
        }
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
        viewModelScope.launch {
            aquariumRepository.observePendingTaskOccurrences().collectLatest { occurrences ->
                taskReminderScheduler.reconcile(occurrences)
                mutableUiState.update { state ->
                    state.copy(tasks = state.tasks.copy(pendingOccurrences = occurrences))
                }
            }
        }
        viewModelScope.launch {
            aquariumRepository.observeResolvedTaskOccurrences().collectLatest { occurrences ->
                mutableUiState.update { state ->
                    state.copy(
                        timeline = state.timeline.copy(resolvedTaskOccurrences = occurrences),
                    )
                }
            }
        }
        if (accountCoordinator != null) {
            viewModelScope.launch {
                when {
                    accountCoordinator.hasPendingMigration() -> {
                        updateAccount { it.copy(step = AccountStep.ERROR, error = AccountError.MIGRATION) }
                    }
                    accountCoordinator.shouldInviteToAccount() -> {
                        mutableUiState.update {
                            it.copy(accountInvitationPrompt = AccountInvitationPrompt.REMINDER)
                        }
                    }
                }
            }
        }
        if (accountControlCoordinator != null) {
            refreshAccountSettings()
        }
        if (proPurchaseCoordinator != null) {
            viewModelScope.launch { refreshProEntitlement() }
            viewModelScope.launch {
                proPurchaseCoordinator.purchaseUpdates.collect { update ->
                    mutableUiState.update { state ->
                        state.copy(pro = state.pro.copy(purchaseStatus = ProPurchaseStatus.VERIFYING, message = null))
                    }
                    val outcome = proPurchaseCoordinator.resolve(update)
                    if (outcome is ProPurchaseOutcome.Entitled) outcome.entitlement.analyticsProductOrNull()?.let {
                        recordProductEvent(ProductEvent.PurchaseConfirmed(it))
                    }
                    handleProOutcome(outcome, continuation = null)
                }
            }
        }
        (aquariumRepository as? SyncLocalDataSource)?.let { syncLocalData ->
            viewModelScope.launch {
                syncLocalData.observeSyncState().collect { syncState ->
                    val previous = lastObservedSyncState
                    if (previous != null && previous != syncState) {
                        when (syncState.status) {
                            SyncStatus.SYNCED -> if (previous.status != SyncStatus.SYNCED) {
                                recordProductEvent(ProductEvent.SyncFinished(SyncOutcome.SUCCEEDED))
                            }
                            SyncStatus.FAILED -> {
                                val category = syncState.failure?.category.toAnalyticsCategory()
                                recordProductEvent(ProductEvent.SyncFinished(SyncOutcome.FAILED, category))
                                runCatching {
                                    crashReporter.record(
                                        TechnicalFailure(
                                            kind = TechnicalFailureKind.NON_FATAL,
                                            component = TechnicalComponent.SYNC,
                                            syncCategory = category,
                                        ),
                                    )
                                }
                            }
                            SyncStatus.PENDING -> Unit
                        }
                    }
                    lastObservedSyncState = syncState
                    mutableUiState.update { it.copy(syncState = syncState) }
                }
            }
        }
    }

    fun startOnboarding() {
        recordProductEvent(ProductEvent.Onboarding(OnboardingMilestone.STARTED))
        moveTo(OnboardingStep.PROFILE)
    }

    fun retrySynchronization() {
        val syncLocalData = aquariumRepository as? SyncLocalDataSource ?: return
        viewModelScope.launch {
            syncLocalData.retrySyncNow()
            onSyncRetryRequested()
        }
    }

    fun selectAquarium(aquariumId: String) {
        viewModelScope.launch {
            aquariumRepository.selectAquarium(aquariumId)
            activateAquariumContext(aquariumId)
        }
    }

    fun openCreateAquarium() {
        val state = mutableUiState.value
        if (state.aquariums.isNotEmpty() && state.pro.purchaseStatus != ProPurchaseStatus.ACTIVE) {
            openProPaywall()
            return
        }
        mutableUiState.update { it.copy(aquariumEditor = AquariumEditorUiState()) }
    }

    fun openEditAquarium() {
        val aquarium = mutableUiState.value.setup?.aquarium ?: return
        mutableUiState.update {
            it.copy(
                aquariumEditor = AquariumEditorUiState(
                    aquariumId = aquarium.id,
                    name = aquarium.name,
                    volume = aquarium.volume.displayInput(),
                    volumeUnit = aquarium.volumeUnit,
                ),
            )
        }
    }

    fun dismissAquariumEditor() {
        mutableUiState.update { it.copy(aquariumEditor = null) }
    }

    fun updateAquariumEditorName(value: String) = updateAquariumEditor {
        it.copy(name = value, showValidationError = false)
    }

    fun updateAquariumEditorVolume(value: String) = updateAquariumEditor {
        it.copy(volume = value, showValidationError = false)
    }

    fun updateAquariumEditorUnit(value: VolumeUnit) = updateAquariumEditor {
        it.copy(volumeUnit = value, showValidationError = false)
    }

    fun saveAquariumEditor() {
        val editor = mutableUiState.value.aquariumEditor ?: return
        val volume = editor.volume.toDecimalOrNull()
        if (editor.name.isBlank() || volume == null || volume <= 0.0 || !volume.isFinite()) {
            updateAquariumEditor { it.copy(showValidationError = true) }
            return
        }
        updateAquariumEditor { it.copy(isSaving = true, showValidationError = false) }
        viewModelScope.launch {
            runCatching {
                val aquariumId = editor.aquariumId
                if (aquariumId == null) {
                    val access = if (mutableUiState.value.pro.purchaseStatus == ProPurchaseStatus.ACTIVE) {
                        AquariumCreationAccess.PRO
                    } else {
                        AquariumCreationAccess.FREE
                    }
                    aquariumRepository.createConfiguredAquarium(
                        name = editor.name,
                        volume = volume,
                        volumeUnit = editor.volumeUnit,
                        profile = AquariumProfile.ESTABLISHED,
                        parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
                        access = access,
                    ).aquarium.id.also { aquariumRepository.selectAquarium(it) }
                } else {
                    val existing = requireNotNull(mutableUiState.value.aquariums.firstOrNull { it.id == aquariumId })
                    aquariumRepository.updateAquarium(
                        existing.copy(
                            name = editor.name,
                            volume = volume,
                            volumeUnit = editor.volumeUnit,
                        ),
                    )
                    aquariumId
                }
            }.onSuccess {
                mutableUiState.update { state -> state.copy(aquariumEditor = null) }
            }.onFailure {
                updateAquariumEditor { current ->
                    current.copy(isSaving = false, showValidationError = true)
                }
            }
        }
    }

    fun archiveCurrentAquarium() {
        val state = mutableUiState.value
        val aquarium = state.setup?.aquarium ?: return
        if (state.aquariums.size <= 1 || state.pro.purchaseStatus != ProPurchaseStatus.ACTIVE) return
        viewModelScope.launch {
            aquariumRepository.archiveAquarium(aquarium.id)
            aquariumRepository.observeCurrentAquarium().first()?.let(::activateAquariumContext)
        }
    }

    fun startPortableDataOperation() {
        mutableUiState.update { it.copy(portableData = PortableDataUiState(isWorking = true)) }
    }

    fun requestPortableImport() {
        mutableUiState.update { state ->
            state.copy(
                portableData = state.portableData.copy(showImportConfirmation = true),
            )
        }
    }

    fun dismissPortableImportConfirmation() {
        mutableUiState.update { state ->
            state.copy(
                portableData = state.portableData.copy(showImportConfirmation = false),
            )
        }
    }

    fun cancelPortableDataOperation() {
        mutableUiState.update { it.copy(portableData = PortableDataUiState()) }
    }

    fun completePortableExport() {
        recordProductEvent(ProductEvent.ExportCompleted)
        mutableUiState.update {
            it.copy(portableData = PortableDataUiState(message = PortableDataMessage.EXPORT_READY))
        }
    }

    fun completePortableImport(result: PortableImportResult) {
        val portableData = when (result) {
            is PortableImportResult.Imported -> {
                PortableDataUiState(message = PortableDataMessage.IMPORT_COMPLETE)
            }
            is PortableImportResult.Rejected -> {
                PortableDataUiState(importProblem = result.problem)
            }
        }
        mutableUiState.update { it.copy(portableData = portableData) }
    }

    fun failPortableDataAccess() {
        mutableUiState.update {
            it.copy(portableData = PortableDataUiState(message = PortableDataMessage.STORAGE_ERROR))
        }
    }

    fun openFeedback() {
        mutableUiState.update { it.copy(feedback = FeedbackUiState(isOpen = true)) }
    }

    fun dismissFeedback() {
        mutableUiState.update { it.copy(feedback = FeedbackUiState()) }
    }

    fun updateFeedback(value: String) {
        mutableUiState.update { state ->
            state.copy(
                feedback = state.feedback.copy(
                    message = value.take(2_000),
                    wasSubmitted = false,
                    isUnavailable = false,
                ),
            )
        }
    }

    fun submitFeedback() {
        val message = mutableUiState.value.feedback.message
        if (message.isBlank()) return
        viewModelScope.launch {
            val delivered = runCatching {
                feedbackCoordinator.submit(VoluntaryFeedback(message))
            }.getOrDefault(false)
            mutableUiState.update { state ->
                state.copy(
                    feedback = state.feedback.copy(
                        message = if (delivered) "" else state.feedback.message,
                        wasSubmitted = delivered,
                        isUnavailable = !delivered,
                    ),
                )
            }
        }
    }

    fun showAccountSettingsDialog(dialog: AccountSettingsDialog) {
        mutableUiState.update { state ->
            state.copy(accountSettings = state.accountSettings.copy(dialog = dialog))
        }
    }

    fun dismissAccountSettingsDialog() = showAccountSettingsDialog(AccountSettingsDialog.NONE)

    fun logout(localCopyDecision: LocalCopyDecision) {
        val coordinator = accountControlCoordinator ?: return
        mutableUiState.update { state ->
            state.copy(accountSettings = state.accountSettings.copy(isWorking = true, dialog = AccountSettingsDialog.NONE))
        }
        viewModelScope.launch {
            runCatching { coordinator.logout(localCopyDecision) }
                .onSuccess { result ->
                    if (result is AccountControlResult.SignOutIncomplete) {
                        mutableUiState.update { state ->
                            state.copy(
                                accountSettings = state.accountSettings.copy(
                                    isWorking = false,
                                    failure = result.failure,
                                    safeDetail = result.safeDetail,
                                    safeMessage = null,
                                ),
                            )
                        }
                    } else {
                        refreshAccountSettingsState()
                    }
                }
                .onFailure {
                    mutableUiState.update { state ->
                        state.copy(
                            accountSettings = state.accountSettings.copy(
                                isWorking = false,
                                failure = AccountControlFailure.LOCAL_DATA,
                                safeMessage = AccountSafeMessage.SIGN_OUT_FAILED,
                            ),
                        )
                    }
                }
        }
    }

    fun deleteAccount() {
        val coordinator = accountControlCoordinator ?: return
        mutableUiState.update { state ->
            state.copy(accountSettings = state.accountSettings.copy(isWorking = true, dialog = AccountSettingsDialog.NONE))
        }
        viewModelScope.launch {
            val result = runCatching { coordinator.deleteAccount() }.getOrElse {
                mutableUiState.update { state ->
                    state.copy(
                        accountSettings = state.accountSettings.copy(
                            isWorking = false,
                            failure = AccountControlFailure.LOCAL_DATA,
                            safeMessage = AccountSafeMessage.DELETION_RESUME_FAILED,
                        ),
                    )
                }
                return@launch
            }
            when (result) {
                AccountControlResult.Deleted -> refreshAccountSettingsState()
                is AccountControlResult.SignOutIncomplete -> Unit
                is AccountControlResult.DeletionIncomplete -> {
                    val pending = coordinator.pendingDeletion()
                    val requiresReauthentication = coordinator.deletionRequiresReauthentication()
                    mutableUiState.update { state ->
                        state.copy(
                            accountSettings = state.accountSettings.copy(
                                pendingDeletion = pending,
                                isWorking = false,
                                failure = result.failure,
                                safeDetail = result.safeDetail,
                                safeMessage = null,
                                requiresDeletionReauthentication = requiresReauthentication,
                            ),
                        )
                    }
                }
                is AccountControlResult.SignedOut -> Unit
            }
        }
    }

    fun retryAccountDeletion() = deleteAccount()

    fun reauthenticateAccountDeletionWithGoogle() = launchDeletionReauthentication {
        requireNotNull(accountControlCoordinator).reauthenticateDeletionWithGoogle()
    }

    fun requestDeletionMagicLink() = launchDeletionReauthentication {
        requireNotNull(accountControlCoordinator).requestDeletionMagicLink()
    }

    private fun launchDeletionReauthentication(
        action: suspend () -> AccountDeletionReauthenticationResult,
    ) {
        if (accountControlCoordinator == null) return
        mutableUiState.update { state ->
            state.copy(accountSettings = state.accountSettings.copy(isWorking = true, safeDetail = null, safeMessage = null))
        }
        viewModelScope.launch {
            handleDeletionReauthentication(
                runCatching { action() }.getOrElse {
                    AccountDeletionReauthenticationResult.Failed("Account authentication could not be completed.")
                },
            )
        }
    }

    private suspend fun handleDeletionReauthentication(result: AccountDeletionReauthenticationResult) {
        when (result) {
            is AccountDeletionReauthenticationResult.Resumed -> when (val deletion = result.deletionResult) {
                AccountControlResult.Deleted -> refreshAccountSettingsState()
                is AccountControlResult.DeletionIncomplete -> {
                    val coordinator = requireNotNull(accountControlCoordinator)
                    val pending = coordinator.pendingDeletion()
                    val requiresReauthentication = coordinator.deletionRequiresReauthentication()
                    mutableUiState.update { state ->
                        state.copy(
                            accountSettings = state.accountSettings.copy(
                                pendingDeletion = pending,
                                isWorking = false,
                                failure = deletion.failure,
                                safeDetail = deletion.safeDetail,
                                safeMessage = null,
                                requiresDeletionReauthentication = requiresReauthentication,
                            ),
                        )
                    }
                }
                is AccountControlResult.SignedOut,
                is AccountControlResult.SignOutIncomplete,
                -> updateDeletionAuthenticationMessage("Account deletion could not be resumed.")
            }
            AccountDeletionReauthenticationResult.AwaitingAuthentication -> updateDeletionAuthenticationMessage(
                "Finish authentication in your browser to resume account deletion.",
            )
            AccountDeletionReauthenticationResult.MagicLinkSent -> updateDeletionAuthenticationMessage(
                "Open the sign-in link sent to your account email to resume deletion.",
            )
            AccountDeletionReauthenticationResult.Cancelled -> updateDeletionAuthenticationMessage(
                "Authentication was cancelled. Account deletion is still pending.",
            )
            AccountDeletionReauthenticationResult.ExpiredLink -> updateDeletionAuthenticationMessage(
                "This sign-in link has expired. Request a new link to resume deletion.",
            )
            AccountDeletionReauthenticationResult.IgnoredCallback -> updateDeletionAuthenticationMessage(
                "This sign-in link does not match the pending deletion.",
            )
            is AccountDeletionReauthenticationResult.WrongAccount -> updateDeletionAuthenticationMessage(
                result.expectedEmail?.let { "Sign in as $it to resume deletion." }
                    ?: "Sign in with the account awaiting deletion.",
            )
            is AccountDeletionReauthenticationResult.Failed -> updateDeletionAuthenticationMessage(result.safeDetail)
        }
    }

    private fun updateDeletionAuthenticationMessage(message: String) {
        mutableUiState.update { state ->
            state.copy(
                accountSettings = state.accountSettings.copy(
                    isWorking = false,
                    safeDetail = message,
                    safeMessage = null,
                ),
            )
        }
    }

    private fun refreshAccountSettings() {
        viewModelScope.launch { refreshAccountSettingsState() }
    }

    private suspend fun refreshAccountSettingsState() {
        val coordinator = accountControlCoordinator ?: return
        val identity = coordinator.identity()
        val pending = coordinator.pendingDeletion()
        val requiresReauthentication = coordinator.deletionRequiresReauthentication()
        mutableUiState.update { state ->
            state.copy(
                accountSettings = AccountSettingsUiState(
                    identity = identity,
                    pendingDeletion = pending,
                    requiresDeletionReauthentication = requiresReauthentication,
                ),
            )
        }
    }

    fun selectProfile(profile: AquariumProfile) {
        recordProductEvent(ProductEvent.Onboarding(OnboardingMilestone.PROFILE_SELECTED))
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

    fun continueToPaywall() {
        recordProductEvent(ProductEvent.Onboarding(OnboardingMilestone.PRACTICE_COMPLETED))
        recordProductEvent(ProductEvent.PaywallViewed(PaywallSource.ONBOARDING))
        moveTo(OnboardingStep.PAYWALL)
        mutableUiState.update {
            it.copy(pro = it.pro.copy(showPaywall = true, openedFromOnboarding = true))
        }
        loadProOffer()
    }

    fun openProPaywall() {
        recordProductEvent(ProductEvent.PaywallViewed(PaywallSource.SETTINGS))
        mutableUiState.update {
            it.copy(pro = it.pro.copy(showPaywall = true, openedFromOnboarding = false))
        }
        loadProOffer()
    }

    fun closeProPaywall() {
        if (mutableUiState.value.setup == null) return
        mutableUiState.update {
            it.copy(pro = it.pro.copy(showPaywall = false, openedFromOnboarding = false))
        }
    }

    fun loadProOffer() {
        val coordinator = proPurchaseCoordinator ?: run {
            mutableUiState.update {
                it.copy(pro = it.pro.copy(isLoadingOffer = false, purchaseStatus = ProPurchaseStatus.ERROR, message = ProMessage.PLAY_UNAVAILABLE))
            }
            return
        }
        mutableUiState.update { it.copy(pro = it.pro.copy(isLoadingOffer = true, message = null)) }
        viewModelScope.launch {
            val lifetimeOffer = runCatching { coordinator.loadLifetimeOffer() }
            val annualOffer = runCatching { coordinator.loadOffer() }.getOrNull()
            lifetimeOffer
                .onSuccess { offer ->
                    mutableUiState.update { state ->
                        state.copy(
                            pro = state.pro.copy(
                                isLoadingOffer = false,
                                offer = annualOffer,
                                lifetimeOffer = offer,
                                purchaseStatus = if (state.pro.purchaseStatus == ProPurchaseStatus.ACTIVE) {
                                    ProPurchaseStatus.ACTIVE
                                } else {
                                    ProPurchaseStatus.IDLE
                                },
                            ),
                        )
                    }
                }
                .onFailure {
                    mutableUiState.update { state ->
                        state.copy(
                            pro = state.pro.copy(
                                isLoadingOffer = false,
                                purchaseStatus = if (state.pro.purchaseStatus == ProPurchaseStatus.ACTIVE) {
                                    ProPurchaseStatus.ACTIVE
                                } else {
                                    ProPurchaseStatus.ERROR
                                },
                                message = ProMessage.PLAY_UNAVAILABLE,
                            ),
                        )
                    }
                }
        }
    }

    fun startProPurchase() {
        val coordinator = proPurchaseCoordinator ?: return
        if (
            mutableUiState.value.pro.purchaseStatus in setOf(
                ProPurchaseStatus.AWAITING_PLAY_CONFIRMATION,
                ProPurchaseStatus.PENDING,
                ProPurchaseStatus.VERIFYING,
                ProPurchaseStatus.ACTIVE,
            )
        ) return
        updatePro(ProPurchaseStatus.VERIFYING)
        viewModelScope.launch {
            handleProOutcome(
                coordinator.startPurchase(),
                continuation = ProAuthenticationContinuation.START_PURCHASE,
            )
        }
    }

    fun startLifetimeProPurchase() {
        val coordinator = proPurchaseCoordinator ?: return
        if (
            mutableUiState.value.pro.purchaseStatus in setOf(
                ProPurchaseStatus.AWAITING_PLAY_CONFIRMATION,
                ProPurchaseStatus.PENDING,
                ProPurchaseStatus.VERIFYING,
                ProPurchaseStatus.ACTIVE,
            )
        ) return
        updatePro(ProPurchaseStatus.VERIFYING)
        viewModelScope.launch {
            handleProOutcome(
                coordinator.startLifetimePurchase(),
                continuation = ProAuthenticationContinuation.START_LIFETIME_PURCHASE,
            )
        }
    }

    fun restoreProPurchase() {
        val coordinator = proPurchaseCoordinator ?: return
        if (mutableUiState.value.pro.purchaseStatus == ProPurchaseStatus.VERIFYING) return
        updatePro(ProPurchaseStatus.VERIFYING)
        viewModelScope.launch {
            handleProOutcome(
                coordinator.restorePurchase(),
                continuation = ProAuthenticationContinuation.RESTORE_PURCHASE,
            )
        }
    }

    fun finishForFree() {
        viewModelScope.launch { persistOnboardingAquarium(keepPaywallOpen = false) }
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

    fun openTasks() {
        val today = LocalDate.now(ZoneId.of(currentTimeZoneId()))
        mutableUiState.update { state ->
            state.copy(
                tasks = state.tasks.copy(
                    isOpen = true,
                    dueDate = state.tasks.dueDate.ifBlank { today.toString() },
                    batteryOptimizationActive = taskReminderScheduler.isBatteryOptimizationActive(),
                ),
            )
        }
    }

    fun closeTasks() {
        mutableUiState.update { it.copy(tasks = it.tasks.copy(isOpen = false)) }
    }

    fun updateTaskTitle(value: String) = updateTasks { it.copy(title = value, showValidationError = false) }

    fun updateTaskDueDate(value: String) = updateTasks { it.copy(dueDate = value, showValidationError = false) }

    fun updateTaskTime(value: String) = updateTasks { it.copy(time = value, showValidationError = false) }

    fun selectTaskRecurrence(value: TaskRecurrence) = updateTasks {
        it.copy(recurrence = value, showValidationError = false)
    }

    fun createTask() {
        val aquariumId = mutableUiState.value.setup?.aquarium?.id ?: return
        val form = mutableUiState.value.tasks
        val date = runCatching { LocalDate.parse(form.dueDate.trim()) }.getOrNull()
        val time = form.time.trim().takeIf(String::isNotEmpty)?.let {
            runCatching { LocalTime.parse(it) }.getOrNull()
        }
        if (form.title.isBlank() || date == null || (form.time.isNotBlank() && time == null)) {
            updateTasks { it.copy(showValidationError = true) }
            return
        }
        updateTasks { it.copy(isSaving = true, showValidationError = false) }
        viewModelScope.launch {
            runCatching {
                aquariumRepository.createTask(
                    TaskInput(
                        aquariumId = aquariumId,
                        title = form.title,
                        recurrence = form.recurrence,
                        firstDueDate = TaskDate(date.year, date.monthValue, date.dayOfMonth),
                        minuteOfDay = time?.let { it.hour * 60 + it.minute },
                        timeZoneId = currentTimeZoneId(),
                    ),
                )
            }.onSuccess { created ->
                taskReminderScheduler.schedule(created.occurrence)
                recordProductEvent(ProductEvent.TaskUsed(TaskUsage.CREATED))
                updateTasks {
                    it.copy(title = "", time = "", isSaving = false, showValidationError = false)
                }
            }.onFailure {
                updateTasks { it.copy(isSaving = false, showValidationError = true) }
            }
        }
    }

    fun resolveTask(occurrence: TaskOccurrence, resolution: TaskResolution) {
        viewModelScope.launch {
            val postponed = if (resolution == TaskResolution.POSTPONED) {
                LocalDate.of(
                    occurrence.dueDate.year,
                    occurrence.dueDate.month,
                    occurrence.dueDate.day,
                ).plusDays(1)
            } else {
                null
            }
            runCatching {
                aquariumRepository.resolveTaskOccurrence(
                    TaskResolutionInput(
                        occurrenceId = occurrence.id,
                        resolution = resolution,
                        resolvedAtEpochMillis = currentTimeMillis(),
                        postponedUntil = postponed?.let {
                            TaskDate(it.year, it.monthValue, it.dayOfMonth)
                        },
                        postponedMinuteOfDay = occurrence.minuteOfDay,
                        postponedTimeZoneId = occurrence.timeZoneId,
                    ),
                )
            }.onSuccess { result ->
                taskReminderScheduler.cancel(occurrence.id)
                result.nextOccurrence?.let(taskReminderScheduler::schedule)
                recordProductEvent(
                    ProductEvent.TaskUsed(
                        when (resolution) {
                            TaskResolution.COMPLETED -> TaskUsage.COMPLETED
                            TaskResolution.POSTPONED -> TaskUsage.POSTPONED
                            TaskResolution.IGNORED -> TaskUsage.SKIPPED
                        },
                    ),
                )
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
        if (destination == MainDestination.HISTORY) {
            if (timelineJob == null) observeTimeline()
            if (mutableUiState.value.historySection == HistorySection.TRENDS) observeTrends()
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

    fun selectHistorySection(section: HistorySection) {
        mutableUiState.update { it.copy(historySection = section) }
        if (section == HistorySection.TRENDS) {
            recordProductEvent(ProductEvent.GraphViewed(mutableUiState.value.trends.period.toGraphPeriod()))
            observeTrends()
        }
    }

    fun selectTrendPeriod(period: TrendPeriod) {
        recordProductEvent(ProductEvent.GraphViewed(period.toGraphPeriod()))
        mutableUiState.update { state ->
            state.copy(trends = state.trends.copy(period = period, advancedEnabled = false))
        }
        observeTrends()
    }

    fun selectTrendParameter(parameterDefinitionId: String) {
        mutableUiState.update { state ->
            state.copy(
                trends = state.trends.copy(selectedParameterDefinitionId = parameterDefinitionId),
            )
        }
    }

    fun showCompleteProHistory() {
        if (mutableUiState.value.pro.purchaseStatus != ProPurchaseStatus.ACTIVE) {
            openProPaywall()
            return
        }
        mutableUiState.update { state ->
            state.copy(
                trends = state.trends.copy(
                    advancedEnabled = true,
                    customStartDate = "",
                    customEndDate = "",
                    customDateError = false,
                ),
            )
        }
        observeTrends()
    }

    fun updateTrendCustomStart(value: String) = mutableUiState.update { state ->
        state.copy(trends = state.trends.copy(customStartDate = value, customDateError = false))
    }

    fun updateTrendCustomEnd(value: String) = mutableUiState.update { state ->
        state.copy(trends = state.trends.copy(customEndDate = value, customDateError = false))
    }

    fun applyTrendCustomPeriod() {
        val trends = mutableUiState.value.trends
        val window = trends.customWindowOrNull()
        if (mutableUiState.value.pro.purchaseStatus != ProPurchaseStatus.ACTIVE || window == null) {
            mutableUiState.update { state ->
                state.copy(trends = state.trends.copy(customDateError = true))
            }
            return
        }
        mutableUiState.update { state ->
            state.copy(trends = state.trends.copy(advancedEnabled = true, customDateError = false))
        }
        observeTrends()
    }

    fun selectTrendIntervention(sessionId: String) {
        mutableUiState.update { state ->
            val analysis = state.trends.advancedAnalysis?.snapshot?.let { snapshot ->
                AdvancedTrendAnalysis.create(snapshot, sessionId)
            }
            state.copy(
                trends = state.trends.copy(
                    selectedInterventionSessionId = sessionId,
                    advancedAnalysis = analysis,
                ),
            )
        }
    }

    fun retryTrends() = observeTrends()

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
        val isFirstSession = draft.editingSessionId == null && mutableUiState.value.latestSession == null
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
                if (
                    draft.editingSessionId == null &&
                    accountCoordinator?.shouldInviteToAccount() == true
                ) {
                    mutableUiState.update {
                        it.copy(accountInvitationPrompt = AccountInvitationPrompt.AFTER_SESSION_CONFIRMATION)
                    }
                }
                recordProductEvent(
                    ProductEvent.SessionSaved(
                        if (draft.editingSessionId == null) SessionUsage.CREATED else SessionUsage.EDITED,
                    ),
                )
                if (isFirstSession) recordProductEvent(ProductEvent.FirstSessionSaved)
                if (draft.editingSessionId == null) reviewCoordinator?.onSuccessfulSession()
            }.onFailure {
                updateRapidSession { it.copy(isSaving = false, saveError = true) }
            }
        }
    }

    fun continueAfterSessionConfirmation() {
        val coordinator = accountCoordinator ?: return
        val prompt = mutableUiState.value.accountInvitationPrompt
        if (prompt == AccountInvitationPrompt.NONE) return
        mutableUiState.update { it.copy(accountInvitationPrompt = AccountInvitationPrompt.NONE) }
        viewModelScope.launch {
            runCatching { coordinator.markInvitationOffered() }
                .onSuccess {
                    mutableUiState.update {
                        it.copy(account = AccountUiState(AccountStep.INVITATION))
                    }
                }
                .onFailure {
                    mutableUiState.update { it.copy(accountInvitationPrompt = prompt) }
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

    fun completeAccountCallback(callbackUrl: String) {
        viewModelScope.launch {
            val target = runCatching {
                resolveAuthenticationCallbackTarget { accountControlCoordinator?.pendingDeletion() }
            }.getOrElse {
                mutableUiState.update { state ->
                    state.copy(
                        accountSettings = state.accountSettings.copy(
                            isWorking = false,
                            failure = AccountControlFailure.LOCAL_DATA,
                            safeMessage = AccountSafeMessage.AUTHENTICATION_LINK_FAILED,
                        ),
                    )
                }
                return@launch
            }
            when (target) {
                AuthenticationCallbackTarget.ACCOUNT_DELETION -> launchDeletionReauthentication {
                    requireNotNull(accountControlCoordinator).completeDeletionReauthentication(callbackUrl)
                }
                AuthenticationCallbackTarget.ACCOUNT_ACTIVATION -> launchAccountAction(AccountError.AUTHENTICATION) {
                    requireNotNull(accountCoordinator).completeMagicLink(callbackUrl)
                }
            }
        }
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
                AccountActivationResult.ReauthenticationRequired -> AccountUiState(AccountStep.METHODS)
                is AccountActivationResult.Failed -> current.copy(step = AccountStep.ERROR, error = failure)
                is AccountActivationResult.MigrationFailed -> current.copy(
                    step = AccountStep.ERROR,
                    error = AccountError.MIGRATION,
                )
            }
        }
        if (result == AccountActivationResult.Activated) {
            val continuation = mutableUiState.value.pro.actionAfterAuthentication
            viewModelScope.launch {
                refreshProEntitlement()
                resumeProAuthenticationContinuation(
                    continuation = continuation,
                    startAnnualPurchase = ::startProPurchase,
                    startLifetimePurchase = ::startLifetimeProPurchase,
                    restorePurchase = ::restoreProPurchase,
                )
            }
        }
    }

    private suspend fun handleProOutcome(
        outcome: ProPurchaseOutcome,
        continuation: ProAuthenticationContinuation?,
    ) {
        when (outcome) {
            ProPurchaseOutcome.AwaitingPlayConfirmation -> updatePro(ProPurchaseStatus.AWAITING_PLAY_CONFIRMATION)
            ProPurchaseOutcome.Pending -> updatePro(ProPurchaseStatus.PENDING, ProMessage.PURCHASE_PENDING)
            ProPurchaseOutcome.Cancelled -> updatePro(ProPurchaseStatus.CANCELLED, ProMessage.PURCHASE_CANCELLED)
            ProPurchaseOutcome.NotEntitled -> updatePro(ProPurchaseStatus.IDLE)
            ProPurchaseOutcome.AuthenticationRequired -> {
                mutableUiState.update { state ->
                    state.copy(
                        pro = state.pro.copy(
                            purchaseStatus = ProPurchaseStatus.ERROR,
                            message = ProMessage.AUTHENTICATION_REQUIRED,
                            actionAfterAuthentication = continuation,
                        ),
                    )
                }
                if (accountCoordinator != null && persistOnboardingAquarium(keepPaywallOpen = true)) {
                    updateAccount { it.copy(step = AccountStep.METHODS, error = null) }
                }
            }
            is ProPurchaseOutcome.Entitled -> {
                outcome.entitlement.analyticsProductOrNull()?.let {
                    recordProductEvent(ProductEvent.EntitlementConfirmed(active = true, product = it))
                }
                updatePro(ProPurchaseStatus.ACTIVE, ProMessage.ACTIVE)
            }
            is ProPurchaseOutcome.Failed -> updatePro(ProPurchaseStatus.ERROR, ProMessage.VERIFICATION_FAILED)
        }
    }

    private suspend fun refreshProEntitlement() {
        val coordinator = proPurchaseCoordinator ?: return
        when (val outcome = coordinator.refreshEntitlement()) {
            is ProPurchaseOutcome.Entitled -> {
                outcome.entitlement.analyticsProductOrNull()?.let {
                    recordProductEvent(ProductEvent.EntitlementConfirmed(active = true, product = it))
                }
                updatePro(ProPurchaseStatus.ACTIVE, ProMessage.ACTIVE)
            }
            ProPurchaseOutcome.NotEntitled -> {
                updatePro(ProPurchaseStatus.IDLE)
            }
            ProPurchaseOutcome.AuthenticationRequired -> updatePro(
                ProPurchaseStatus.ERROR,
                ProMessage.AUTHENTICATION_REQUIRED,
            )
            is ProPurchaseOutcome.Failed -> updatePro(
                ProPurchaseStatus.ERROR,
                ProMessage.VERIFICATION_FAILED,
            )
            else -> Unit
        }
    }

    private fun updatePro(status: ProPurchaseStatus, message: ProMessage? = null) {
        mutableUiState.update { state ->
            state.copy(
                pro = state.pro.copy(
                    purchaseStatus = status,
                    message = message,
                    actionAfterAuthentication = null,
                ),
            )
        }
        if (mutableUiState.value.historySection == HistorySection.TRENDS) observeTrends()
    }

    private suspend fun persistOnboardingAquarium(keepPaywallOpen: Boolean): Boolean {
        val state = mutableUiState.value
        if (state.setup != null) {
            if (!keepPaywallOpen) hideProPaywall()
            return true
        }
        if (state.isSaving) return false
        val volume = state.volumeInput.toDecimalOrNull() ?: return false
        val parameters = configuredParameters() ?: return false
        mutableUiState.update { it.copy(isSaving = true) }
        return runCatching {
            aquariumRepository.createConfiguredAquarium(
                name = state.aquariumName,
                volume = volume,
                volumeUnit = state.volumeUnit,
                profile = state.profile,
                parameters = parameters,
            )
        }.fold(
            onSuccess = {
                recordProductEvent(ProductEvent.Onboarding(OnboardingMilestone.COMPLETED))
                if (!keepPaywallOpen) hideProPaywall()
                true
            },
            onFailure = {
                mutableUiState.update { current ->
                    current.copy(
                        isSaving = false,
                        pro = current.pro.copy(
                            purchaseStatus = ProPurchaseStatus.ERROR,
                            message = ProMessage.SAVE_FAILED,
                        ),
                    )
                }
                false
            },
        )
    }

    private fun hideProPaywall() {
        mutableUiState.update { current ->
            current.copy(
                pro = current.pro.copy(
                    showPaywall = false,
                    openedFromOnboarding = false,
                ),
            )
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

    private fun updateTasks(transform: (TaskUiState) -> TaskUiState) {
        mutableUiState.update { it.copy(tasks = transform(it.tasks)) }
    }

    private fun updateAquariumEditor(transform: (AquariumEditorUiState) -> AquariumEditorUiState) {
        mutableUiState.update { state ->
            state.aquariumEditor?.let { state.copy(aquariumEditor = transform(it)) } ?: state
        }
    }

    private fun activateAquariumContext(aquarium: Aquarium) = activateAquariumContext(aquarium.id)

    private fun activateAquariumContext(aquariumId: String) {
        mutableUiState.update { state ->
            state.copy(
                timeline = state.timeline.copy(aquariumId = aquariumId),
                trends = state.trends.copy(selectedParameterDefinitionId = null),
            )
        }
        if (mutableUiState.value.destination == MainDestination.HISTORY) {
            observeTimeline()
            if (mutableUiState.value.historySection == HistorySection.TRENDS) {
                observeTrends(aquariumId)
            }
        }
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

    private fun observeTrends(aquariumId: String? = null) {
        val selectedAquariumId = aquariumId ?: mutableUiState.value.setup?.aquarium?.id ?: return
        trendsJob?.cancel()
        val period = mutableUiState.value.trends.period
        mutableUiState.update { state ->
            state.copy(
                trends = state.trends.copy(
                    isLoading = true,
                    hasRecoverableError = false,
                ),
            )
        }
        trendsJob = viewModelScope.launch {
            val trendsState = mutableUiState.value.trends
            val isProAdvanced = mutableUiState.value.pro.purchaseStatus == ProPurchaseStatus.ACTIVE &&
                trendsState.advancedEnabled
            if (isProAdvanced) {
                aquariumRepository.observeAdvancedTrends(
                    aquariumId = selectedAquariumId,
                    customWindow = trendsState.customWindowOrNull(),
                ).catch {
                    mutableUiState.update { state ->
                        state.copy(trends = state.trends.copy(isLoading = false, hasRecoverableError = true))
                    }
                }.collect { advanced ->
                    val selectedIntervention = mutableUiState.value.trends.selectedInterventionSessionId
                        ?.takeIf { id -> advanced.events.any { it.sessionId == id } }
                    val analysis = AdvancedTrendAnalysis.create(advanced, selectedIntervention)
                    mutableUiState.update { state ->
                        val selected = state.trends.selectedParameterDefinitionId
                            ?.takeIf { id -> advanced.series.any { it.definition.id == id } }
                            ?: advanced.series.firstOrNull()?.definition?.id
                        state.copy(
                            trends = state.trends.copy(
                                isLoading = false,
                                snapshot = advanced.asTrendSnapshot(period),
                                selectedParameterDefinitionId = selected,
                                selectedInterventionSessionId = selectedIntervention,
                                advancedAnalysis = analysis,
                                hasRecoverableError = false,
                            ),
                        )
                    }
                }
                return@launch
            }
            aquariumRepository.observeFreeTrends(selectedAquariumId, period)
                .catch {
                    mutableUiState.update { state ->
                        state.copy(
                            trends = state.trends.copy(
                                isLoading = false,
                                hasRecoverableError = true,
                            ),
                        )
                    }
                }
                .collect { snapshot ->
                    mutableUiState.update { state ->
                        val selected = state.trends.selectedParameterDefinitionId
                            ?.takeIf { id -> snapshot.series.any { it.definition.id == id } }
                            ?: snapshot.series.firstOrNull()?.definition?.id
                        state.copy(
                            trends = state.trends.copy(
                                isLoading = false,
                                snapshot = snapshot,
                                advancedEnabled = false,
                                advancedAnalysis = null,
                                selectedInterventionSessionId = null,
                                selectedParameterDefinitionId = selected,
                                hasRecoverableError = false,
                            ),
                        )
                    }
                }
        }
    }

    private fun TrendsUiState.customWindowOrNull(): TrendWindow? {
        if (customStartDate.isBlank() && customEndDate.isBlank()) return null
        return runCatching {
            val zone = ZoneId.of(currentTimeZoneId())
            val start = LocalDate.parse(customStartDate).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = LocalDate.parse(customEndDate).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
            TrendWindow(start, end)
        }.getOrNull()
    }

    private fun moveTo(step: OnboardingStep) {
        mutableUiState.update { it.copy(step = step) }
    }

    private fun recordProductEvent(event: ProductEvent) {
        runCatching { productTelemetry.record(event) }
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
            taskReminderScheduler: TaskReminderScheduler = NoOpTaskReminderScheduler,
            accountCoordinator: AccountActivationCoordinator? = null,
            accountControlCoordinator: AccountControlCoordinator? = null,
            proPurchaseCoordinator: ProPurchaseCoordinator? = null,
            productTelemetry: ProductTelemetry = DisabledProductTelemetry,
            crashReporter: CrashReporter = DisabledCrashReporter,
            feedbackGateway: FeedbackGateway = DisabledFeedbackGateway,
            reviewCoordinator: ReviewCoordinator? = null,
            onSyncRetryRequested: () -> Unit = {},
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    HomeViewModel(
                        aquariumRepository = repository,
                        taskReminderScheduler = taskReminderScheduler,
                        accountCoordinator = accountCoordinator,
                        accountControlCoordinator = accountControlCoordinator,
                        proPurchaseCoordinator = proPurchaseCoordinator,
                        productTelemetry = productTelemetry,
                        crashReporter = crashReporter,
                        feedbackGateway = feedbackGateway,
                        reviewCoordinator = reviewCoordinator,
                        onSyncRetryRequested = onSyncRetryRequested,
                        savedStateHandle = extras.createSavedStateHandle(),
                    ) as T
            }
    }
}

internal fun ProEntitlement.analyticsProductOrNull(): ProProduct? = when (productId) {
    ANNUAL_PRO_PRODUCT_ID -> ProProduct.ANNUAL
    LIFETIME_PRO_PRODUCT_ID -> ProProduct.LIFETIME
    else -> null
}

private object NoOpTaskReminderScheduler : TaskReminderScheduler {
    override fun schedule(occurrence: TaskOccurrence) = Unit
    override fun cancel(occurrenceId: String) = Unit
    override fun reconcile(occurrences: List<TaskOccurrence>) = Unit
    override fun isBatteryOptimizationActive(): Boolean = false
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

private fun TrendPeriod.toGraphPeriod(): GraphPeriod = when (this) {
    TrendPeriod.SEVEN_DAYS -> GraphPeriod.DAYS_7
    TrendPeriod.THIRTY_DAYS -> GraphPeriod.DAYS_30
    TrendPeriod.NINETY_DAYS -> GraphPeriod.DAYS_90
}

private fun SyncFailureCategory?.toAnalyticsCategory(): AnalyticsSyncFailureCategory = when (this) {
    SyncFailureCategory.NETWORK -> AnalyticsSyncFailureCategory.NETWORK
    SyncFailureCategory.CONFLICT -> AnalyticsSyncFailureCategory.CONFLICT
    SyncFailureCategory.SERVER -> AnalyticsSyncFailureCategory.SERVER
    SyncFailureCategory.UNKNOWN, null -> AnalyticsSyncFailureCategory.UNKNOWN
}
