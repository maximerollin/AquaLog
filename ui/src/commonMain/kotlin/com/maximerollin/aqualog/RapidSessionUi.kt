package com.maximerollin.aqualog

import com.maximerollin.aqualog.ui.icon.*
import com.maximerollin.aqualog.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.home.HeroText
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.MaintenanceActionInput
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.ui.theme.CarnetSectionTitle
import com.maximerollin.aqualog.ui.theme.FilledField
import com.maximerollin.aqualog.ui.theme.GlassIconButton
import com.maximerollin.aqualog.ui.theme.HeroTextBacking
import com.maximerollin.aqualog.ui.theme.PaperSheetTop
import com.maximerollin.aqualog.ui.theme.WaterHeader
import com.maximerollin.aqualog.ui.theme.overlapNext

@Composable
fun RapidSessionScreen(
    state: RapidSessionUiState,
    aquarium: Aquarium,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val formatter = currentLocalizedFormatters()
    val context = state.context
    SaveCheckDialog(state, viewModel)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        // The water header runs to the top edge, under the status bar; the bottom bar keeps its own insets.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = { viewModel.saveRapidSession() },
                    shape = MaterialTheme.shapes.medium,
                    enabled = state.canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .heightIn(min = 48.dp)
                        .testTag("save-session"),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator()
                    } else {
                        Text(
                            saveButtonLabel(
                                editing = state.editingSessionId != null,
                                count = context?.let { validMeasurementCount(state.measurementInputs, it.activeParameters) } ?: 0,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        val title = stringResource(
            if (state.editingSessionId == null) Res.string.rapid_session_title else Res.string.edit_session,
        )
        val focusManager = LocalFocusManager.current
        // Once per screen, not per composition of the first row: scrolling it back in must not steal the focus.
        val firstFieldFocus = remember { FocusRequester() }
        val listState = rememberLazyListState()
        val firstParameterId = context?.activeParameters?.firstOrNull()?.id
        LaunchedEffect(firstParameterId) {
            if (firstParameterId != null) {
                withFrameNanos { }
                // The first row must be composed for its requester to be attached (short viewport, large text).
                if (listState.layoutInfo.visibleItemsInfo.none { it.key == firstParameterId }) {
                    listState.scrollToItem(FIRST_PARAMETER_ITEM_INDEX)
                    withFrameNanos { }
                }
                // shortcut: a row that still is not attached skips the auto-focus instead of crashing, the user taps it.
                runCatching { firstFieldFocus.requestFocus() }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            // Two tiles a row; at large text or on a very narrow screen they stack instead of shrinking.
            val columns = sessionColumns(LocalDensity.current.fontScale, maxWidth)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("rapid-session-list"),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                item(key = "header") {
                    WaterHeader(
                        aquarium = aquarium,
                        title = title,
                        subtitle = aquarium.name,
                        action = {
                            GlassIconButton(AquagendaIcons.X, stringResource(Res.string.cancel), viewModel::closeRapidSession)
                        },
                        // The sheet's top edge, the next item, covers the water's bottom.
                        modifier = Modifier.overlapNext(30.dp),
                    ) {
                        SessionTimeChip(
                            time = formatter.shortDateTime(state.occurredAtEpochMillis),
                            onEarlier = { viewModel.shiftSessionTimeByMinutes(-15) },
                            onLater = { viewModel.shiftSessionTimeByMinutes(15) },
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
                        )
                    }
                }
                item(key = "sheet-top") { PaperSheetTop() }
                paperItem("measurements-heading") {
                    CarnetSectionTitle(stringResource(Res.string.measurements_heading), Modifier.padding(bottom = 8.dp))
                }
                if (context == null) {
                    paperItem("loading") { CircularProgressIndicator(Modifier.padding(vertical = 16.dp)) }
                    return@LazyColumn
                }
                val parameters = context.activeParameters
                itemsIndexed(parameters.chunked(columns), key = { _, row -> row.first().id }) { rowIndex, row ->
                    OnPaper {
                        MeasureTileRow(
                            tiles = row.mapIndexed { column, definition ->
                                val index = rowIndex * columns + column
                                val tile: @Composable (Modifier) -> Unit = { tileModifier ->
                                    MeasureTile(
                                        definition = definition,
                                        input = state.measurementInputs[definition.id].orEmpty(),
                                        profile = aquarium.profile,
                                        formatter = formatter,
                                        imeAction = sessionImeAction(index, parameters.lastIndex),
                                        onValueChange = { viewModel.updateMeasurement(definition.id, it) },
                                        onNext = { focusManager.moveFocus(FocusDirection.Next) },
                                        onDone = { focusManager.clearFocus() },
                                        modifier = tileModifier
                                            .testTag("rapid-measurement-${definition.parameter.storageValue}")
                                            .then(if (index == 0) Modifier.focusRequester(firstFieldFocus) else Modifier),
                                    )
                                }
                                tile
                            },
                            columns = columns,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                }
                paperItem("maintenance-heading") {
                    CarnetSectionTitle(
                        stringResource(Res.string.maintenance_heading),
                        Modifier.padding(top = 6.dp, bottom = 4.dp),
                    )
                }
                paperItem("maintenance-chips") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MaintenanceActionType.entries.forEach { type ->
                            MaintenanceChip(type, state.actions[type], viewModel)
                        }
                    }
                }
                val editing = state.editingAction?.let(state.actions::get)
                if (editing != null) {
                    paperItem("action-details") {
                        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionDetails(editing, viewModel)
                        }
                    }
                }
                paperItem("observation") {
                    Column(Modifier.padding(top = 12.dp)) {
                        NoteRow(
                            stringResource(if (state.showObservation) Res.string.hide_observation else Res.string.add_observation),
                            viewModel::toggleObservation,
                        )
                        if (state.showObservation) {
                            FilledField(
                                value = state.observation,
                                onValueChange = viewModel::updateObservation,
                                label = stringResource(Res.string.observation),
                                singleLine = false,
                                valueStyle = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
                paperItem("incident") {
                    Column(Modifier.padding(top = 8.dp)) {
                        NoteRow(
                            stringResource(if (state.showIncident) Res.string.hide_incident else Res.string.add_incident),
                            viewModel::toggleIncident,
                        )
                        if (state.showIncident) {
                            FilledField(
                                value = state.incident,
                                onValueChange = viewModel::updateIncident,
                                label = stringResource(Res.string.incident),
                                singleLine = false,
                                valueStyle = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
                if (state.saveError) {
                    paperItem("save-error") {
                        Text(
                            stringResource(Res.string.session_save_error),
                            modifier = Modifier.padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            StatusBarOverHeader(listState)
        }
    }
    if (state.confirmDiscard) {
        AlertDialog(
            onDismissRequest = viewModel::keepRapidSession,
            title = { Text(stringResource(Res.string.session_discard_title)) },
            text = { Text(stringResource(Res.string.session_discard_description)) },
            confirmButton = {
                TextButton(onClick = viewModel::discardRapidSession) { Text(stringResource(Res.string.session_discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::keepRapidSession) { Text(stringResource(Res.string.session_discard_keep)) }
            },
        )
    }
}

/** « Save changes » while editing, else « Save Session » until a valid Measurement is typed, then « Save · 4 measurements ». */
@Composable
internal fun saveButtonLabel(editing: Boolean, count: Int): String = when {
    editing -> stringResource(Res.string.save_changes)
    count == 0 -> stringResource(Res.string.save_session)
    else -> pluralStringResource(Res.plurals.save_session_count, count, count)
}

/** A sheet item: paper behind it edge to edge, its content 16 dp from the sides. */
private fun LazyListScope.paperItem(key: String, content: @Composable () -> Unit) = item(key = key) { OnPaper(content) }

@Composable
private fun OnPaper(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp)) { content() }
}

/** Glass pill under the header's title: −15 min, the Session's time, +15 min. Each button keeps a 48 dp target. */
@Composable
private fun SessionTimeChip(time: String, onEarlier: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    val pill = RoundedCornerShape(24.dp)
    Row(modifier) {
        Row(
            Modifier.background(HeroTextBacking, pill).border(1.dp, Color.White.copy(alpha = .3f), pill),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassStepButton(AquagendaIcons.Minus, stringResource(Res.string.earlier_time), onEarlier)
            Text(
                time,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.bodyMedium,
                color = HeroText,
            )
            GlassStepButton(AquagendaIcons.Plus, stringResource(Res.string.later_time), onLater)
        }
    }
}

@Composable
private fun GlassStepButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = HeroText, modifier = Modifier.size(18.dp)) }
}

/** « Add an observation » / « Report an incident »: a dashed row that opens its text field below. */
@Composable
private fun NoteRow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(NoteRowRadius)
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp)
            .dashedBorder(colors.outlineVariant, NoteRowRadius)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(AquagendaIcons.NotepadText, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
}

private val NoteRowRadius = 18.dp

/** A 1 dp dashed rounded outline, drawn inside the bounds. */
private fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    val dash = 4.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx() - stroke / 2),
        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
    )
}

@Composable
private fun MaintenanceChip(type: MaintenanceActionType, action: MaintenanceActionInput?, viewModel: HomeViewModel) {
    val label = type.label()
    val summary = action?.let { listOf(it.quantity, it.unit, it.product).filter(String::isNotBlank).joinToString(" ") }
    val remove = stringResource(Res.string.remove_action, label)
    // Moving on to maintenance closes the keyboard: a focused tile would otherwise pull the list back to itself.
    val focusManager = LocalFocusManager.current
    val toggle = {
        focusManager.clearFocus()
        viewModel.toggleMaintenanceAction(type)
    }
    FilterChip(
        selected = action != null,
        // A selected chip opens its quantity editor; the trailing icon is what removes it.
        onClick = {
            focusManager.clearFocus()
            if (action == null) viewModel.toggleMaintenanceAction(type) else viewModel.editMaintenanceAction(type)
        },
        // Also a custom action: a nested clickable can be dropped when the chip is merged for accessibility services.
        modifier = if (action != null) {
            Modifier.semantics {
                customActions = listOf(CustomAccessibilityAction(remove) { toggle(); true })
            }
        } else {
            Modifier
        },
        leadingIcon = {
            Icon(
                if (action != null) AquagendaIcons.Check else type.icon(),
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize),
            )
        },
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label)
                if (!summary.isNullOrBlank()) {
                    Text(SEPARATOR, Modifier.clearAndSetSemantics { })
                    Text(summary)
                }
            }
        },
        trailingIcon = if (action != null) {
            {
                Box(
                    Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(role = Role.Button, onClick = toggle)
                        .semantics { contentDescription = remove },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AquagendaIcons.X, contentDescription = null)
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun ActionDetails(action: MaintenanceActionInput, viewModel: HomeViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledField(
            value = action.quantity,
            onValueChange = { viewModel.updateMaintenanceQuantity(action.type, it) },
            label = stringResource(Res.string.action_quantity),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = !isValidActionQuantity(action.quantity),
            errorMessage = stringResource(Res.string.action_quantity_invalid),
            modifier = Modifier.weight(1f),
        )
        FilledField(
            value = action.unit,
            onValueChange = { viewModel.updateMaintenanceUnit(action.type, it) },
            label = stringResource(Res.string.parameter_unit),
            modifier = Modifier.weight(1f),
        )
    }
    if (action.type == MaintenanceActionType.FERTILIZATION) {
        FilledField(
            value = action.product,
            onValueChange = { viewModel.updateMaintenanceProduct(action.type, it) },
            label = stringResource(Res.string.action_product),
        )
    }
}

@Composable
internal fun MaintenanceActionType.label(): String = stringResource(
    when (this) {
        MaintenanceActionType.WATER_CHANGE -> Res.string.action_water_change
        MaintenanceActionType.FERTILIZATION -> Res.string.action_fertilization
        MaintenanceActionType.FILTER_MAINTENANCE -> Res.string.action_filter
        MaintenanceActionType.VACUUM -> Res.string.action_vacuum
        MaintenanceActionType.GLASS_CLEANING -> Res.string.action_glass
        MaintenanceActionType.PRUNING -> Res.string.action_pruning
    },
)

/** Stroke icon of an action: on its Session chip and beside the title of a task linked to it. */
internal fun MaintenanceActionType.icon(): ImageVector = when (this) {
    MaintenanceActionType.WATER_CHANGE -> AquagendaIcons.Droplet
    MaintenanceActionType.FILTER_MAINTENANCE -> AquagendaIcons.Funnel
    MaintenanceActionType.FERTILIZATION -> AquagendaIcons.FlaskConical
    // Lucide 0.469 has no brush-cleaning: the paintbrush stands in for the gravel vacuum.
    MaintenanceActionType.VACUUM -> AquagendaIcons.Paintbrush
    MaintenanceActionType.GLASS_CLEANING -> AquagendaIcons.SprayCan
    MaintenanceActionType.PRUNING -> AquagendaIcons.Scissors
}

/** A task's title, after its linked action's icon unless [showIcon] is false (a row that already shows the icon in its tile). */
@Composable
internal fun LinkedTaskTitle(title: String, type: MaintenanceActionType?, showIcon: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        type?.takeIf { showIcon }?.let { Icon(it.icon(), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

internal const val SEPARATOR = " · "

// Water header, sheet top, the Measurements section title, then one item per row of tiles.
private const val FIRST_PARAMETER_ITEM_INDEX = 3
