package com.maximerollin.aqualog

import com.maximerollin.aqualog.resources.*
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.shared.AccountDeletionPhase
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.LocalCopyDecision
import com.maximerollin.aqualog.shared.PortableImportProblem
import com.maximerollin.aqualog.shared.SyncFailureCategory
import com.maximerollin.aqualog.shared.SyncStatus
import com.maximerollin.aqualog.shared.VolumeUnit

@Composable
fun AquaLogContent(
    state: HomeUiState,
    viewModel: HomeViewModel,
    onExportData: () -> Unit = {},
    onImportData: () -> Unit = {},
    externalLinks: ExternalLinks = ExternalLinks(),
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> LoadingContent(modifier)
        state.accountSettings.pendingDeletion?.phase == AccountDeletionPhase.CLOUD_DELETED -> {
            AccountDeletionCleanupScreen(state.accountSettings, viewModel, modifier)
        }
        state.account.step != AccountStep.HIDDEN -> AccountScreen(state.account, viewModel, modifier)
        state.tasks.isOpen && state.setup != null -> TaskScreen(
            state = state.tasks.copy(
                pendingOccurrences = state.tasks.pendingOccurrences.filter {
                    it.aquariumId == state.setup.aquarium.id
                },
            ),
            viewModel = viewModel,
            modifier = modifier,
        )
        state.rapidSession != null && state.setup != null -> RapidSessionScreen(
            state = state.rapidSession,
            aquarium = state.setup.aquarium,
            viewModel = viewModel,
            modifier = modifier,
        )
        state.sessionDetail != null && state.setup != null -> SessionDetailsScreen(
            recorded = state.sessionDetail,
            setup = state.setup,
            returnToTimeline = state.sessionDetailReturnsToTimeline,
            showDeleteConfirmation = state.showDeleteConfirmation,
            viewModel = viewModel,
            modifier = modifier,
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
            modifier = modifier,
        )
        state.setup != null -> AquariumShell(state, viewModel, onExportData, externalLinks, modifier)
        state.step == OnboardingStep.WELCOME -> WelcomeScreen(
            state = state.portableData,
            onContinue = viewModel::startOnboarding,
            onRequestImport = viewModel::requestPortableImport,
            modifier = modifier,
        )
        state.step == OnboardingStep.PROFILE -> ProfileScreen(
            selected = state.profile,
            onSelected = viewModel::selectProfile,
            onContinue = viewModel::continueToAquarium,
            modifier = modifier,
        )
        state.step == OnboardingStep.AQUARIUM -> AquariumDetailsScreen(
            state = state,
            viewModel = viewModel,
            modifier = modifier,
        )
        state.step == OnboardingStep.PARAMETERS -> ParametersScreen(
            state = state,
            viewModel = viewModel,
            modifier = modifier,
        )
        state.step == OnboardingStep.PRACTICE -> PracticeScreen(
            state = state,
            onValueChanged = viewModel::updatePracticeValue,
            onContinue = viewModel::continueToPaywall,
            modifier = modifier,
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
            modifier = modifier,
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
            onSubmit = viewModel::submitFeedback,
            onDismiss = viewModel::dismissFeedback,
        )
    }
}

