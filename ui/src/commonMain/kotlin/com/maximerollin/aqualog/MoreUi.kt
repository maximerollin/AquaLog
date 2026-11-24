package com.maximerollin.aqualog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximerollin.aqualog.home.AquariumPickerSheet
import com.maximerollin.aqualog.home.HabitantsSheet
import com.maximerollin.aqualog.home.TankScene
import com.maximerollin.aqualog.home.aquariumContextLine
import com.maximerollin.aqualog.home.rememberEncyclopedia
import com.maximerollin.aqualog.resources.*
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.LocalCopyDecision
import com.maximerollin.aqualog.shared.PortableImportProblem
import com.maximerollin.aqualog.shared.SyncStatus
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.ui.icon.*
import com.maximerollin.aqualog.ui.theme.FilledField
import com.maximerollin.aqualog.ui.theme.FloatingNavBarClearance
import com.maximerollin.aqualog.ui.theme.GlassChip
import com.maximerollin.aqualog.ui.theme.GlassIconButton
import com.maximerollin.aqualog.ui.theme.GroupLabel
import com.maximerollin.aqualog.ui.theme.PaperSheetTop
import com.maximerollin.aqualog.ui.theme.PeriodSegmented
import com.maximerollin.aqualog.ui.theme.SettingsGroup
import com.maximerollin.aqualog.ui.theme.SettingsRow
import com.maximerollin.aqualog.ui.theme.WaterHeader
import com.maximerollin.aqualog.ui.theme.frauncesFamily
import com.maximerollin.aqualog.ui.theme.overlapNext
import java.util.Locale
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The More tab: the Aquarium's water with its name, then its settings, the offer, data and account, help, account actions. */
@Composable
fun MoreScreen(
    state: HomeUiState,
    viewModel: HomeViewModel,
    onExportData: () -> Unit,
    externalLinks: ExternalLinks,
    rateApp: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // Archiving cannot be undone from the app, so it is never a single tap.
    var confirmArchive by remember { mutableStateOf(false) }
    MoreContent(
        state = state,
        actions = MoreActions(
            onEditAquarium = viewModel::openEditAquarium,
            onSelectAquarium = viewModel::selectAquarium,
            onAddAquarium = viewModel::openCreateAquarium,
            onOpenRhythm = viewModel::openRhythmSettings,
            onOpenParameters = viewModel::openParameterSettings,
            onOpenCalculators = viewModel::openCalculators,
            onOpenTasks = viewModel::openTasks,
            onRemoveInhabitants = viewModel::removeInhabitants,
            onAddInhabitant = viewModel::openAddInhabitant,
            onOpenPaywall = viewModel::openProPaywall,
            onExportData = onExportData,
            onRequestImport = viewModel::requestPortableImport,
            onReauthenticateDeletionWithGoogle = viewModel::reauthenticateAccountDeletionWithGoogle,
            onRequestDeletionMagicLink = viewModel::requestDeletionMagicLink,
            onRetryAccountDeletion = viewModel::retryAccountDeletion,
            onOpenFeedback = { viewModel.openFeedback() },
            rateApp = rateApp,
            onShowAccountDialog = viewModel::showAccountSettingsDialog,
            onArchive = { confirmArchive = true },
            onUnarchive = viewModel::unarchiveAquarium,
            loadPhoto = viewModel::aquariumPhoto,
            cachedPhoto = viewModel::cachedAquariumPhoto,
        ),
        modifier = modifier,
    )
    AccountSettingsDialog(state.accountSettings.dialog, viewModel, externalLinks)
    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text(stringResource(Res.string.archive_aquarium_title)) },
            text = { Text(stringResource(Res.string.archive_aquarium_description)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmArchive = false
                        viewModel.archiveCurrentAquarium()
                    },
                ) { Text(stringResource(Res.string.archive_aquarium_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmArchive = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}

/** What More's rows and chips ask for; [MoreScreen] wires them to the [HomeViewModel], a test to fakes. */
internal class MoreActions(
    val onEditAquarium: () -> Unit = {},
    val onSelectAquarium: (String) -> Unit = {},
    val onAddAquarium: () -> Unit = {},
    val onOpenRhythm: () -> Unit = {},
    val onOpenParameters: () -> Unit = {},
    val onOpenCalculators: () -> Unit = {},
    val onUnarchive: (aquariumId: String) -> Unit = {},
    val onOpenTasks: () -> Unit = {},
    val onRemoveInhabitants: (inhabitantId: String, count: Int, onFailed: () -> Unit) -> Unit = { _, _, _ -> },
    val onAddInhabitant: () -> Unit = {},
    val onOpenPaywall: () -> Unit = {},
    val onExportData: () -> Unit = {},
    val onRequestImport: () -> Unit = {},
    val onReauthenticateDeletionWithGoogle: () -> Unit = {},
    val onRequestDeletionMagicLink: () -> Unit = {},
    val onRetryAccountDeletion: () -> Unit = {},
    val onOpenFeedback: () -> Unit = {},
    val rateApp: (() -> Unit)? = null,
    val onShowAccountDialog: (AccountSettingsDialog) -> Unit = {},
    val onArchive: () -> Unit = {},
    val loadPhoto: suspend (String) -> ImageBitmap? = { null },
    val cachedPhoto: (String) -> ImageBitmap? = { null },
)

/** The deep water of the Pro banner, deep at the text and fading to the right so the scene shows through. */
internal val ProBannerWater = Color(0xFF0F4A43)
internal const val ProBannerShadeStart = .9f
internal const val ProBannerShadeEnd = .2f

/** The banner shade's alpha at [fraction] of the card's width, left to right. */
internal fun proBannerShadeAlpha(fraction: Float): Float = ProBannerShadeStart + (ProBannerShadeEnd - ProBannerShadeStart) * fraction

/** The text column's share of the card: the shade up to there keeps white text at 4.5:1 over the scene (ScenePhysicsTest). */
internal const val ProBannerTextFraction = .6f

/** Space under the banner's text: more than the scene's sand (16 dp at most), its lightest colour, so no text lies on it. */
internal val ProBannerTextBottom = 18.dp

/** More drawn from [state] alone: the single scrollable `settings-list`, under the status bar, clear of the floating bar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MoreContent(state: HomeUiState, actions: MoreActions, modifier: Modifier = Modifier) {
    val setup = requireNotNull(state.setup)
    val aquarium = setup.aquarium
    val account = state.accountSettings
    val portable = state.portableData
    val proActive = state.pro.purchaseStatus == ProPurchaseStatus.ACTIVE
    val inhabitantCount = state.inhabitants.sumOf { it.count }
    val pendingTasks = state.tasks.pendingOccurrences.count { it.aquariumId == aquarium.id }
    val formatter = currentLocalizedFormatters()
    var showPicker by remember { mutableStateOf(false) }
    var showHabitants by remember(aquarium.id) { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    val photoId = aquarium.photoId
    var photo by remember(photoId) { mutableStateOf(photoId?.let(actions.cachedPhoto)) }
    LaunchedEffect(photoId) { if (photoId != null && photo == null) photo = actions.loadPhoto(photoId) }
    val listState = rememberLazyListState()
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("settings-list"),
            state = listState,
            contentPadding = PaddingValues(bottom = FloatingNavBarClearance.dp),
        ) {
            item(key = "header") {
                WaterHeader(
                    aquarium = aquarium,
                    title = aquarium.name,
                    subtitle = aquariumContextLine(aquarium, inhabitantCount),
                    minHeight = 190.dp,
                    photo = photo,
                    action = { GlassIconButton(AquagendaIcons.Pencil, stringResource(Res.string.edit_aquarium), actions.onEditAquarium) },
                    below = {
                        // At large font sizes the second chip wraps under the first instead of being cut.
                        FlowRow(
                            Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            GlassChip(stringResource(Res.string.home_change_aquarium), onClick = { showPicker = true })
                            GlassChip(stringResource(Res.string.add_aquarium), actions.onAddAquarium, icon = AquagendaIcons.Plus)
                        }
                    },
                    // The sheet's top edge, the next item, covers the water's bottom.
                    modifier = Modifier.overlapNext(30.dp),
                )
            }
            item(key = "sheet-top") { PaperSheetTop() }
            groupItem("aquarium") {
                SettingsGroup {
                    SettingsRow(
                        stringResource(Res.string.habitants_title), AquagendaIcons.Fish,
                        value = inhabitantCount.takeIf { it > 0 }?.let { formatter.number(it.toDouble(), 0) },
                        onClick = { showHabitants = true },
                    )
                    SettingsRow(
                        stringResource(Res.string.measured_parameters_title), AquagendaIcons.FlaskConical,
                        onClick = actions.onOpenParameters,
                    )
                    SettingsRow(stringResource(Res.string.rhythm_title), AquagendaIcons.Calendar, onClick = actions.onOpenRhythm)
                    SettingsRow(
                        stringResource(Res.string.calculators_title), AquagendaIcons.Droplet,
                        onClick = actions.onOpenCalculators,
                    )
                    SettingsRow(
                        stringResource(Res.string.open_tasks), AquagendaIcons.Bell,
                        value = pendingTasks.takeIf { it > 0 }?.let { formatter.number(it.toDouble(), 0) },
                        onClick = actions.onOpenTasks,
                    )
                }
            }
            if (!proActive) groupItem("pro-banner") { ProBanner(aquarium, actions.onOpenPaywall) }
            labelItem("data-label", Res.string.more_section_data_account)
            groupItem("data") {
                SettingsGroup {
                    if (proActive) {
                        SettingsRow(
                            stringResource(Res.string.pro_active), AquagendaIcons.Sparkles,
                            supporting = stringResource(Res.string.subscription_settings_description),
                            onClick = actions.onOpenPaywall,
                        )
                    }
                    SettingsRow(
                        account.identity?.email ?: stringResource(Res.string.account_local_only), AquagendaIcons.Cloud,
                        value = stringResource(
                            when (state.syncState.status) {
                                SyncStatus.SYNCED -> Res.string.sync_status_synced
                                SyncStatus.PENDING -> Res.string.sync_status_pending
                                SyncStatus.FAILED -> Res.string.sync_status_failed
                            },
                        ),
                    )
                    if (account.pendingDeletion != null) {
                        SettingsRow(
                            stringResource(Res.string.account_deletion_pending), icon = null,
                            supporting = account.localizedSafeDetail() ?: stringResource(Res.string.account_deletion_pending_description),
                        )
                        if (account.showDeletionReauthenticationAction) {
                            SettingsRow(
                                stringResource(Res.string.continue_with_google), icon = null,
                                onClick = actions.onReauthenticateDeletionWithGoogle, enabled = !account.isWorking,
                            )
                            SettingsRow(
                                stringResource(Res.string.send_deletion_magic_link), icon = null,
                                onClick = actions.onRequestDeletionMagicLink, enabled = !account.isWorking,
                            )
                        } else {
                            SettingsRow(
                                stringResource(Res.string.retry_account_deletion), icon = null,
                                onClick = actions.onRetryAccountDeletion, enabled = !account.isWorking,
                            )
                        }
                    }
                    Text(
                        stringResource(Res.string.portable_data_replace_explanation),
                        modifier = Modifier.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (portable.message != null || portable.importProblem != null) {
                        Box(Modifier.padding(vertical = 12.dp)) { PortableDataMessage(portable) }
                    }
                    SettingsRow(
                        stringResource(Res.string.export_data), AquagendaIcons.Download,
                        onClick = actions.onExportData, enabled = !portable.isWorking,
                    )
                    SettingsRow(
                        stringResource(Res.string.restore_from_export), AquagendaIcons.Upload,
                        onClick = actions.onRequestImport, enabled = !portable.isWorking,
                    )
                }
            }
            labelItem("help-label", Res.string.more_section_help)
            groupItem("help") {
                SettingsGroup {
                    SettingsRow(stringResource(Res.string.send_feedback), AquagendaIcons.MessageSquare, onClick = actions.onOpenFeedback)
                    actions.rateApp?.let { SettingsRow(stringResource(Res.string.rate_app), AquagendaIcons.Star, onClick = it) }
                    SettingsRow(
                        stringResource(Res.string.privacy_policy), AquagendaIcons.Shield,
                        onClick = { actions.onShowAccountDialog(AccountSettingsDialog.PRIVACY) },
                    )
                    SettingsRow(
                        stringResource(Res.string.support), AquagendaIcons.CircleHelp,
                        onClick = { actions.onShowAccountDialog(AccountSettingsDialog.SUPPORT) },
                    )
                }
            }
            labelItem("actions-label", Res.string.account_actions)
            groupItem("actions") {
                SettingsGroup {
                    if (account.identity != null && account.pendingDeletion == null) {
                        SettingsRow(
                            stringResource(Res.string.sign_out), AquagendaIcons.LogOut,
                            onClick = { actions.onShowAccountDialog(AccountSettingsDialog.LOGOUT) }, enabled = !account.isWorking,
                        )
                        SettingsRow(
                            stringResource(Res.string.delete_account), AquagendaIcons.Trash,
                            onClick = { actions.onShowAccountDialog(AccountSettingsDialog.DELETE) }, enabled = !account.isWorking,
                            labelColor = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        SettingsRow(stringResource(Res.string.account_local_only_description), icon = null)
                    }
                    SettingsRow(
                        stringResource(Res.string.archive_aquarium), AquagendaIcons.Archive,
                        onClick = actions.onArchive,
                        enabled = state.aquariums.size > 1,
                        supporting = if (proActive) null else stringResource(Res.string.free_aquarium_limit),
                    )
                    if (state.archivedAquariums.isNotEmpty()) {
                        SettingsRow(
                            stringResource(Res.string.archived_aquariums), AquagendaIcons.Archive,
                            value = formatter.number(state.archivedAquariums.size.toDouble(), 0),
                            onClick = { showArchived = true },
                        )
                    }
                }
            }
            item(key = "footer") {
                OnPaper {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (portable.isWorking || account.isWorking) CircularProgressIndicator()
                        account.localizedSafeDetail()?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
        StatusBarOverHeader(listState)
    }
    if (showHabitants) {
        val encyclopedia by rememberEncyclopedia()
        HabitantsSheet(
            groups = state.inhabitants,
            encyclopedia = encyclopedia.orEmpty(),
            language = remember { Locale.getDefault().language },
            aquariumName = aquarium.name,
            onRemove = actions.onRemoveInhabitants,
            onAdd = {
                showHabitants = false
                actions.onAddInhabitant()
            },
            onDismiss = { showHabitants = false },
        )
    }
    if (showArchived && state.archivedAquariums.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showArchived = false },
            title = { Text(stringResource(Res.string.archived_aquariums)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).testTag("archived-aquariums")) {
                    state.archivedAquariums.forEach { archived ->
                        val restoreDescription = stringResource(Res.string.restore_aquarium_named, archived.name)
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(archived.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            TextButton(
                                onClick = { actions.onUnarchive(archived.id) },
                                modifier = Modifier.semantics { contentDescription = restoreDescription },
                            ) { Text(stringResource(Res.string.restore_aquarium)) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showArchived = false }) { Text(stringResource(Res.string.close)) }
            },
        )
    }
    if (showPicker) {
        AquariumPickerSheet(
            aquariums = state.aquariums,
            currentId = aquarium.id,
            onSelect = { id ->
                showPicker = false
                if (id != aquarium.id) actions.onSelectAquarium(id)
            },
            onAdd = {
                showPicker = false
                actions.onAddAquarium()
            },
            onDismiss = { showPicker = false },
            loadPhoto = actions.loadPhoto,
            cachedPhoto = actions.cachedPhoto,
            inhabitantsByAquarium = state.inhabitantsByAquarium,
        )
    }
}

/** A sheet item: paper behind it edge to edge, then 14 dp before the next one; the group sets its own side margins. */
private fun LazyListScope.groupItem(key: String, content: @Composable () -> Unit) =
    item(key = key) { OnPaper { Box(Modifier.padding(bottom = 14.dp)) { content() } } }

private fun LazyListScope.labelItem(key: String, text: StringResource) =
    item(key = key) { OnPaper { GroupLabel(stringResource(text), Modifier.padding(top = 8.dp, bottom = 8.dp)) } }

@Composable
private fun OnPaper(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) { content() }
}

/** Free only: a still scene under deep water, « Gratuit », the Pro promise and « Découvrir Pro »; one button to the offer. */
@Composable
private fun ProBanner(aquarium: Aquarium, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(min = 112.dp).clip(shape)
            .clickable(role = Role.Button, onClick = onOpen),
    ) {
        TankScene(aquarium, inhabitants = emptyList(), modifier = Modifier.matchParentSize(), decorPlants = 0, animate = false)
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(listOf(ProBannerWater.copy(alpha = ProBannerShadeStart), ProBannerWater.copy(alpha = ProBannerShadeEnd))),
            ),
        )
        // The text stays where the shade is deep enough: long titles wrap within the first 60 % and the card grows.
        Column(Modifier.fillMaxWidth(ProBannerTextFraction).padding(start = 16.dp, top = 14.dp, bottom = ProBannerTextBottom)) {
            Text(
                stringResource(Res.string.free_plan).uppercase(),
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
            )
            Text(
                stringResource(Res.string.more_pro_banner_title),
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = frauncesFamily(), fontSize = 19.sp),
                color = Color.White,
            )
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.more_pro_banner_action),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                )
                Icon(
                    AquagendaIcons.ChevronRight, contentDescription = null, tint = Color.White,
                    modifier = Modifier.padding(start = 4.dp).size(16.dp),
                )
            }
        }
    }
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

