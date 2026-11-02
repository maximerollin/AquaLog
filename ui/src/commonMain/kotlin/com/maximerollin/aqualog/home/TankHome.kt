package com.maximerollin.aqualog.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
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
import com.maximerollin.aqualog.label
import com.maximerollin.aqualog.resources.*
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.SyncFailureCategory
import com.maximerollin.aqualog.shared.SyncStatus
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.TaskOccurrence
import com.maximerollin.aqualog.todayTasks
import com.maximerollin.aqualog.ui.theme.CarnetSectionTitle
import com.maximerollin.aqualog.ui.theme.ReadingValue
import com.maximerollin.aqualog.ui.theme.attentionMarkColor
import com.maximerollin.aqualog.ui.theme.attentionTextColor
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
    val overview = homeOverview(setup, state.latestReadings, state.latestSession)
    val today = viewModel.today()
    val todayDate = LocalDate.of(today.year, today.month, today.day)
    val tasks = todayTasks(state.tasks.pendingOccurrences, aquarium.id, today)
    val columns = if (LocalDensity.current.fontScale >= 1.3f) 2 else 3
    val formatter = currentLocalizedFormatters()
    val gutter = Modifier.padding(horizontal = 16.dp)
    val alerts = otherAquariumAlerts(state.aquariums, aquarium.id, state.tasks.pendingOccurrences, today)
    var showPicker by remember { mutableStateOf(false) }
    val currentIndex = state.aquariums.indexOfFirst { it.id == aquarium.id }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = currentIndex) { state.aquariums.size }
    val shownAquariums by rememberUpdatedState(state.aquariums)
    val currentId by rememberUpdatedState(aquarium.id)
    // Both effects live here, not in the scene item: the scene can be scrolled off-screen while the Aquarium changes.
    LaunchedEffect(currentIndex, state.aquariums.size) {
        pagerSyncTarget(currentIndex, state.aquariums.size, pager.currentPage)?.let {
            if (pager.layoutInfo.visiblePagesInfo.isEmpty()) pager.scrollToPage(it) else pager.animateScrollToPage(it)
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            shownAquariums.getOrNull(page)?.takeIf { it.id != currentId && shownAquariums.size > 1 }
                ?.let { viewModel.selectAquarium(it.id) }
        }
    }
    val canMeasure = measureTargetsShownAquarium(state.aquariums, pager.settledPage, aquarium.id, pager.isScrollInProgress)

    Box(modifier.fillMaxSize()) {
        // The only scrollable on the home: tests rely on a single node with a scroll action.
        LazyColumn(
            Modifier.fillMaxSize().testTag("home-list"),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            item {
                AquariumScene(state.aquariums, aquarium, pager, todayDate) { showPicker = true }
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
                            value = value.value?.let { formatter.number(it, value.definition.precision) } ?: "—",
                            label = label,
                            modifier = Modifier.weight(1f),
                            unit = value.definition.unit.takeIf { it.isNotBlank() && it != label }, // pH's unit is pH
                            attention = value.isOutsideRange,
                            age = value.occurredAtEpochMillis?.takeIf { value.isOlderThanLatestSession }
                                ?.let { daysSince(it, todayDate) }?.takeIf { it > 0 }
                                ?.let { days -> pluralStringResource(Res.plurals.reading_age_days, days, days) },
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
                    TaskRow(task, today, onComplete = { viewModel.completeTask(task) })
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
        )
    }
}

/**
 * « Measure » records into the current Aquarium, so it is only live while the scene shows that one:
 * not mid-swipe, and not between a settled swipe and the selection catching up.
 */
internal fun measureTargetsShownAquarium(aquariums: List<Aquarium>, settledPage: Int, currentId: String, scrolling: Boolean): Boolean =
    aquariums.size < 2 || (!scrolling && aquariums.getOrNull(settledPage)?.id == currentId)