@Composable
private fun AccountScreen(state: AccountUiState, viewModel: HomeViewModel, modifier: Modifier) {
    StandardScreen(modifier) {
        when (state.step) {
            AccountStep.INVITATION -> {
                ScreenHeading(Res.string.account_invitation_title, Res.string.account_invitation_description)
                Spacer(Modifier.weight(1f))
                PrimaryButton(Res.string.create_account, viewModel::openAccountMethods)
                TextButton(onClick = viewModel::dismissAccount, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(stringResource(Res.string.not_now))
                }
            }
            AccountStep.METHODS -> {
                ScreenHeading(Res.string.account_methods_title, Res.string.account_methods_description)
                PrimaryButton(Res.string.continue_with_google, viewModel::signInWithGoogle)
                PrimaryButton(Res.string.continue_with_email, viewModel::openMagicEmail)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = viewModel::dismissAccount, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(stringResource(Res.string.cancel))
                }
            }
            AccountStep.MAGIC_EMAIL -> {
                ScreenHeading(Res.string.magic_link_title, Res.string.magic_link_description)
                OutlinedTextField(
                    value = state.email,
                    onValueChange = viewModel::updateAccountEmail,
                    label = { Text(stringResource(Res.string.email_address)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton(Res.string.send_magic_link, viewModel::requestMagicLink)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = viewModel::openAccountMethods, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(stringResource(Res.string.back))
                }
            }
            AccountStep.MAGIC_SENT -> {
                ScreenHeading(Res.string.magic_link_sent_title, Res.string.magic_link_sent_description)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = viewModel::dismissAccount, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(stringResource(Res.string.back_to_aquarium))
                }
            }
            AccountStep.WAITING_BROWSER -> {
                ScreenHeading(Res.string.authentication_waiting_title, Res.string.authentication_waiting_description)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = viewModel::dismissAccount, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(stringResource(Res.string.back_to_aquarium))
                }
            }
            AccountStep.WORKING -> LoadingContent(Modifier.fillMaxSize())
            AccountStep.ERROR -> {
                val message = when (state.error) {
                    AccountError.EXPIRED_LINK -> Res.string.magic_link_expired
                    AccountError.AUTHENTICATION -> Res.string.authentication_failed
                    AccountError.MIGRATION, null -> Res.string.account_migration_failed
                }
                ScreenHeading(Res.string.account_error_title, message)
                PrimaryButton(
                    Res.string.try_again,
                    if (state.error == AccountError.MIGRATION) {
                        viewModel::retryAccountMigration
                    } else {
                        viewModel::openAccountMethods
                    },
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = viewModel::dismissAccount, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(stringResource(Res.string.back_to_aquarium))
                }
            }
            AccountStep.HIDDEN -> Unit
        }
    }
}