/** The outcome of the last export or restore, or why the file was refused; nothing when there is neither. */
@Composable
internal fun PortableDataMessage(
    state: PortableDataUiState,
    modifier: Modifier = Modifier,
    colorOverride: Color? = null,
) {
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
        PortableImportProblem.INVALID_PHOTO -> Res.string.portable_problem_photo
        PortableImportProblem.INVALID_PARAMETERS -> Res.string.portable_problem_parameters
        PortableImportProblem.INVALID_SESSIONS -> Res.string.portable_problem_sessions
        PortableImportProblem.INVALID_MEASUREMENTS -> Res.string.portable_problem_measurements
        PortableImportProblem.INVALID_ACTIONS -> Res.string.portable_problem_actions
        PortableImportProblem.INVALID_EVENTS -> Res.string.portable_problem_events
        PortableImportProblem.INVALID_TASKS -> Res.string.portable_problem_tasks
        PortableImportProblem.INVALID_OCCURRENCES -> Res.string.portable_problem_occurrences
        PortableImportProblem.INVALID_INHABITANTS -> Res.string.portable_problem_inhabitants
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
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = colorOverride ?: if (
            state.message == PortableDataMessage.EXPORT_READY ||
            state.message == PortableDataMessage.IMPORT_COMPLETE
        ) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        },
    )
}

