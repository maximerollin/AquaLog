package com.maximerollin.aqualog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximerollin.aqualog.habitants.AddInhabitantScreen
import com.maximerollin.aqualog.home.TankHome
import com.maximerollin.aqualog.onboarding.ProfileContent
import com.maximerollin.aqualog.onboarding.WelcomeContent
import com.maximerollin.aqualog.onboarding.onboardingActions
import com.maximerollin.aqualog.home.plusEnabled
import com.maximerollin.aqualog.resources.*
import com.maximerollin.aqualog.shared.AccountDeletionPhase
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.FeedbackKind
import com.maximerollin.aqualog.shared.FeedbackOutcome
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.ui.icon.AquagendaIcons
import com.maximerollin.aqualog.ui.icon.Cloud
import com.maximerollin.aqualog.ui.theme.CarnetDivider
import com.maximerollin.aqualog.ui.theme.CarnetScreenTitle
import com.maximerollin.aqualog.ui.theme.CarnetSectionTitle
import com.maximerollin.aqualog.ui.theme.FilledField
import com.maximerollin.aqualog.ui.theme.FloatingNavBar
import com.maximerollin.aqualog.ui.theme.WaterFullScreen
import com.maximerollin.aqualog.ui.theme.WaterPrimaryButtonText
import com.maximerollin.aqualog.ui.theme.frauncesFamily
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AquaLogContent(
    state: HomeUiState,
    viewModel: HomeViewModel,
    onExportData: () -> Unit = {},
    onImportData: () -> Unit = {},
    externalLinks: ExternalLinks = ExternalLinks(),
    rateApp: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    ScreenBackHandler(state, viewModel)
    // [contentPadding] is the host's bottom and side insets (the gesture bar). The account screens are full-frame water that
    // handles those insets itself, so they get the bare [modifier] and reach the bottom of the window. Every other screen gets
    // `framed`, kept clear of the gesture bar.
    val framed = modifier.padding(contentPadding).consumeWindowInsets(contentPadding)
    // Screens whose water header draws under the status bar get `framed` as is: Tasks, Rhythm, the Session, Add inhabitant,
    // Session details and the shell (the home, History, Trends and More). Every other screen gets `inset` and starts below the
    // status bar.
    val inset = framed.windowInsetsPadding(WindowInsets.statusBars)
    when {
        state.isLoading -> LoadingContent(inset)
        state.accountSettings.pendingDeletion?.phase == AccountDeletionPhase.CLOUD_DELETED -> {
            AccountDeletionCleanupScreen(state.accountSettings, state.setup?.aquarium, viewModel, modifier)
        }
        state.account.step != AccountStep.HIDDEN -> AccountScreen(state.account, state.setup?.aquarium, viewModel, modifier)
        state.tasks.isOpen && state.setup != null -> TaskScreen(
            state = state.tasks.copy(
                pendingOccurrences = state.tasks.pendingOccurrences.filter {
                    it.aquariumId == state.setup.aquarium.id
                },
            ),
            viewModel = viewModel,
            aquarium = state.setup.aquarium,
            modifier = framed,
        )
        state.showRhythmSettings && state.setup != null -> RhythmSettingsScreen(
            state.setup,
            viewModel,
            framed,
        )
        state.rapidSession != null && state.setup != null -> RapidSessionScreen(
            state = state.rapidSession,
            aquarium = state.setup.aquarium,
            viewModel = viewModel,
            modifier = framed,
        )
        // Below entry: « Faire une analyse » opens a Session over the fiche, which comes back once it is closed.
        state.addInhabitant != null && state.setup != null -> AddInhabitantScreen(state.addInhabitant, state, viewModel, framed)
        state.sessionDetail != null && state.setup != null -> SessionDetailsScreen(
            recorded = state.sessionDetail,
            setup = state.setup,
            returnToTimeline = state.sessionDetailReturnsToTimeline,
            showDeleteConfirmation = state.showDeleteConfirmation,
            viewModel = viewModel,
            modifier = framed,
        )
        state.setup != null && state.pro.showPaywall -> PaywallScreen(
            pro = state.pro,
            isSaving = state.isSaving,
            onboarding = state.pro.openedFromOnboarding,
            onStartPurchase = viewModel::startProPurchase,
            onStartLifetimePurchase = viewModel::startLifetimeProPurchase,
            onRestore = viewModel::restoreProPurchase,
            onRetryOffer = viewModel::loadProOffer,
            onContinue = viewModel::closeProPaywall,
            modifier = inset,
        )
        state.setup != null -> AquariumShell(state, viewModel, onExportData, externalLinks, rateApp, framed)
        state.step == OnboardingStep.WELCOME -> WelcomeContent(state.portableData, viewModel.onboardingActions(), modifier)
        state.step == OnboardingStep.PROFILE -> ProfileContent(state.profile, viewModel.onboardingActions(), framed)
        state.step == OnboardingStep.AQUARIUM -> AquariumDetailsScreen(
            state = state,
            viewModel = viewModel,
            modifier = inset,
        )
        state.step == OnboardingStep.PARAMETERS -> ParametersScreen(
            state = state,
            viewModel = viewModel,
            modifier = inset,
        )
        state.step == OnboardingStep.PRACTICE -> PracticeScreen(
            state = state,
            onValueChanged = viewModel::updatePracticeValue,
            onContinue = viewModel::continueToPaywall,
            modifier = inset,
        )
        else -> PaywallScreen(
            pro = state.pro,
            isSaving = state.isSaving,
            onboarding = true,
            onStartPurchase = viewModel::startProPurchase,
            onStartLifetimePurchase = viewModel::startLifetimeProPurchase,
            onRestore = viewModel::restoreProPurchase,
            onRetryOffer = viewModel::loadProOffer,
            onContinue = viewModel::finishForFree,
            modifier = inset,
        )
    }
    if (state.portableData.showImportConfirmation) {
        PortableImportConfirmation(
            onConfirm = {
                viewModel.dismissPortableImportConfirmation()
                onImportData()
            },
            onDismiss = viewModel::dismissPortableImportConfirmation,
        )
    }
    if (state.feedback.isOpen) {
        FeedbackDialog(
            state = state.feedback,
            onMessageChanged = viewModel::updateFeedback,
            onKindSelected = viewModel::selectFeedbackKind,
            onSubmit = { viewModel.submitFeedback(feedbackLanguage(Locale.current.language)) },
            onDismiss = viewModel::dismissFeedback,
        )
    }
}

