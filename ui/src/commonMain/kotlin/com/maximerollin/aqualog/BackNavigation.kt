package com.maximerollin.aqualog

import androidx.compose.runtime.Composable
import com.maximerollin.aqualog.shared.AccountDeletionPhase

/** What system Back does on the screen shown: the same call as that screen's own close or back control. */
internal enum class BackStep {
    CloseAccount,
    ToAccountMethods,
    CloseTasks,
    CloseRhythm,
    CloseParameters,
    CloseSession,
    BackInAddInhabitant,
    CloseSessionDetails,
    ClosePaywall,
    ToTank,
}

/**
 * The step for the screen [AquaLogContent] draws for [state], in its `when` order; null leaves Back to the
 * system (a screen without a close or back control, Bac, the onboarding). Sheets and dialogs handle Back themselves.
 */
internal fun backStep(state: HomeUiState): BackStep? = when {
    state.isLoading || state.isRestoringAccount -> null
    state.accountSettings.pendingDeletion?.phase == AccountDeletionPhase.CLOUD_DELETED -> null
    state.account.step != AccountStep.HIDDEN -> when (state.account.step) {
        AccountStep.INVITATION,
        AccountStep.METHODS,
        AccountStep.MAGIC_SENT,
        AccountStep.WAITING_BROWSER,
        AccountStep.ERROR,
        -> BackStep.CloseAccount
        AccountStep.MAGIC_EMAIL -> BackStep.ToAccountMethods
        AccountStep.WORKING, AccountStep.HIDDEN -> null
    }
    state.tasks.isOpen && state.setup != null -> BackStep.CloseTasks
    state.showRhythmSettings && state.setup != null -> BackStep.CloseRhythm
    state.showParameterSettings && state.setup != null -> BackStep.CloseParameters
    state.rapidSession != null && state.setup != null -> BackStep.CloseSession
    state.addInhabitant != null && state.setup != null -> BackStep.BackInAddInhabitant
    state.sessionDetail != null && state.setup != null -> BackStep.CloseSessionDetails
    state.setup != null && state.pro.showPaywall -> BackStep.ClosePaywall
    state.setup != null -> if (state.destination == MainDestination.TANK) null else BackStep.ToTank
    // The onboarding steps have no back control, and its paywall only continues.
    else -> null
}

internal fun HomeViewModel.perform(step: BackStep) = when (step) {
    BackStep.CloseAccount -> dismissAccount()
    BackStep.ToAccountMethods -> openAccountMethods()
    BackStep.CloseTasks -> closeTasks()
    BackStep.CloseRhythm -> closeRhythmSettings()
    BackStep.CloseParameters -> closeParameterSettings()
    BackStep.CloseSession -> closeRapidSession()
    BackStep.BackInAddInhabitant -> backInAddInhabitant()
    BackStep.CloseSessionDetails -> closeSessionDetails()
    BackStep.ClosePaywall -> closeProPaywall()
    BackStep.ToTank -> selectDestination(MainDestination.TANK)
}

/** Takes system Back while the screen shown has a close or back control; otherwise the system handles it. */
@Composable
internal fun ScreenBackHandler(state: HomeUiState, viewModel: HomeViewModel) {
    val step = backStep(state)
    SystemBackHandler(enabled = step != null) { step?.let(viewModel::perform) }
}

/** While [enabled], system Back (button or gesture) calls [onBack] instead of reaching the system. */
@Composable
internal expect fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit)