/** The page the pager must scroll to so it shows the current Aquarium, or null when it already does. */
internal fun pagerSyncTarget(currentIndex: Int, pageCount: Int, currentPage: Int): Int? =
    currentIndex.takeIf { pageCount > 1 && it in 0 until pageCount && it != currentPage }

/** One scene, or a pager of scenes with dots when there are several Aquariums; settling on a page selects it. */
@Composable
private fun AquariumScene(
    aquariums: List<Aquarium>,
    current: Aquarium,
    pager: PagerState,
    today: LocalDate,
    onChangeAquarium: () -> Unit,
) {
    if (aquariums.size < 2) {
        SceneHeader(current, today, onChangeAquarium)
        return
    }
    Column {
        HorizontalPager(pager, Modifier.testTag("home-pager")) { page ->
            SceneHeader(aquariums[page], today, onChangeAquarium)
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
    Text(
        buildAnnotatedString {
            append("${alert.aquarium.name} · ${alert.occurrence.title} · ")
            withStyle(SpanStyle(color = if (alert.isOverdue) attentionTextColor() else MaterialTheme.colorScheme.onSurfaceVariant)) {
                append(due)
            }
            append(" ›")
        },
        modifier = Modifier.clickable(onClick = onClick).then(modifier).fillMaxWidth().heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun SceneHeader(aquarium: Aquarium, today: LocalDate, onChangeAquarium: () -> Unit) {
    val created = localDate(aquarium.createdAtEpochMillis)
    val months = ChronoUnit.MONTHS.between(created, today).toInt()
    val days = ChronoUnit.DAYS.between(created, today).toInt()
    val age = when {
        months >= 1 -> pluralStringResource(Res.plurals.aquarium_age_months, months, months)
        days >= 1 -> pluralStringResource(Res.plurals.aquarium_age_days, days, days)
        else -> null
    }
    val shadow = Shadow(Color.Black.copy(alpha = .45f), Offset(0f, 1f), blurRadius = 3f)
    Box(Modifier.fillMaxWidth()) {
        TankScene(aquarium, Modifier.fillMaxWidth())
        Column(
            Modifier.clickable(onClickLabel = stringResource(Res.string.home_change_aquarium), onClick = onChangeAquarium)
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
                listOfNotNull(aquarium.formattedVolume(), age).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium.copy(shadow = shadow),
                color = Color.White,
            )
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
    val measured = overview.values.any { it.value != null }
    val summary = when {
        !measured -> stringResource(Res.string.home_no_measurement_yet)
        overview.outsideRangeCount > 0 -> pluralStringResource(
            Res.plurals.home_status_outside_range,
            overview.outsideRangeCount,
            overview.outsideRangeCount,
        )
        else -> stringResource(Res.string.home_status_all_in_range)
    }
    val since = overview.latestSessionAtEpochMillis?.takeIf { measured }?.let {
        when (val days = daysSince(it, today)) {
            0 -> stringResource(Res.string.home_measured_today)
            1 -> stringResource(Res.string.home_measured_yesterday)
            else -> pluralStringResource(Res.plurals.home_measured_days_ago, days, days)
        }
    }
    val dot = when {
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
                if (since != null) append(" · $since")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun TaskRow(task: TaskOccurrence, today: TaskDate, onComplete: () -> Unit) {
    // Guards against a double tap resolving the occurrence twice before the list refreshes.
    var completed by remember(task.id) { mutableStateOf(false) }
    val formatter = currentLocalizedFormatters()
    val description = "${stringResource(Res.string.task_complete)}, ${task.title}"
    val overdue = compareValuesBy(task.dueDate, today, TaskDate::year, TaskDate::month, TaskDate::day) < 0
    Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = completed,
            onCheckedChange = if (completed) null else { _ -> completed = true; onComplete() },
            modifier = Modifier.padding(start = 4.dp).semantics { contentDescription = description },
        )
        Column(Modifier.weight(1f)) {
            Text(task.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
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
private fun daysSince(epochMillis: Long, today: LocalDate): Int =
    ChronoUnit.DAYS.between(localDate(epochMillis), today).toInt().coerceAtLeast(0)