/** Test tag of the decorative cloud square that opens every account step. */
internal const val ACCOUNT_CLOUD_TAG = "account-cloud"

/** A glass button's fill on the full-screen backing: its white label keeps 4.5:1 (ScenePhysicsTest). */
internal val AccountGlassButtonFill = Color.White.copy(alpha = .14f)

/** A step's description on the full-screen backing: it keeps 4.5:1 (ScenePhysicsTest). */
internal val AccountDescriptionText = Color.White.copy(alpha = .85f)

@Composable
private fun AccountScreen(state: AccountUiState, aquarium: Aquarium?, viewModel: HomeViewModel, modifier: Modifier) {
    AccountContent(
        state = state,
        aquarium = aquarium,
        actions = AccountActions(
            onOpenMethods = viewModel::openAccountMethods,
            onDismiss = viewModel::dismissAccount,
            onSignInWithGoogle = viewModel::signInWithGoogle,
            onOpenMagicEmail = viewModel::openMagicEmail,
            onEmailChange = viewModel::updateAccountEmail,
            onRequestMagicLink = viewModel::requestMagicLink,
            onRetryMigration = viewModel::retryAccountMigration,
        ),
        modifier = modifier,
    )
}

/** What the account steps ask of their ViewModel; every default does nothing, for tests. */
internal class AccountActions(
    val onOpenMethods: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onSignInWithGoogle: () -> Unit = {},
    val onOpenMagicEmail: () -> Unit = {},
    val onEmailChange: (String) -> Unit = {},
    val onRequestMagicLink: () -> Unit = {},
    val onRetryMigration: () -> Unit = {},
)