private val EditorUnits = listOf(VolumeUnit.LITERS, VolumeUnit.US_GALLONS)

/** Creates or edits an Aquarium: filled name and volume fields, then litres or US gallons as a two-option segment. */
@Composable
internal fun AquariumEditorDialog(
    editor: AquariumEditorUiState,
    onNameChange: (String) -> Unit,
    onVolumeChange: (String) -> Unit,
    onUnitChange: (VolumeUnit) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val invalid = stringResource(Res.string.invalid_aquarium)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (editor.aquariumId == null) Res.string.add_aquarium else Res.string.edit_aquarium))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledField(
                    value = editor.name,
                    onValueChange = onNameChange,
                    label = stringResource(Res.string.aquarium_name_label),
                    isError = editor.showValidationError,
                    errorMessage = invalid,
                )
                FilledField(
                    value = editor.volume,
                    onValueChange = onVolumeChange,
                    label = stringResource(Res.string.aquarium_volume_label),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = editor.showValidationError,
                    errorMessage = invalid,
                )
                PeriodSegmented(
                    labels = EditorUnits.map { stringResource(if (it == VolumeUnit.LITERS) Res.string.unit_liters else Res.string.unit_us_gallons) },
                    selectedIndex = EditorUnits.indexOf(editor.volumeUnit),
                    onSelect = { onUnitChange(EditorUnits[it]) },
                )
                if (editor.showValidationError) Text(invalid, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !editor.isSaving) {
                Text(stringResource(Res.string.save_aquarium))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