@Composable
private fun AquariumShell(
    state: HomeUiState,
    viewModel: HomeViewModel,
    onExportData: () -> Unit,
    externalLinks: ExternalLinks,
    modifier: Modifier,
) {
    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                MainDestination.entries.forEach { destination ->
                    val label = when (destination) {
                        MainDestination.HOME -> stringResource(Res.string.navigation_home)
                        MainDestination.HISTORY -> stringResource(Res.string.navigation_history)
                        MainDestination.SETTINGS -> stringResource(Res.string.navigation_settings)
                    }
                    NavigationBarItem(
                        selected = state.destination == destination,
                        onClick = { viewModel.selectDestination(destination) },
                        icon = {
                            Icon(
                                painterResource(when (destination) {
                                    MainDestination.HOME -> Res.drawable.ic_home
                                    MainDestination.HISTORY -> Res.drawable.ic_history
                                    MainDestination.SETTINGS -> Res.drawable.ic_settings
                                }),
                                contentDescription = null,
                            )
                        },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
        when (state.destination) {
            MainDestination.HOME -> AquariumHome(state, viewModel, contentModifier)
            MainDestination.HISTORY -> TimelineScreen(state, viewModel, contentModifier)
            MainDestination.SETTINGS -> PortableDataSettings(
                homeState = state,
                state = state.portableData,
                accountState = state.accountSettings,
                syncState = state.syncState,
                proState = state.pro,
                onExportData = onExportData,
                onRequestImport = viewModel::requestPortableImport,
                viewModel = viewModel,
                externalLinks = externalLinks,
                modifier = contentModifier,
            )
        }
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
private fun WelcomeScreen(
    state: PortableDataUiState,
    onContinue: () -> Unit,
    onRequestImport: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.weight(1f))
        ScreenHeading(Res.string.welcome_title, Res.string.welcome_description)
        PortableDataMessage(state)
        Spacer(Modifier.weight(1f))
        PrimaryButton(Res.string.get_started, onContinue)
        OutlinedButton(
            onClick = onRequestImport,
            enabled = !state.isWorking,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(stringResource(Res.string.restore_from_export))
        }
    }
}

@Composable
private fun PortableDataSettings(
    homeState: HomeUiState,
    state: PortableDataUiState,
    accountState: AccountSettingsUiState,
    syncState: com.maximerollin.aqualog.shared.SyncState,
    proState: ProUiState,
    onExportData: () -> Unit,
    onRequestImport: () -> Unit,
    viewModel: HomeViewModel,
    externalLinks: ExternalLinks,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("settings-list").padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        item { ScreenHeading(Res.string.settings_title, Res.string.portable_data_description) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.aquariums_title), style = MaterialTheme.typography.titleMedium)
                    homeState.aquariums.forEach { aquarium ->
                        if (homeState.setup?.aquarium?.id == aquarium.id) {
                            Button(
                                onClick = {},
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) { Text(aquarium.name) }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.selectAquarium(aquarium.id) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) { Text(aquarium.name) }
                        }
                    }
                    Button(
                        onClick = viewModel::openCreateAquarium,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(stringResource(Res.string.add_aquarium)) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = viewModel::openEditAquarium,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text(stringResource(Res.string.edit_aquarium)) }
                        OutlinedButton(
                            onClick = viewModel::archiveCurrentAquarium,
                            enabled = proState.purchaseStatus == ProPurchaseStatus.ACTIVE &&
                                homeState.aquariums.size > 1,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text(stringResource(Res.string.archive_aquarium)) }
                    }
                    if (proState.purchaseStatus != ProPurchaseStatus.ACTIVE) {
                        Text(
                            stringResource(Res.string.free_one_aquarium_limit),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            if (proState.purchaseStatus == ProPurchaseStatus.ACTIVE) {
                                Res.string.pro_active
                            } else {
                                Res.string.free_plan
                            },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(Res.string.subscription_settings_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = viewModel::openProPaywall,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text(stringResource(Res.string.view_free_and_pro))
                    }
                }
            }
        }
        if (state.message != null || state.importProblem != null) {
            item { PortableDataMessage(state) }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.account_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        accountState.identity?.email ?: stringResource(Res.string.account_local_only),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        when (syncState.status) {
                            SyncStatus.SYNCED -> stringResource(Res.string.sync_status_synced)
                            SyncStatus.PENDING -> stringResource(Res.string.sync_status_pending)
                            SyncStatus.FAILED -> stringResource(Res.string.sync_status_failed)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        accountState.pendingDeletion?.let {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(Res.string.account_deletion_pending), style = MaterialTheme.typography.titleMedium)
                        Text(accountState.localizedSafeDetail() ?: stringResource(Res.string.account_deletion_pending_description))
                        if (accountState.showDeletionReauthenticationAction) {
                            Button(
                                onClick = viewModel::reauthenticateAccountDeletionWithGoogle,
                                enabled = !accountState.isWorking,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                            ) { Text(stringResource(Res.string.continue_with_google)) }
                            OutlinedButton(
                                onClick = viewModel::requestDeletionMagicLink,
                                enabled = !accountState.isWorking,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                            ) { Text(stringResource(Res.string.send_deletion_magic_link)) }
                        } else {
                            Button(
                                onClick = viewModel::retryAccountDeletion,
                                enabled = !accountState.isWorking,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                            ) { Text(stringResource(Res.string.retry_account_deletion)) }
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.portable_data_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(Res.string.portable_data_replace_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onExportData,
                    enabled = !state.isWorking,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(stringResource(Res.string.export_data))
                }
                OutlinedButton(
                    onClick = onRequestImport,
                    enabled = !state.isWorking,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(stringResource(Res.string.restore_from_export))
                }
            }
        }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.account_actions), style = MaterialTheme.typography.titleMedium)
                    if (accountState.identity != null && accountState.pendingDeletion == null) {
                        OutlinedButton(
                            onClick = { viewModel.showAccountSettingsDialog(AccountSettingsDialog.LOGOUT) },
                            enabled = !accountState.isWorking,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) { Text(stringResource(Res.string.sign_out)) }
                        OutlinedButton(
                            onClick = { viewModel.showAccountSettingsDialog(AccountSettingsDialog.DELETE) },
                            enabled = !accountState.isWorking,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) { Text(stringResource(Res.string.delete_account)) }
                    } else {
                        Text(stringResource(Res.string.account_local_only_description))
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.feedback_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(Res.string.feedback_settings_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = viewModel::openFeedback,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(Res.string.send_feedback))
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { viewModel.showAccountSettingsDialog(AccountSettingsDialog.PRIVACY) }) {
                    Text(stringResource(Res.string.privacy_policy))
                }
                TextButton(onClick = { viewModel.showAccountSettingsDialog(AccountSettingsDialog.SUPPORT) }) {
                    Text(stringResource(Res.string.support))
                }
            }
        }
        item {
            if (state.isWorking || accountState.isWorking) CircularProgressIndicator()
            accountState.localizedSafeDetail()?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(16.dp))
        }
    }
    AccountSettingsDialog(accountState.dialog, viewModel, externalLinks)
    homeState.aquariumEditor?.let { editor -> AquariumEditorDialog(editor, viewModel) }
}