/** The account steps, full frame on the [aquarium]'s water (deep water without one): a cloud, the step's text, its buttons. */
@Composable
internal fun AccountContent(state: AccountUiState, aquarium: Aquarium?, actions: AccountActions, modifier: Modifier = Modifier) {
    AccountFrame(aquarium, modifier) {
        when (state.step) {
            AccountStep.INVITATION -> {
                AccountHeading(Res.string.account_invitation_title, Res.string.account_invitation_description)
                WaterPrimaryButton(Res.string.create_account, actions.onOpenMethods)
                WaterTextButton(Res.string.not_now, actions.onDismiss)
            }
            AccountStep.METHODS -> {
                AccountHeading(Res.string.account_methods_title, Res.string.account_methods_description)
                WaterPrimaryButton(Res.string.continue_with_google, actions.onSignInWithGoogle)
                WaterGlassButton(Res.string.continue_with_email, actions.onOpenMagicEmail)
                WaterTextButton(Res.string.cancel, actions.onDismiss)
            }
            AccountStep.MAGIC_EMAIL -> {
                AccountHeading(Res.string.magic_link_title, Res.string.magic_link_description)
                FilledField(
                    value = state.email,
                    onValueChange = actions.onEmailChange,
                    label = stringResource(Res.string.email_address),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                WaterPrimaryButton(Res.string.send_magic_link, actions.onRequestMagicLink)
                WaterTextButton(Res.string.back, actions.onOpenMethods)
            }
            AccountStep.MAGIC_SENT -> {
                AccountHeading(Res.string.magic_link_sent_title, Res.string.magic_link_sent_description)
                WaterTextButton(Res.string.back_to_aquarium, actions.onDismiss)
            }
            AccountStep.WAITING_BROWSER -> {
                AccountHeading(Res.string.authentication_waiting_title, Res.string.authentication_waiting_description)
                WaterTextButton(Res.string.back_to_aquarium, actions.onDismiss)
            }
            AccountStep.WORKING -> Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
            AccountStep.ERROR -> {
                val message = when (state.error) {
                    AccountError.EXPIRED_LINK -> Res.string.magic_link_expired
                    AccountError.AUTHENTICATION -> Res.string.authentication_failed
                    AccountError.MIGRATION, null -> Res.string.account_migration_failed
                }
                // The error is said in words, in white: never red on the water.
                AccountHeading(Res.string.account_error_title, message, descriptionColor = Color.White)
                WaterPrimaryButton(
                    Res.string.try_again,
                    if (state.error == AccountError.MIGRATION) actions.onRetryMigration else actions.onOpenMethods,
                )
                WaterTextButton(Res.string.back_to_aquarium, actions.onDismiss)
            }
            AccountStep.HIDDEN -> Unit
        }
    }
}

/** The frame every account step shares: the water, then at the bottom a glass square with a cloud and the step's [content]. */
@Composable
private fun AccountFrame(aquarium: Aquarium?, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    WaterFullScreen(aquarium, modifier) {
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.testTag(ACCOUNT_CLOUD_TAG).size(56.dp)
                    .background(Color.White.copy(alpha = .2f), RoundedCornerShape(18.dp))
                    .border(1.dp, Color.White.copy(alpha = .3f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AquagendaIcons.Cloud, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun AccountHeading(title: StringResource, description: StringResource, descriptionColor: Color = AccountDescriptionText) {
    Text(
        stringResource(title),
        modifier = Modifier.semantics { heading() },
        style = TextStyle(fontFamily = frauncesFamily(), fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 34.sp),
        color = Color.White,
    )
    Text(
        stringResource(description),
        modifier = Modifier.padding(bottom = 8.dp),
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
        color = descriptionColor,
    )
}

/** A white filled button on the water, its label deep teal. */
@Composable
private fun WaterPrimaryButton(label: StringResource, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = WaterPrimaryButtonText,
            disabledContainerColor = Color.White.copy(alpha = .6f),
            disabledContentColor = WaterPrimaryButtonText.copy(alpha = .7f),
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(stringResource(label), fontWeight = FontWeight.SemiBold)
    }
}

/** A secondary action on the water: glass with a light rim, white label. */
@Composable
private fun WaterGlassButton(label: StringResource, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = AccountGlassButtonFill, contentColor = Color.White),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .35f)),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(stringResource(label), fontWeight = FontWeight.SemiBold)
    }
}

