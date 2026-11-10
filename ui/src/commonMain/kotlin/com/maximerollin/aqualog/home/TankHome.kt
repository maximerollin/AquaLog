package com.maximerollin.aqualog.home

import com.maximerollin.aqualog.ui.icon.*
import androidx.compose.foundation.background
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.graphics.ImageBitmap
import com.maximerollin.aqualog.photo.PhotoSheets
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.AccountInvitationPrompt
import com.maximerollin.aqualog.HomeOverview
import com.maximerollin.aqualog.HomeUiState
import com.maximerollin.aqualog.HomeViewModel
import com.maximerollin.aqualog.MainDestination
import com.maximerollin.aqualog.currentLocalizedFormatters
import com.maximerollin.aqualog.AquariumAlert
import com.maximerollin.aqualog.homeOverview
import com.maximerollin.aqualog.otherAquariumAlerts
import com.maximerollin.aqualog.joinLabels
import com.maximerollin.aqualog.ProPurchaseStatus
import com.maximerollin.aqualog.shortLabelResource
import com.maximerollin.aqualog.shared.assistance.DueTest
import com.maximerollin.aqualog.shared.assistance.looksCycled
import com.maximerollin.aqualog.PistesSheet
import com.maximerollin.aqualog.overdueLinkedAction
import com.maximerollin.aqualog.pistesAdvices
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.resources.*
import com.maximerollin.aqualog.SEPARATOR
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.SyncFailureCategory
import com.maximerollin.aqualog.shared.SyncStatus
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.TaskOccurrence
import com.maximerollin.aqualog.LinkedTaskTitle
import com.maximerollin.aqualog.todayTasks
import com.maximerollin.aqualog.ui.theme.CarnetSectionTitle
import com.maximerollin.aqualog.ui.theme.FloatingNavBarClearance
import com.maximerollin.aqualog.ui.theme.PaperSheetTop
import com.maximerollin.aqualog.ui.theme.ValueRow
import com.maximerollin.aqualog.ui.theme.attentionTextColor
import com.maximerollin.aqualog.shared.assistance.ReadingSample
import java.time.Instant
import java.util.Locale
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The Tank destination: the water hero with the status card, then the paper sheet: every value, « To do » and the conditional blocks. */
@Composable
fun TankHome(
    state: HomeUiState,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    rateApp: (() -> Unit)? = null,
    onMeasureAvailabilityChange: (Boolean) -> Unit = {},
) {
    val setup = requireNotNull(state.setup)
    val aquarium = setup.aquarium
    val overview = homeOverview(setup, state.latestReadings)
    val today = viewModel.today()
    val todayDate = LocalDate.of(today.year, today.month, today.day)
    val tasks = todayTasks(state.tasks.pendingOccurrences, aquarium.id, today)
    val formatter = currentLocalizedFormatters()
    val gutter = Modifier.padding(horizontal = 16.dp)
    val alerts = otherAquariumAlerts(state.aquariums, aquarium.id, state.tasks.pendingOccurrences, today)
    val pistes = pistesAdvices(
        setup.parameters,
        state.latestReadings.map { ReadingSample(it.parameterDefinitionId, it.value, viewModel.dayOf(it.occurredAtEpochMillis)) },
        aquarium.profile,
        overdueLinkedAction(state.tasks.pendingOccurrences, aquarium.id, MaintenanceActionType.WATER_CHANGE, today),
    )
    var showPicker by remember { mutableStateOf(false) }
    var showHabitants by remember(aquarium.id) { mutableStateOf(false) }
    var showPistes by remember(aquarium.id) { mutableStateOf(false) }
    // null while loading, so the scene does not flash silhouettes; empty when the file is unreadable (silhouettes, species ids).
    val encyclopedia by rememberEncyclopedia()
    val sprites = remember(state.inhabitants, encyclopedia) {
        encyclopedia?.let { species -> sceneSprites(state.inhabitants, species.associateBy { it.id }) }.orEmpty()
    }
    // Every Aquarium's own scene, for the pages of the pager that are not the current one yet.
    val pageScenes = remember(state.inhabitantsByAquarium, encyclopedia) { miniScenes(state.inhabitantsByAquarium, encyclopedia, max = 12) }
    val pageInhabitantCounts = remember(state.inhabitantsByAquarium) { state.inhabitantsByAquarium.mapValues { (_, list) -> list.sumOf { it.count } } }
    val snackbar = remember { SnackbarHostState() }
    val currentIndex = state.aquariums.indexOfFirst { it.id == aquarium.id }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = currentIndex) { state.aquariums.size }
    // Set when a swipe asks for an Aquarium, cleared once it is the current one: independent of the pager's layout.
    var pendingSwipeSelection by remember { mutableStateOf<String?>(null) }
    // Any change of the current Aquarium ends the wait, even when it is not the one the swipe asked for.
    LaunchedEffect(aquarium.id) { pendingSwipeSelection = null }
    LaunchedEffect(state.aquariums) {
        if (state.aquariums.none { it.id == pendingSwipeSelection }) pendingSwipeSelection = null
    }
    val listState = rememberLazyListState()
    val shownAquariums by rememberUpdatedState(state.aquariums)
    val currentId by rememberUpdatedState(aquarium.id)
    // Both effects live here, not in the hero item: the hero can be scrolled off-screen while the Aquarium changes.
    LaunchedEffect(currentIndex, state.aquariums.size) {
        pagerSyncTarget(currentIndex, state.aquariums.size, pager.currentPage)?.let {
            if (listState.layoutInfo.visibleItemsInfo.any { item -> item.index == 0 }) pager.animateScrollToPage(it) else pager.scrollToPage(it)
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            shownAquariums.getOrNull(page)?.takeIf { it.id != currentId && shownAquariums.size > 1 }
                ?.let {
                    pendingSwipeSelection = it.id
                    viewModel.selectAquarium(it.id)
                }
        }
    }
    val canMeasure = measureTargetsShownAquarium(state.aquariums.size, pager.isScrollInProgress, pendingSwipeSelection != null)
    // The shell owns « + »: it only needs to know whether the shown Aquarium can take a Measure.
    LaunchedEffect(canMeasure) { onMeasureAvailabilityChange(canMeasure) }
    val currentHero = CurrentHero(
        overview = overview,
        scene = MiniScene(sprites, decorPlants(state.inhabitants)),
        inhabitantCount = state.inhabitants.sumOf { it.count },
        onOpenPhoto = viewModel::openPhoto,
        onOpenHabitants = { showHabitants = true },
        // Only once the list is loaded, so an Aquarium with inhabitants does not flash the chip.
        onAddHabitants = (viewModel::openAddInhabitant).takeIf { state.inhabitantsLoaded },
        // The card opens the pistes when a value is out of range, else the latest Session.
        onOpenStatus = when {
            pistes.isNotEmpty() -> ({ showPistes = true })
            state.latestSession != null -> viewModel::openSessionDetails
            else -> null
        },
    )

    Box(modifier.fillMaxSize()) {
        // The only scrollable on the home: tests rely on a single node with a scroll action.
        LazyColumn(
            Modifier.fillMaxSize().testTag("home-list"),
            state = listState,
            contentPadding = PaddingValues(bottom = FloatingNavBarClearance.dp),
        ) {
            item(key = "hero") {
                AquariumHeroes(
                    state.aquariums, aquarium, pager, todayDate, currentHero, pageScenes, pageInhabitantCounts,
                    viewModel::aquariumPhoto, viewModel::cachedAquariumPhoto,
                    onChangeAquarium = { showPicker = true },
                    // The sheet's top edge, the next item, covers the water's bottom.
                    modifier = Modifier.overlapNext(30.dp),
                )
            }
            item(key = "sheet-top") { PaperSheetTop() }
            sheetItem("values") { ValueRow(overview.all, formatter, Modifier.padding(bottom = 8.dp)) }
            if (state.maintenanceSeries >= 1) {
                sheetItem("maintenance-series") {
                    Text(
                        pluralStringResource(Res.plurals.series_weeks, state.maintenanceSeries, state.maintenanceSeries),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = gutter.padding(top = 4.dp),
                    )
                }
            }
            sheetItem("to-do-title") {
                CarnetSectionTitle(
                    stringResource(Res.string.home_to_do),
                    gutter.padding(top = 8.dp),
                    actionLabel = stringResource(Res.string.home_see_all),
                    onAction = viewModel::openTasks,
                )
            }
            if (state.dueTests.isNotEmpty()) {
                sheetItem("due-tests") {
                    DueTestsRow(
                        state.dueTests,
                        enabled = canMeasure,
                        onClick = { viewModel.openRapidSession(state.dueTests.map { it.definition.id }) },
                        modifier = gutter,
                    )
                }
            }
            if (tasks.due.isEmpty()) {
                sheetItem("no-task-due") {
                    Text(
                        tasks.next?.let {
                            stringResource(
                                Res.string.home_nothing_due,
                                it.title,
                                formatter.date(it.dueDate.year, it.dueDate.month, it.dueDate.day),
                            )
                        } ?: stringResource(Res.string.no_pending_tasks),
                        modifier = gutter,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(tasks.due, key = { it.id }) { task ->
                    OnPaper { TaskRow(task, today, onComplete = { onFailed -> viewModel.completeTask(task, onFailed) }) }
                }
            }
            if (state.accountInvitationPrompt == AccountInvitationPrompt.AFTER_SESSION_CONFIRMATION) {
                sheetItem("continue-after-session") {
                    Button(
                        onClick = viewModel::continueAfterSessionConfirmation,
                        shape = MaterialTheme.shapes.medium,
                        modifier = gutter.padding(top = 8.dp).fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(stringResource(Res.string.continue_after_session)) }
                }
            }
            if (state.cycledDismissalsLoaded && looksCycled(setup, state.latestReadings) && state.latestSession?.session?.id != state.cycledDismissedSessionId) {
                sheetItem("cycled-proposal") {
                    CycledProposalCard(viewModel::switchToEstablished, viewModel::dismissCycledProposal, gutter)
                }
            }
            items(alerts, key = { "alert-${it.aquarium.id}" }) { alert ->
                OnPaper { AlertRow(alert, onClick = { viewModel.selectAquarium(alert.aquarium.id) }, modifier = gutter) }
            }
            state.weeklySummary?.let { summary ->
                sheetItem("weekly-summary") {
                    WeeklySummaryCard(summary, state.pro.purchaseStatus == ProPurchaseStatus.ACTIVE, viewModel::openProPaywall, gutter)
                }
            }
            if (state.syncState.status == SyncStatus.FAILED) {
                sheetItem("sync-failure") { SyncFailure(state, viewModel, gutter) }
            }
            if (state.accountInvitationPrompt == AccountInvitationPrompt.REMINDER) {
                sheetItem("account-reminder") {
                    Column(gutter.padding(top = 8.dp)) {
                        CarnetSectionTitle(stringResource(Res.string.account_invitation_title))
                        Text(
                            stringResource(Res.string.account_invitation_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = viewModel::continueAfterSessionConfirmation,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text(stringResource(Res.string.review_account_options)) }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = FloatingNavBarClearance.dp))
    }
    if (showPistes && pistes.isNotEmpty()) PistesSheet(pistes, today, onDismiss = { showPistes = false })
    PhotoSheets(state, viewModel, snackbar)
    if (state.pistesAfterSave.isNotEmpty()) PistesSheet(state.pistesAfterSave, viewModel.today(), viewModel::dismissPistes)
    if (state.reviewDiscoveryVisible) {
        androidx.compose.material3.AlertDialog(
            // Dismissing is « Plus tard ». The confirm button opens the store page: the In-App Review API is never a call-to-action's target.
            onDismissRequest = { viewModel.answerReviewDiscovery(leaveReview = false) },
            title = { Text(stringResource(Res.string.review_discovery_title)) },
            text = { Text(stringResource(Res.string.review_discovery_text)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.answerReviewDiscovery(leaveReview = true, openStorePage = rateApp) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(Res.string.review_discovery_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.answerReviewDiscovery(leaveReview = false) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(Res.string.review_discovery_later)) }
            },
        )
    }
    if (showHabitants) {
        HabitantsSheet(
            groups = state.inhabitants,
            encyclopedia = encyclopedia.orEmpty(),
            language = remember { Locale.getDefault().language },
            onRemove = viewModel::removeInhabitants,
            onAdd = {
                showHabitants = false
                viewModel.openAddInhabitant()
            },
            onDismiss = { showHabitants = false },
        )
    }
    if (showPicker) {
        AquariumPickerSheet(
            aquariums = state.aquariums,
            currentId = aquarium.id,
            onSelect = { id ->
                showPicker = false
                if (id != aquarium.id) viewModel.selectAquarium(id)
            },
            onAdd = {
                showPicker = false
                viewModel.openCreateAquarium()
            },
            onDismiss = { showPicker = false },
            loadPhoto = viewModel::aquariumPhoto,
            cachedPhoto = viewModel::cachedAquariumPhoto,
            inhabitantsByAquarium = state.inhabitantsByAquarium,
        )
    }
}

/**
 * « Measure » records into the current Aquarium, so it is only live while the scene shows that one:
 * not mid-swipe, and not between a swipe asking for another Aquarium and the selection catching up.
 */
internal fun measureTargetsShownAquarium(aquariumCount: Int, scrolling: Boolean, swipeSelectionPending: Boolean): Boolean =
    aquariumCount < 2 || (!scrolling && !swipeSelectionPending)

/** The shell's « + » is live on every tab but the Tank's, where it follows the home's [measureTargetsShownAquarium] guard. */
internal fun plusEnabled(destination: MainDestination, tankCanMeasure: Boolean): Boolean =
    destination != MainDestination.TANK || tankCanMeasure

/** The page the pager must scroll to so it shows the current Aquarium, or null when it already does. */
internal fun pagerSyncTarget(currentIndex: Int, pageCount: Int, currentPage: Int): Int? =
    currentIndex.takeIf { pageCount > 1 && it in 0 until pageCount && it != currentPage }

/** What the hero shows of the current Aquarium, and its ways out; the pager's other pages get none of the actions. */
private class CurrentHero(
    val overview: HomeOverview,
    val scene: MiniScene,
    val inhabitantCount: Int,
    val onOpenPhoto: () -> Unit,
    val onOpenHabitants: () -> Unit,
    val onAddHabitants: (() -> Unit)?,
    val onOpenStatus: (() -> Unit)?,
)

/** One hero, or a pager of heroes with white dots on the water when there are several Aquariums; settling on a page selects it. */
@Composable
private fun AquariumHeroes(
    aquariums: List<Aquarium>,
    current: Aquarium,
    pager: PagerState,
    today: LocalDate,
    hero: CurrentHero,
    pageScenes: Map<String, MiniScene>,
    pageInhabitantCounts: Map<String, Int>,
    loadPhoto: suspend (String) -> ImageBitmap?,
    cachedPhoto: (String) -> ImageBitmap?,
    onChangeAquarium: () -> Unit,
    modifier: Modifier,
) {
    if (aquariums.size < 2) {
        HeroPage(current, current, today, hero, pageScenes, pageInhabitantCounts, loadPhoto, cachedPhoto, onChangeAquarium, modifier)
        return
    }
    Box(modifier) {
        HorizontalPager(pager, Modifier.testTag("home-pager")) { page ->
            HeroPage(aquariums[page], current, today, hero, pageScenes, pageInhabitantCounts, loadPhoto, cachedPhoto, onChangeAquarium)
        }
        PagerDots(aquariums.size, pager.currentPage, onWater = true, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp))
    }
}

@Composable
private fun HeroPage(
    aquarium: Aquarium,
    current: Aquarium,
    today: LocalDate,
    hero: CurrentHero,
    pageScenes: Map<String, MiniScene>,
    pageInhabitantCounts: Map<String, Int>,
    loadPhoto: suspend (String) -> ImageBitmap?,
    cachedPhoto: (String) -> ImageBitmap?,
    onChangeAquarium: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The status, the photo flow and the habitants sheet belong to the current Aquarium: a page still sliding in has none
    // of them, but it draws its own Aquarium's inhabitants. It is only seen mid-swipe, so it stays still (one frame loop).
    val isCurrent = aquarium.id == current.id
    val photoId = aquarium.photoId
    var photo by remember(photoId) { mutableStateOf(photoId?.let(cachedPhoto)) }
    LaunchedEffect(photoId) { if (photoId != null && photo == null) photo = loadPhoto(photoId) }
    HomeHero(
        aquarium = aquarium,
        overview = hero.overview.takeIf { isCurrent },
        today = today,
        inhabitantCount = if (isCurrent) hero.inhabitantCount else pageInhabitantCounts[aquarium.id] ?: 0,
        scene = if (isCurrent) hero.scene else pageScenes[aquarium.id] ?: MiniScene.Empty,
        still = !isCurrent,
        photo = photo,
        onChangeAquarium = onChangeAquarium,
        onOpenPhoto = hero.onOpenPhoto.takeIf { isCurrent },
        onOpenHabitants = hero.onOpenHabitants.takeIf { isCurrent },
        onAddHabitants = hero.onAddHabitants.takeIf { isCurrent },
        onOpenStatus = hero.onOpenStatus.takeIf { isCurrent },
        modifier = modifier,
    )
}

/** The page dots, read as one « 2 / 3 »: the individual dots carry no semantics (the current one is shown by colour only). */
@Composable
internal fun PagerDots(count: Int, current: Int, onWater: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(top = 8.dp).semantics(mergeDescendants = true) { stateDescription = "${current + 1} / $count" },
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { index ->
            val color = when {
                onWater -> if (index == current) Color.White else Color.White.copy(alpha = .45f)
                index == current -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Box(Modifier.padding(horizontal = 4.dp).size(8.dp).background(color, CircleShape).clearAndSetSemantics {})
        }
    }
}

/** A list item on the paper sheet: opaque, so the water never shows between items. */
private fun LazyListScope.sheetItem(key: String, content: @Composable () -> Unit) = item(key = key) { OnPaper(content) }

@Composable
private fun OnPaper(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) { content() }
}

/** « {Aquarium} · {Task} · {overdue|today} › », one row per other Aquarium with something due. */
@Composable
private fun AlertRow(alert: AquariumAlert, onClick: () -> Unit, modifier: Modifier) {
    val due = stringResource(if (alert.isOverdue) Res.string.home_task_overdue else Res.string.home_alert_due_today)
    Row(
        Modifier.clickable(role = Role.Button, onClickLabel = stringResource(Res.string.home_change_aquarium), onClick = onClick)
            .then(modifier).fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildAnnotatedString {
                append(alert.aquarium.name + SEPARATOR + alert.occurrence.title + SEPARATOR)
                withStyle(SpanStyle(color = if (alert.isOverdue) attentionTextColor() else MaterialTheme.colorScheme.onSurfaceVariant)) {
                    append(due)
                }
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Icon(
            AquagendaIcons.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp).clearAndSetSemantics { },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A one-time proposal: a Cycling Aquarium whose ammonia and nitrite read 0 may move to the established rhythm. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CycledProposalCard(onSwitch: () -> Unit, onLater: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(Res.string.cycled_proposal_text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
            Button(onClick = onSwitch, shape = MaterialTheme.shapes.medium, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.cycled_proposal_switch))
            }
            TextButton(onClick = onLater, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.cycled_proposal_later))
            }
        }
    }
}