@Composable
private fun AccountSettingsDialog(
    dialog: AccountSettingsDialog,
    viewModel: HomeViewModel,
    externalLinks: ExternalLinks,
) {
    when (dialog) {
        AccountSettingsDialog.NONE -> Unit
        AccountSettingsDialog.LOGOUT -> AlertDialog(
            onDismissRequest = viewModel::dismissAccountSettingsDialog,
            title = { Text(stringResource(Res.string.sign_out)) },
            text = { Text(stringResource(Res.string.sign_out_copy_choice)) },
            confirmButton = {
                TextButton(onClick = { viewModel.logout(LocalCopyDecision.KEEP) }) {
                    Text(stringResource(Res.string.keep_local_copy))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.logout(LocalCopyDecision.REMOVE) }) {
                        Text(stringResource(Res.string.remove_local_copy))
                    }
                    TextButton(onClick = viewModel::dismissAccountSettingsDialog) { Text(stringResource(Res.string.cancel)) }
                }
            },
        )
        AccountSettingsDialog.DELETE -> AlertDialog(
            onDismissRequest = viewModel::dismissAccountSettingsDialog,
            title = { Text(stringResource(Res.string.delete_account_confirmation_title)) },
            text = { Text(stringResource(Res.string.delete_account_confirmation_description), modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(onClick = viewModel::deleteAccount) { Text(stringResource(Res.string.delete_account_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissAccountSettingsDialog) { Text(stringResource(Res.string.cancel)) }
            },
        )
        AccountSettingsDialog.PRIVACY -> InformationDialog(
            Res.string.privacy_policy,
            Res.string.privacy_policy_local,
            externalLinks.privacyUrl,
            Res.string.open_privacy_policy,
            externalLinks.open,
            viewModel::dismissAccountSettingsDialog,
        )
        AccountSettingsDialog.SUPPORT -> InformationDialog(
            Res.string.support,
            Res.string.support_unconfigured,
            externalLinks.supportUrl,
            Res.string.open_support,
            externalLinks.open,
            viewModel::dismissAccountSettingsDialog,
        )
    }
}

@Composable
private fun InformationDialog(
    title: StringResource,
    body: StringResource,
    configuredUrl: String,
    openLabel: StringResource,
    onOpenExternalUrl: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body), modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = {
            if (configuredUrl.startsWith("https://")) {
                TextButton(onClick = { onOpenExternalUrl(configuredUrl) }) { Text(stringResource(openLabel)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) }
            }
        },
        dismissButton = if (configuredUrl.startsWith("https://")) {
            { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) } }
        } else null,
    )
}

