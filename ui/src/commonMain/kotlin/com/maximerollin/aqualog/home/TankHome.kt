package com.maximerollin.aqualog.home

import com.maximerollin.aqualog.ui.icon.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.graphics.ImageBitmap
import com.maximerollin.aqualog.photo.AquariumThumbnail
import com.maximerollin.aqualog.photo.PhotoSheets
import com.maximerollin.aqualog.ui.theme.CarnetLightColors
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.AccountInvitationPrompt
import com.maximerollin.aqualog.HomeOverview
import com.maximerollin.aqualog.HomeUiState
import com.maximerollin.aqualog.HomeViewModel
import com.maximerollin.aqualog.MainDestination
import com.maximerollin.aqualog.currentLocalizedFormatters
import com.maximerollin.aqualog.formattedVolume
import com.maximerollin.aqualog.AquariumAlert
import com.maximerollin.aqualog.homeOverview
import com.maximerollin.aqualog.otherAquariumAlerts
import com.maximerollin.aqualog.PistesCard
import com.maximerollin.aqualog.joinLabels
import com.maximerollin.aqualog.ProPurchaseStatus
import com.maximerollin.aqualog.shortLabelResource
import com.maximerollin.aqualog.shared.assistance.DueTest
import com.maximerollin.aqualog.shared.assistance.looksCycled
import com.maximerollin.aqualog.PistesSheet
import com.maximerollin.aqualog.overdueLinkedAction
import com.maximerollin.aqualog.pistesAdvices
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.label
import com.maximerollin.aqualog.resources.*
import com.maximerollin.aqualog.SEPARATOR
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.SyncFailureCategory
import com.maximerollin.aqualog.shared.SyncStatus
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.TaskOccurrence
import com.maximerollin.aqualog.LinkedTaskTitle
import com.maximerollin.aqualog.todayTasks
import com.maximerollin.aqualog.ui.theme.CarnetSectionTitle
import com.maximerollin.aqualog.ui.theme.ReadingValue
import com.maximerollin.aqualog.shared.assistance.ReadingLevel
import com.maximerollin.aqualog.ui.theme.attentionMarkColor
import com.maximerollin.aqualog.ui.theme.levelMarkColor
import com.maximerollin.aqualog.ui.theme.attentionTextColor
import com.maximerollin.aqualog.shared.assistance.ReadingSample
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The Tank destination: scene, status, latest value per parameter, today's Tasks and the Measure button. */
@Composable
fun TankHome(state: HomeUiState, viewModel: HomeViewModel, modifier: Modifier = Modifier) {
    val setup = requireNotNull(state.setup)
    val aquarium = setup.aquarium
    val overview = homeOverview(setup, state.latestReadings)
    val today = viewModel.today()
    val todayDate = LocalDate.of(today.year, today.month, today.day)
    val tasks = todayTasks(state.tasks.pendingOccurrences, aquarium.id, today)
    val columns = if (LocalDensity.current.fontScale >= 1.3f) 2 else 3
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
    // Both effects live here, not in the scene item: the scene can be scrolled off-screen while the Aquarium changes.
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

    Box(modifier.fillMaxSize()) {
        // The only scrollable on the home: tests rely on a single node with a scroll action.
        LazyColumn(
            Modifier.fillMaxSize().testTag("home-list"),
            state = listState,
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            item {
                AquariumScene(state.aquariums, aquarium, pager, viewModel::openPhoto, viewModel::aquariumPhoto) { showPicker = true }
            }
            item {
                StatusLine(
                    overview = overview,
                    hasSession = state.latestSession != null,
                    today = todayDate,
                    onOpenSession = viewModel::openSessionDetails,
                    modifier = gutter,
                )
            }
            if (state.maintenanceSeries >= 1) {
                item(key = "maintenance-series") {
                    Text(
                        pluralStringResource(Res.plurals.series_weeks, state.maintenanceSeries, state.maintenanceSeries),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = gutter.padding(bottom = 8.dp),
                    )
                }
            }
            state.weeklySummary?.let { summary ->
                item(key = "weekly-summary") {
                    WeeklySummaryCard(summary, state.pro.purchaseStatus == ProPurchaseStatus.ACTIVE, viewModel::openProPaywall, gutter)
                }
            }
            items(alerts, key = { "alert-${it.aquarium.id}" }) { alert ->
                AlertRow(alert, onClick = { viewModel.selectAquarium(alert.aquarium.id) }, modifier = gutter)
            }
            if (state.accountInvitationPrompt == AccountInvitationPrompt.AFTER_SESSION_CONFIRMATION) {
                item {
                    Button(
                        onClick = viewModel::continueAfterSessionConfirmation,
                        shape = MaterialTheme.shapes.medium,
                        modifier = gutter.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(stringResource(Res.string.continue_after_session)) }
                }
            }
            if (state.cycledDismissalsLoaded && looksCycled(setup, state.latestReadings) && state.latestSession?.session?.id != state.cycledDismissedSessionId) {
                item(key = "cycled-proposal") {
                    CycledProposalCard(viewModel::switchToEstablished, viewModel::dismissCycledProposal, gutter)
                }
            }
            if (pistes.isNotEmpty()) {
                item(key = "pistes") { PistesCard(pistes, aquarium.id, today, gutter) }
            }
            item {
                CarnetSectionTitle(
                    stringResource(Res.string.latest_values_title),
                    gutter,
                    actionLabel = stringResource(Res.string.navigation_trends),
                    onAction = { viewModel.selectDestination(MainDestination.TRENDS) },
                )
            }
            items(overview.values.chunked(columns)) { row ->
                Row(gutter.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { value ->
                        val label = value.definition.parameter.label()
                        ReadingValue(
                            value = value.value?.let { formatter.number(it, value.definition.precision) },
                            label = label,
                            modifier = Modifier.weight(1f),
                            unit = value.definition.unit.takeIf { it.isNotBlank() && it != label }, // pH's unit is pH
                            level = value.level,
                            age = value.occurredAtEpochMillis?.takeIf { value.isOlderThanLatestReading }
                                ?.let { daysSince(it, todayDate) }?.takeIf { it > 0 }
                                ?.let { days -> pluralStringResource(Res.plurals.reading_age_days, days, days) },
                            trend = value.trend,
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (state.syncState.status == SyncStatus.FAILED) {
                item { SyncFailure(state, viewModel, gutter) }
            }
            item {
                CarnetSectionTitle(
                    stringResource(Res.string.home_today),
                    gutter,
                    actionLabel = stringResource(Res.string.home_see_all),
                    onAction = viewModel::openTasks,
                )
            }
            if (state.dueTests.isNotEmpty()) {
                item(key = "due-tests") {
                    DueTestsRow(
                        state.dueTests,
                        enabled = canMeasure,
                        onClick = { viewModel.openRapidSession(state.dueTests.map { it.definition.id }) },
                        modifier = gutter,
                    )
                }
            }
            if (tasks.due.isEmpty()) {
                item {
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
                    TaskRow(task, today, onComplete = { onFailed -> viewModel.completeTask(task, onFailed) })
                }
            }
            if (state.accountInvitationPrompt == AccountInvitationPrompt.REMINDER) {
                item {
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
        ExtendedFloatingActionButton(
            onClick = { if (canMeasure) viewModel.openRapidSession() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).heightIn(min = 48.dp)
                .alpha(if (canMeasure) 1f else .38f)
                .semantics { if (!canMeasure) disabled() },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) { Text(stringResource(Res.string.measure_action), fontWeight = FontWeight.SemiBold) }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }
    PhotoSheets(state, viewModel, snackbar)
    if (state.pistesAfterSave.isNotEmpty()) PistesSheet(state.pistesAfterSave, viewModel.today(), viewModel::dismissPistes)
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
        )
    }
}

/**
 * « Measure » records into the current Aquarium, so it is only live while the scene shows that one:
 * not mid-swipe, and not between a swipe asking for another Aquarium and the selection catching up.
 */
internal fun measureTargetsShownAquarium(aquariumCount: Int, scrolling: Boolean, swipeSelectionPending: Boolean): Boolean =
    aquariumCount < 2 || (!scrolling && !swipeSelectionPending)

/** The page the pager must scroll to so it shows the current Aquarium, or null when it already does. */
internal fun pagerSyncTarget(currentIndex: Int, pageCount: Int, currentPage: Int): Int? =
    currentIndex.takeIf { pageCount > 1 && it in 0 until pageCount && it != currentPage }

/** One scene, or a pager of scenes with dots when there are several Aquariums; settling on a page selects it. */
@Composable
private fun AquariumScene(
    aquariums: List<Aquarium>,
    current: Aquarium,
    pager: PagerState,
    onOpenPhoto: () -> Unit,
    loadPhoto: suspend (String) -> ImageBitmap?,
    onChangeAquarium: () -> Unit,
) {
    if (aquariums.size < 2) {
        SceneHeader(current, onChangeAquarium, onOpenPhoto, loadPhoto)
        return
    }
    Column {
        HorizontalPager(pager, Modifier.testTag("home-pager")) { page ->
            // The photo flow acts on the current Aquarium: a page still sliding in has no live vignette.
            SceneHeader(aquariums[page], onChangeAquarium, onOpenPhoto.takeIf { aquariums[page].id == current.id }, loadPhoto)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            repeat(aquariums.size) { index ->
                val color = if (index == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                Box(Modifier.padding(horizontal = 4.dp).size(8.dp).background(color, CircleShape))
            }
        }
    }
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
private fun SceneHeader(
    aquarium: Aquarium,
    onChangeAquarium: () -> Unit,
    onOpenPhoto: (() -> Unit)?,
    loadPhoto: suspend (String) -> ImageBitmap?,
) {
    // No age: createdAtEpochMillis is when the Aquarium was added to the app, not when it was set up.
    val type = stringResource(
        when (aquarium.profile) {
            AquariumProfile.ESTABLISHED -> Res.string.profile_established
            AquariumProfile.CYCLING -> Res.string.profile_cycling
            AquariumProfile.PLANTED_SHRIMP -> Res.string.profile_planted_shrimp
        },
    )
    val shadow = Shadow(Color.Black.copy(alpha = .45f), Offset(0f, 1f), blurRadius = 3f)
    Box(Modifier.fillMaxWidth()) {
        TankScene(aquarium, Modifier.fillMaxWidth())
        Column(
            // Clear of the photo vignette (48 dp + 12 dp margin) at the top end.
            Modifier.padding(end = 60.dp).clickable(onClickLabel = stringResource(Res.string.home_change_aquarium), onClick = onChangeAquarium)
                .padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 48.dp),
        ) {
            Text(
                aquarium.name,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, shadow = shadow),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                aquarium.formattedVolume() + SEPARATOR + type,
                style = MaterialTheme.typography.bodyMedium.copy(shadow = shadow),
                color = Color.White,
            )
        }
        val photoLabel = stringResource(if (aquarium.photoId != null) Res.string.photo_open else Res.string.photo_add, aquarium.name)
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp).size(48.dp)
                .clickable(enabled = onOpenPhoto != null, onClickLabel = photoLabel, role = Role.Button) { onOpenPhoto?.invoke() }
                .semantics { contentDescription = photoLabel },
            contentAlignment = Alignment.Center,
        ) {
            Box {
                AquariumThumbnail(aquarium, 44.dp, loadPhoto, Modifier.border(1.5.dp, Color.White, RoundedCornerShape(12.dp)))
                if (aquarium.photoId == null) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(22.dp)
                            .background(Color.White.copy(alpha = .85f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(AquagendaIcons.Camera, contentDescription = null, modifier = Modifier.size(14.dp), tint = CarnetLightColors.primary) }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(
    overview: HomeOverview,
    hasSession: Boolean,
    today: LocalDate,
    onOpenSession: () -> Unit,
    modifier: Modifier,
) {
    val measured = overview.isMeasured
    val status = statusText(overview, today)
    val summary = status.summary.resolve()
    val since = status.since?.resolve()
    val dot = when {
        overview.hasAlert -> levelMarkColor(ReadingLevel.ALERT)!!
        overview.outsideRangeCount > 0 -> attentionMarkColor()
        measured -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val viewDetails = stringResource(Res.string.view_session_details)
    Row(
        // onClickLabel keeps the status itself as what TalkBack reads, and names the action.
        (if (hasSession) Modifier.clickable(onClickLabel = viewDetails, onClick = onOpenSession) else Modifier)
            .then(modifier)
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(summary) }
                if (since != null) append(SEPARATOR + since)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
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