/** « Tester NO₃ et pH ›»: one row for every due test of the Aquarium, opening entry with those parameters first. */
@Composable
private fun DueTestsRow(tests: List<DueTest>, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val labels = joinLabels(tests.map { stringResource(it.definition.parameter.shortLabelResource()) }) { first, last ->
        stringResource(Res.string.list_pair, first, last)
    }
    Row(
        Modifier.clickable(enabled = enabled, role = Role.Button, onClickLabel = stringResource(Res.string.measure_action), onClick = onClick)
            .then(modifier).fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(AquagendaIcons.Droplet, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(Res.string.due_tests_row, labels),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Icon(
            AquagendaIcons.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TaskRow(task: TaskOccurrence, today: TaskDate, onComplete: (onFailed: () -> Unit) -> Unit) {
    // Guards against a double tap resolving the occurrence twice before the list refreshes.
    var completed by remember(task.id) { mutableStateOf(false) }
    val formatter = currentLocalizedFormatters()
    val description = "${stringResource(Res.string.task_complete)}, ${task.title}"
    val overdue = compareValuesBy(task.dueDate, today, TaskDate::year, TaskDate::month, TaskDate::day) < 0
    Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = completed,
            onCheckedChange = if (completed) null else { _ -> completed = true; onComplete { completed = false } },
            modifier = Modifier.padding(start = 4.dp).semantics { contentDescription = description },
        )
        Column(Modifier.weight(1f)) {
            LinkedTaskTitle(task.title, task.maintenanceActionType)
            Text(
                if (overdue) {
                    stringResource(Res.string.home_task_overdue)
                } else {
                    stringResource(
                        Res.string.task_due_value,
                        formatter.date(task.dueDate.year, task.dueDate.month, task.dueDate.day),
                        task.minuteOfDay?.let(formatter::time) ?: stringResource(Res.string.task_no_time),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (overdue) attentionTextColor() else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SyncFailure(state: HomeUiState, viewModel: HomeViewModel, modifier: Modifier) {
    Column(modifier.padding(top = 8.dp)) {
        CarnetSectionTitle(stringResource(Res.string.sync_status_failed))
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
        state.syncState.failure?.technicalDetail?.takeIf(String::isNotBlank)?.let { detail ->
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(
            onClick = viewModel::retrySynchronization,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(Res.string.sync_retry)) }
    }
}

private fun localDate(epochMillis: Long): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

// A Session saved a moment "in the future" (clock skew) still reads as today.
internal fun daysSince(epochMillis: Long, today: LocalDate): Int =
    ChronoUnit.DAYS.between(localDate(epochMillis), today).toInt().coerceAtLeast(0)