@Composable
private fun AccountDeletionCleanupScreen(
    state: AccountSettingsUiState,
    viewModel: HomeViewModel,
    modifier: Modifier,
) {
    StandardScreen(modifier) {
        ScreenHeading(Res.string.account_deleted_cleanup_title, Res.string.account_deleted_cleanup_description)
        state.localizedSafeDetail()?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = viewModel::retryAccountDeletion,
            enabled = !state.isWorking,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text(stringResource(Res.string.finish_local_cleanup)) }
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
private fun PortableDataMessage(state: PortableDataUiState) {
    val string = when (state.importProblem) {
        PortableImportProblem.INVALID_DOCUMENT -> Res.string.portable_problem_invalid_document
        PortableImportProblem.MISSING_VERSION -> Res.string.portable_problem_missing_version
        PortableImportProblem.UNSUPPORTED_VERSION -> Res.string.portable_problem_unsupported_version
        PortableImportProblem.SCHEMA_MISMATCH -> Res.string.portable_problem_schema_mismatch
        PortableImportProblem.UNSUPPORTED_DUPLICATE_STRATEGY -> {
            Res.string.portable_problem_duplicate_strategy
        }
        PortableImportProblem.DUPLICATE_IDENTIFIERS -> Res.string.portable_problem_duplicate_ids
        PortableImportProblem.INVALID_AQUARIUMS -> Res.string.portable_problem_aquariums
        PortableImportProblem.INVALID_PARAMETERS -> Res.string.portable_problem_parameters
        PortableImportProblem.INVALID_SESSIONS -> Res.string.portable_problem_sessions
        PortableImportProblem.INVALID_MEASUREMENTS -> Res.string.portable_problem_measurements
        PortableImportProblem.INVALID_ACTIONS -> Res.string.portable_problem_actions
        PortableImportProblem.INVALID_EVENTS -> Res.string.portable_problem_events
        PortableImportProblem.INVALID_TASKS -> Res.string.portable_problem_tasks
        PortableImportProblem.INVALID_OCCURRENCES -> Res.string.portable_problem_occurrences
        PortableImportProblem.ACCOUNT_LINKED -> Res.string.portable_problem_account_linked
        PortableImportProblem.DATABASE_WRITE_FAILED -> Res.string.portable_problem_database
        null -> when (state.message) {
            PortableDataMessage.EXPORT_READY -> Res.string.portable_export_ready
            PortableDataMessage.IMPORT_COMPLETE -> Res.string.portable_import_complete
            PortableDataMessage.STORAGE_ERROR -> Res.string.portable_storage_error
            null -> return
        }
    }
    Text(
        stringResource(string),
        style = MaterialTheme.typography.bodyMedium,
        color = if (
            state.message == PortableDataMessage.EXPORT_READY ||
            state.message == PortableDataMessage.IMPORT_COMPLETE
        ) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        },
    )
}

@Composable
private fun ProfileScreen(
    selected: AquariumProfile,
    onSelected: (AquariumProfile) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeading(Res.string.profile_title, Res.string.profile_description)
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProfileOption(
                title = stringResource(Res.string.profile_established),
                description = stringResource(Res.string.profile_established_description),
                selected = selected == AquariumProfile.ESTABLISHED,
                onClick = { onSelected(AquariumProfile.ESTABLISHED) },
            )
            ProfileOption(
                title = stringResource(Res.string.profile_cycling),
                description = stringResource(Res.string.profile_cycling_description),
                selected = selected == AquariumProfile.CYCLING,
                onClick = { onSelected(AquariumProfile.CYCLING) },
            )
            ProfileOption(
                title = stringResource(Res.string.profile_planted_shrimp),
                description = stringResource(Res.string.profile_planted_shrimp_description),
                selected = selected == AquariumProfile.PLANTED_SHRIMP,
                onClick = { onSelected(AquariumProfile.PLANTED_SHRIMP) },
            )
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton(Res.string.continue_to_aquarium, onContinue)
    }
}