/** A text action on the water, in white. */
@Composable
private fun WaterTextButton(label: StringResource, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(stringResource(label), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AquariumShell(
    state: HomeUiState,
    viewModel: HomeViewModel,
    onExportData: () -> Unit,
    externalLinks: ExternalLinks,
    rateApp: (() -> Unit)?,
    modifier: Modifier,
) {
    // Reported by the home, which owns the guard: « + » records into the Aquarium the scene shows.
    var tankCanMeasure by remember { mutableStateOf(false) }
    // Read at the moment of a tap, so a swipe that started a frame ago is never missed (the Boolean above lags it).
    var tankMeasureGuard by remember { mutableStateOf<() -> Boolean>({ false }) }
    Box(modifier.fillMaxSize()) {
        when (state.destination) {
            // The home sets the status-bar icons itself: light over the water, dark once the sheet is under them.
            MainDestination.TANK -> TankHome(
                state, viewModel, Modifier, rateApp,
                onMeasureAvailabilityChange = { tankCanMeasure = it },
                onMeasureGuard = { tankMeasureGuard = it },
            )
            // Like the home, History, Trends and More draw their water under the status bar and clear the floating bar themselves.
            MainDestination.HISTORY -> TimelineScreen(state, viewModel, Modifier)
            MainDestination.TRENDS -> TrendsScreen(state, viewModel, Modifier)
            MainDestination.MORE -> MoreScreen(state, viewModel, onExportData, externalLinks, rateApp, Modifier)
        }
        FloatingNavBar(
            selected = state.destination,
            onSelect = viewModel::selectDestination,
            onMeasure = { if (plusEnabled(state.destination, tankMeasureGuard())) viewModel.openRapidSession() },
            measureEnabled = plusEnabled(state.destination, tankCanMeasure),
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun LoadingContent(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun AccountDeletionCleanupScreen(
    state: AccountSettingsUiState,
    aquarium: Aquarium?,
    viewModel: HomeViewModel,
    modifier: Modifier,
) {
    AccountDeletionCleanupContent(state, aquarium, viewModel::retryAccountDeletion, modifier)
}

/** The account-deletion cleanup in the account steps' frame; its error detail is white, never red on the water. */
@Composable
internal fun AccountDeletionCleanupContent(
    state: AccountSettingsUiState,
    aquarium: Aquarium?,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AccountFrame(aquarium, modifier) {
        AccountHeading(Res.string.account_deleted_cleanup_title, Res.string.account_deleted_cleanup_description)
        state.localizedSafeDetail()?.let {
            Text(it, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
        }
        WaterPrimaryButton(Res.string.finish_local_cleanup, onFinish, enabled = !state.isWorking)
    }
}

@Composable
private fun PortableImportConfirmation(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.portable_import_confirmation_title)) },
        text = { Text(stringResource(Res.string.portable_import_confirmation_description)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.choose_export_file))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun AquariumDetailsScreen(
    state: HomeUiState,
    viewModel: HomeViewModel,
    modifier: Modifier,
) {
    StandardScreen(modifier) {
        ScreenHeading(Res.string.aquarium_details_title, Res.string.aquarium_details_description)
        OutlinedTextField(
            value = state.aquariumName,
            onValueChange = viewModel::updateAquariumName,
            label = { Text(stringResource(Res.string.aquarium_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.volumeInput,
            onValueChange = viewModel::updateVolume,
            label = { Text(stringResource(Res.string.aquarium_volume_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        CarnetSectionTitle(stringResource(Res.string.volume_unit_label))
        Column(Modifier.selectableGroup()) {
            VolumeUnitOption(
                text = stringResource(Res.string.unit_liters),
                selected = state.volumeUnit == VolumeUnit.LITERS,
                onClick = { viewModel.selectVolumeUnit(VolumeUnit.LITERS) },
            )
            VolumeUnitOption(
                text = stringResource(Res.string.unit_us_gallons),
                selected = state.volumeUnit == VolumeUnit.US_GALLONS,
                onClick = { viewModel.selectVolumeUnit(VolumeUnit.US_GALLONS) },
            )
        }
        if (state.showAquariumValidationError) {
            ErrorText(Res.string.invalid_aquarium)
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(Res.string.continue_to_parameters, viewModel::continueToParameters)
    }
}

@Composable
private fun VolumeUnitOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
private fun ParametersScreen(
    state: HomeUiState,
    viewModel: HomeViewModel,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
    ) {
        item {
            ScreenHeading(Res.string.parameters_title, Res.string.parameters_description)
            Spacer(Modifier.height(16.dp))
        }
        itemsIndexed(state.parameters, key = { _, item -> item.parameter.storageValue }) { index, item ->
            ParameterEditor(
                state = item,
                canMoveUp = index > 0,
                canMoveDown = index < state.parameters.lastIndex,
                viewModel = viewModel,
            )
        }
        item { CarnetDivider() }
        if (state.showParameterValidationError) {
            item { ErrorText(Res.string.invalid_parameters) }
        }
        item {
            Spacer(Modifier.height(16.dp))
            PrimaryButton(Res.string.try_mini_session, viewModel::continueToPractice)
        }
    }
}

@Composable
private fun ParameterEditor(
    state: ParameterEditorState,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    viewModel: HomeViewModel,
) {
    val label = state.parameter.label()
    val moveUpDescription = stringResource(Res.string.move_parameter_up, label)
    val moveDownDescription = stringResource(Res.string.move_parameter_down, label)
    val enabledDescription = stringResource(Res.string.enabled_for_parameter, label)
    Column {
        CarnetDivider()
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { viewModel.moveParameter(state.parameter, -1) },
                    enabled = canMoveUp,
                    modifier = Modifier.semantics { contentDescription = moveUpDescription },
                ) { Text(stringResource(Res.string.move_up_symbol)) }
                TextButton(
                    onClick = { viewModel.moveParameter(state.parameter, 1) },
                    enabled = canMoveDown,
                    modifier = Modifier.semantics { contentDescription = moveDownDescription },
                ) { Text(stringResource(Res.string.move_down_symbol)) }
                Switch(
                    checked = state.isActive,
                    onCheckedChange = { viewModel.setParameterActive(state.parameter, it) },
                    modifier = Modifier.semantics { contentDescription = enabledDescription },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ParameterTextField(
                    value = state.unit,
                    onValueChange = { viewModel.updateParameterUnit(state.parameter, it) },
                    label = stringResource(Res.string.parameter_unit),
                    description = stringResource(Res.string.unit_for_parameter, label),
                    modifier = Modifier.weight(1f),
                )
                ParameterTextField(
                    value = state.precision,
                    onValueChange = { viewModel.updateParameterPrecision(state.parameter, it) },
                    label = stringResource(Res.string.parameter_precision),
                    description = stringResource(Res.string.precision_for_parameter, label),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ParameterTextField(
                    value = state.indicativeMinimum,
                    onValueChange = { viewModel.updateParameterMinimum(state.parameter, it) },
                    label = stringResource(Res.string.parameter_minimum),
                    description = stringResource(Res.string.minimum_for_parameter, label),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                ParameterTextField(
                    value = state.indicativeMaximum,
                    onValueChange = { viewModel.updateParameterMaximum(state.parameter, it) },
                    label = stringResource(Res.string.parameter_maximum),
                    description = stringResource(Res.string.maximum_for_parameter, label),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ParameterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    description: String,
    modifier: Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.semantics { contentDescription = description },
    )
}

@Composable
private fun PracticeScreen(
    state: HomeUiState,
    onValueChanged: (BuiltInParameter, String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier,
) {
    val activeParameters = state.parameters.filter(ParameterEditorState::isActive).take(3)
    StandardScreen(modifier) {
        ScreenHeading(Res.string.practice_title, Res.string.practice_description)
        activeParameters.forEach { parameter ->
            val label = parameter.parameter.label()
            val practiceDescription = stringResource(Res.string.practice_value_for_parameter, label)
            OutlinedTextField(
                value = state.practiceValues[parameter.parameter].orEmpty(),
                onValueChange = { onValueChanged(parameter.parameter, it) },
                label = { Text(stringResource(Res.string.parameter_with_unit, label, parameter.unit)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = practiceDescription },
            )
        }
        Text(
            stringResource(Res.string.practice_not_saved),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton(Res.string.see_free_and_pro, onContinue)
    }
}

@Composable
private fun PaywallScreen(
    pro: ProUiState,
    isSaving: Boolean,
    onboarding: Boolean,
    onStartPurchase: () -> Unit,
    onStartLifetimePurchase: () -> Unit,
    onRestore: () -> Unit,
    onRetryOffer: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier,
) {
    val formatter = currentLocalizedFormatters()
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeading(Res.string.paywall_title, Res.string.paywall_description)
        PlanCard(Res.string.free_plan, Res.string.free_plan_description)
        PlanCard(Res.string.pro_plan, Res.string.pro_plan_description)
        when {
            pro.isLoadingOffer -> {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                Text(stringResource(Res.string.loading_google_play_offer))
            }
            pro.lifetimeOffer != null -> {
                Text(
                    stringResource(Res.string.pro_lifetime_offer_summary, pro.lifetimeOffer.formattedPrice),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(
                    onClick = onStartLifetimePurchase,
                    enabled = pro.purchaseStatus !in setOf(
                        ProPurchaseStatus.AWAITING_PLAY_CONFIRMATION,
                        ProPurchaseStatus.PENDING,
                        ProPurchaseStatus.VERIFYING,
                        ProPurchaseStatus.ACTIVE,
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(stringResource(Res.string.buy_lifetime_in_google_play))
                }
                pro.offer?.let { annualOffer ->
                    Text(
                        stringResource(
                            Res.string.pro_offer_summary,
                            annualOffer.formattedPrice,
                            annualOffer.trialDays,
                            annualOffer.localizedBillingDate(formatter),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(
                        onClick = onStartPurchase,
                        enabled = pro.purchaseStatus !in setOf(
                            ProPurchaseStatus.AWAITING_PLAY_CONFIRMATION,
                            ProPurchaseStatus.PENDING,
                            ProPurchaseStatus.VERIFYING,
                            ProPurchaseStatus.ACTIVE,
                        ),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text(stringResource(Res.string.start_trial_in_google_play))
                    }
                }
            }
            pro.offer != null -> {
                Text(
                    stringResource(
                        Res.string.pro_offer_summary,
                        pro.offer.formattedPrice,
                        pro.offer.trialDays,
                        pro.offer.localizedBillingDate(formatter),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(
                    onClick = onStartPurchase,
                    enabled = pro.purchaseStatus !in setOf(
                        ProPurchaseStatus.AWAITING_PLAY_CONFIRMATION,
                        ProPurchaseStatus.PENDING,
                        ProPurchaseStatus.VERIFYING,
                        ProPurchaseStatus.ACTIVE,
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(stringResource(Res.string.start_trial_in_google_play))
                }
            }
            else -> {
                Text(stringResource(Res.string.google_play_offer_unavailable), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetryOffer, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(stringResource(Res.string.try_again))
                }
            }
        }
        pro.message?.let { message ->
            Text(
                stringResource(message.resourceId()),
                color = if (pro.purchaseStatus == ProPurchaseStatus.ACTIVE) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        TextButton(
            onClick = onRestore,
            enabled = pro.purchaseStatus != ProPurchaseStatus.VERIFYING,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(stringResource(Res.string.restore_purchase))
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onContinue,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (isSaving) {
                CircularProgressIndicator()
            } else {
                Text(
                    stringResource(
                        when {
                            !onboarding -> Res.string.back_to_settings
                            pro.purchaseStatus == ProPurchaseStatus.ACTIVE -> Res.string.continue_with_pro
                            else -> Res.string.continue_for_free
                        },
                    ),
                )
            }
        }
    }
}

private fun ProMessage.resourceId(): StringResource = when (this) {
    ProMessage.PLAY_UNAVAILABLE -> Res.string.pro_message_play_unavailable
    ProMessage.PURCHASE_PENDING -> Res.string.pro_message_purchase_pending
    ProMessage.PURCHASE_CANCELLED -> Res.string.pro_message_purchase_cancelled
    ProMessage.AUTHENTICATION_REQUIRED -> Res.string.pro_message_authentication_required
    ProMessage.ACTIVE -> Res.string.pro_message_active
    ProMessage.VERIFICATION_FAILED -> Res.string.pro_message_verification_failed
    ProMessage.SAVE_FAILED -> Res.string.pro_message_save_failed
}

@Composable
internal fun AccountSettingsUiState.localizedSafeDetail(): String? = safeMessage?.let {
    stringResource(
        when (it) {
            AccountSafeMessage.SIGN_OUT_FAILED -> Res.string.account_sign_out_failed
            AccountSafeMessage.DELETION_RESUME_FAILED -> Res.string.account_deletion_resume_failed
            AccountSafeMessage.AUTHENTICATION_LINK_FAILED -> Res.string.authentication_link_processing_failed
        },
    )
} ?: safeDetail

private fun FeedbackKind.labelResource(): StringResource = when (this) {
    FeedbackKind.BUG -> Res.string.feedback_kind_bug
    FeedbackKind.TRANSLATION -> Res.string.feedback_kind_translation
    FeedbackKind.SPECIES -> Res.string.feedback_kind_species
    FeedbackKind.IDEA -> Res.string.feedback_kind_idea
    FeedbackKind.OTHER -> Res.string.feedback_kind_other
}

/** A decorative glyph, not a word: the chip's label carries the meaning. */
private const val SELECTED_MARK = "✓"

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FeedbackDialog(
    state: FeedbackUiState,
    onMessageChanged: (String) -> Unit,
    onKindSelected: (FeedbackKind) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.feedback_title)) },
        text = {
            Column(
                Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(Res.string.feedback_privacy_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeedbackKind.entries.forEach { kind ->
                        FilterChip(
                            selected = state.kind == kind,
                            onClick = { onKindSelected(kind) },
                            label = { Text(stringResource(kind.labelResource())) },
                            // Selection is shown by a filled container and a check mark, not by colour alone.
                            leadingIcon = if (state.kind == kind) {
                                { Text(SELECTED_MARK, fontWeight = FontWeight.Bold, modifier = Modifier.clearAndSetSemantics {}) }
                            } else {
                                null
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier.heightIn(min = 48.dp).semantics { role = Role.RadioButton },
                        )
                    }
                }
                state.speciesName?.let { Text(stringResource(Res.string.feedback_species_line, it), style = MaterialTheme.typography.bodyMedium) }
                // The label slot gives the field its accessible name (a separate Text or a contentDescription leaves the typed text
                // nameless); a short label keeps it on one line, unbroken, in the narrow field at large font scales.
                OutlinedTextField(
                    value = state.message,
                    onValueChange = onMessageChanged,
                    label = { Text(stringResource(Res.string.feedback_message_label)) },
                    supportingText = {
                        Text(stringResource(Res.string.feedback_character_count, state.message.length))
                    },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("feedback-message"),
                )
                state.outcome?.let { outcome ->
                    val (text, color) = when (outcome) {
                        FeedbackOutcome.SENT -> Res.string.feedback_sent to MaterialTheme.colorScheme.primary
                        FeedbackOutcome.RATE_LIMITED -> Res.string.feedback_rate_limited to MaterialTheme.colorScheme.error
                        FeedbackOutcome.FAILED -> Res.string.feedback_failed to MaterialTheme.colorScheme.error
                        FeedbackOutcome.UNAVAILABLE -> Res.string.feedback_unavailable to MaterialTheme.colorScheme.error
                    }
                    Text(stringResource(text), color = color, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSubmit,
                enabled = state.message.isNotBlank() && !state.isSending,
                modifier = Modifier.testTag("submit-feedback").heightIn(min = 48.dp),
            ) {
                Text(stringResource(Res.string.send_feedback))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun PlanCard(title: StringResource, description: StringResource) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CarnetSectionTitle(stringResource(title))
        Text(
            stringResource(description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StandardScreen(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun ScreenHeading(title: StringResource, description: StringResource) {
    CarnetScreenTitle(stringResource(title), stringResource(description))
}

@Composable
private fun PrimaryButton(label: StringResource, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(stringResource(label))
    }
}

@Composable
private fun ErrorText(message: StringResource) {
    Text(
        stringResource(message),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
internal fun BuiltInParameter.label(): String = stringResource(labelResource())

internal fun BuiltInParameter.labelResource(): StringResource = when (this) {
    BuiltInParameter.TEMPERATURE -> Res.string.parameter_temperature
    BuiltInParameter.PH -> Res.string.parameter_ph
    BuiltInParameter.AMMONIA -> Res.string.parameter_ammonia
    BuiltInParameter.NITRITE -> Res.string.parameter_nitrite
    BuiltInParameter.NITRATE -> Res.string.parameter_nitrate
    BuiltInParameter.GH -> Res.string.parameter_gh
    BuiltInParameter.KH -> Res.string.parameter_kh
    BuiltInParameter.TDS -> Res.string.parameter_tds
    BuiltInParameter.PHOSPHATE -> Res.string.parameter_phosphate
    BuiltInParameter.SALINITY -> Res.string.parameter_salinity
}

/** The chemical symbol where there is one (« NO₃ »), otherwise the usual label. */
internal fun BuiltInParameter.shortLabelResource(): StringResource = when (this) {
    BuiltInParameter.AMMONIA -> Res.string.parameter_short_ammonia
    BuiltInParameter.NITRITE -> Res.string.parameter_short_nitrite
    BuiltInParameter.NITRATE -> Res.string.parameter_short_nitrate
    BuiltInParameter.PHOSPHATE -> Res.string.parameter_short_phosphate
    else -> labelResource()
}

@Composable
internal fun Aquarium.formattedVolume(): String {
    val formatted = currentLocalizedFormatters().number(volume)
    return when (volumeUnit) {
        VolumeUnit.LITERS -> stringResource(Res.string.volume_liters_format, formatted)
        VolumeUnit.US_GALLONS -> stringResource(Res.string.volume_us_gallons_format, formatted)
    }
}