@Composable
private fun ProfileOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
        Text(stringResource(Res.string.volume_unit_label), style = MaterialTheme.typography.titleMedium)
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { ScreenHeading(Res.string.parameters_title, Res.string.parameters_description) }
        itemsIndexed(state.parameters, key = { _, item -> item.parameter.storageValue }) { index, item ->
            ParameterEditor(
                state = item,
                canMoveUp = index > 0,
                canMoveDown = index < state.parameters.lastIndex,
                viewModel = viewModel,
            )
        }
        if (state.showParameterValidationError) {
            item { ErrorText(Res.string.invalid_parameters) }
        }
        item { PrimaryButton(Res.string.try_mini_session, viewModel::continueToPractice) }
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
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
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
private fun AccountSettingsUiState.localizedSafeDetail(): String? = safeMessage?.let {
    stringResource(
        when (it) {
            AccountSafeMessage.SIGN_OUT_FAILED -> Res.string.account_sign_out_failed
            AccountSafeMessage.DELETION_RESUME_FAILED -> Res.string.account_deletion_resume_failed
            AccountSafeMessage.AUTHENTICATION_LINK_FAILED -> Res.string.authentication_link_processing_failed
        },
    )
} ?: safeDetail

@Composable
private fun FeedbackDialog(
    state: FeedbackUiState,
    onMessageChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.feedback_title)) },
        text = {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(Res.string.feedback_privacy_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = state.message,
                    onValueChange = onMessageChanged,
                    label = { Text(stringResource(Res.string.feedback_message_label)) },
                    supportingText = {
                        Text(stringResource(Res.string.feedback_character_count, state.message.length))
                    },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.wasSubmitted) {
                    Text(stringResource(Res.string.feedback_sent), color = MaterialTheme.colorScheme.primary)
                }
                if (state.isUnavailable) {
                    Text(stringResource(Res.string.feedback_unavailable), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSubmit,
                enabled = state.message.isNotBlank(),
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
private fun AquariumEditorDialog(editor: AquariumEditorUiState, viewModel: HomeViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::dismissAquariumEditor,
        title = {
            Text(stringResource(if (editor.aquariumId == null) Res.string.add_aquarium else Res.string.edit_aquarium))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = viewModel::updateAquariumEditorName,
                    label = { Text(stringResource(Res.string.aquarium_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = editor.volume,
                    onValueChange = viewModel::updateAquariumEditorVolume,
                    label = { Text(stringResource(Res.string.aquarium_volume_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VolumeUnit.entries.forEach { unit ->
                        OutlinedButton(
                            onClick = { viewModel.updateAquariumEditorUnit(unit) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(if (unit == VolumeUnit.LITERS) Res.string.unit_liters else Res.string.unit_us_gallons))
                        }
                    }
                }
                if (editor.showValidationError) Text(stringResource(Res.string.invalid_aquarium), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::saveAquariumEditor, enabled = !editor.isSaving) {
                Text(stringResource(Res.string.save_aquarium))
            }
        },
        dismissButton = { TextButton(onClick = viewModel::dismissAquariumEditor) { Text(stringResource(Res.string.cancel)) } },
    )
}

@Composable
private fun PlanCard(title: StringResource, description: StringResource) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AquariumHome(state: HomeUiState, viewModel: HomeViewModel, modifier: Modifier) {
    val aquarium = requireNotNull(state.setup).aquarium
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f).testTag("home-list"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(aquarium.name, style = MaterialTheme.typography.headlineSmall)
                                Text(aquarium.formattedVolume(), color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            AquariumArtwork(Modifier.size(72.dp))
                        }
                        Text(
                            stringResource(
                                when (state.syncState.status) {
                                    SyncStatus.SYNCED -> Res.string.sync_status_synced
                                    SyncStatus.PENDING -> Res.string.sync_status_pending
                                    SyncStatus.FAILED -> Res.string.sync_status_failed
                                },
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.syncState.status == SyncStatus.SYNCED) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(
                                if (state.latestSession == null) Res.string.no_session_yet else Res.string.session_saved,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            state.latestSession?.let { session ->
                                sessionSavedDescription(
                                    measurementCount = session.measurements.size,
                                    actionCount = session.maintenanceActions.size,
                                )
                            } ?: stringResource(Res.string.no_session_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.latestSession != null) {
                            TextButton(onClick = viewModel::openSessionDetails) {
                                Text(stringResource(Res.string.view_session_details))
                            }
                        }
                    }
                }
            }
            if (state.accountInvitationPrompt == AccountInvitationPrompt.AFTER_SESSION_CONFIRMATION) {
                item {
                    PrimaryButton(Res.string.continue_after_session, viewModel::continueAfterSessionConfirmation)
                }
            }
            item { HomeMeasurements(state) }
            if (state.syncState.status == SyncStatus.FAILED) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(
                                    when (state.syncState.failure?.category) {
                                        SyncFailureCategory.NETWORK -> Res.string.sync_failure_network
                                        SyncFailureCategory.CONFLICT -> Res.string.sync_failure_conflict
                                        SyncFailureCategory.SERVER -> Res.string.sync_failure_server
                                        SyncFailureCategory.UNKNOWN, null -> Res.string.sync_failure_unknown
                                    },
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            state.syncState.failure?.technicalDetail
                                ?.takeIf(String::isNotBlank)
                                ?.let { detail ->
                                    Text(
                                        detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            TextButton(
                                onClick = viewModel::retrySynchronization,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                            ) {
                                Text(stringResource(Res.string.sync_retry))
                            }
                        }
                    }
                }
            }
            val pendingTasks = state.tasks.pendingOccurrences.filter { it.aquariumId == aquarium.id }.take(3)
            if (pendingTasks.isNotEmpty()) {
                item { Text(stringResource(Res.string.tasks_title), style = MaterialTheme.typography.titleLarge) }
                itemsIndexed(pendingTasks, key = { _, task -> task.id }) { _, task ->
                    Card(onClick = viewModel::openTasks, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(task.title, style = MaterialTheme.typography.titleMedium)
                            val formatter = currentLocalizedFormatters()
                            Text(stringResource(Res.string.task_due_value,
                                formatter.date(task.dueDate.year, task.dueDate.month, task.dueDate.day),
                                task.minuteOfDay?.let(formatter::time) ?: stringResource(Res.string.task_no_time)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (state.accountInvitationPrompt == AccountInvitationPrompt.REMINDER) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(Res.string.account_invitation_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(Res.string.account_invitation_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = viewModel::continueAfterSessionConfirmation,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) {
                                Text(stringResource(Res.string.review_account_options))
                            }
                        }
                    }
                }
            }

        }
        TextButton(
            onClick = viewModel::openTasks,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(stringResource(Res.string.open_tasks))
        }
        PrimaryButton(Res.string.new_session, viewModel::openRapidSession)
    }
}

@Composable
private fun sessionSavedDescription(measurementCount: Int, actionCount: Int): String = stringResource(
    Res.string.session_saved_description,
    pluralStringResource(Res.plurals.session_measurement_count, measurementCount, measurementCount),
    pluralStringResource(Res.plurals.session_maintenance_count, actionCount, actionCount),
)

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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
internal fun BuiltInParameter.label(): String = stringResource(
    when (this) {
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
    },
)

@Composable
private fun Aquarium.formattedVolume(): String {
    val formatted = currentLocalizedFormatters().number(volume)
    return when (volumeUnit) {
        VolumeUnit.LITERS -> stringResource(Res.string.volume_liters_format, formatted)
        VolumeUnit.US_GALLONS -> stringResource(Res.string.volume_us_gallons_format, formatted)
    }
}
