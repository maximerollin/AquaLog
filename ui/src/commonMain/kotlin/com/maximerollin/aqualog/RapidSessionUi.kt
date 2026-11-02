package com.maximerollin.aqualog

import com.maximerollin.aqualog.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.MaintenanceActionInput
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.evaluateMeasurement
import com.maximerollin.aqualog.ui.theme.CarnetDivider
import com.maximerollin.aqualog.ui.theme.CarnetScreenTitle
import com.maximerollin.aqualog.ui.theme.CarnetSectionTitle
import com.maximerollin.aqualog.ui.theme.attentionMarkColor
import com.maximerollin.aqualog.ui.theme.attentionTextColor

@Composable
fun RapidSessionScreen(
    state: RapidSessionUiState,
    aquarium: Aquarium,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val formatter = currentLocalizedFormatters()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = viewModel::saveRapidSession,
                    shape = MaterialTheme.shapes.medium,
                    enabled = state.canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .heightIn(min = 48.dp),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator()
                    } else {
                        Text(
                            stringResource(
                                if (state.editingSessionId == null) Res.string.save_session else Res.string.save_changes,
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
        val context = state.context
        if (context == null) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                CarnetScreenTitle(title, aquarium.name)
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val focusManager = LocalFocusManager.current
        // Once per screen, not per composition of the first row: scrolling it back in must not steal the focus.
        val firstFieldFocus = remember { FocusRequester() }
        LaunchedEffect(context.activeParameters.firstOrNull()?.id) {
            if (context.activeParameters.isNotEmpty()) {
                withFrameNanos { }
                firstFieldFocus.requestFocus()
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            // Rows keep their 48 dp height; at large text or on a very narrow screen they stack instead of shrinking.
            val stacked = LocalDensity.current.fontScale >= 1.3f || maxWidth - 48.dp < 280.dp
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("rapid-session-list"),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
            ) {
                item {
                    val timeControls: @Composable () -> Unit = {
                        IconButton(onClick = { viewModel.shiftSessionTimeByMinutes(-15) }) {
                            Icon(
                                painterResource(Res.drawable.ic_chevron_left),
                                stringResource(Res.string.earlier_time),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { viewModel.shiftSessionTimeByMinutes(15) }) {
                            Icon(
                                painterResource(Res.drawable.ic_chevron_right),
                                stringResource(Res.string.later_time),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Column {
                        CarnetScreenTitle(
                            title,
                            "${aquarium.name} · ${formatter.shortDateTime(state.occurredAtEpochMillis)}",
                            action = {
                                if (!stacked) timeControls()
                                TextButton(
                                    onClick = viewModel::closeRapidSession,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) { Text(stringResource(Res.string.cancel)) }
                            },
                        )
                        if (stacked) Row { timeControls() }
                    }
                }
                item {
                    CarnetSectionTitle(
                        stringResource(Res.string.measurements_heading),
                        Modifier.padding(top = 12.dp),
                    )
                }
                itemsIndexed(
                    context.activeParameters,
                    key = { _, definition -> definition.id },
                ) { index, definition ->
                    val focusRequester = if (index == 0) firstFieldFocus else null
                    val input = state.measurementInputs[definition.id].orEmpty()
                    val feedback = evaluateMeasurement(input, definition)
                    val description = stringResource(Res.string.measurement_for_parameter, definition.parameter.label())
                    val muted = MaterialTheme.colorScheme.onSurfaceVariant
                    val name = definition.parameter.label()
                    val unit = definition.unit
                    val flag = when {
                        !feedback.isValid -> MaterialTheme.colorScheme.error
                        feedback.isOutsideIndicativeRange -> attentionMarkColor()
                        else -> null
                    }
                    val valueColor = when {
                        !feedback.isValid -> MaterialTheme.colorScheme.error
                        feedback.isOutsideIndicativeRange -> attentionTextColor()
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    val label: @Composable () -> Unit = {
                        Text(
                            buildAnnotatedString {
                                append(name)
                                if (unit.isNotBlank()) {
                                    append(" ")
                                    withStyle(SpanStyle(fontSize = 12.sp, color = muted)) { append(unit) }
                                }
                            },
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        )
                    }
                    val range = definition.rangeText(formatter)
                    val rangeLabel: @Composable () -> Unit = {
                        if (range != null) Text(range, style = MaterialTheme.typography.labelMedium, color = muted)
                    }
                    val editor: @Composable (Modifier) -> Unit = { fieldModifier ->
                        UnderlinedValueField(
                            value = input,
                            onValueChange = { viewModel.updateMeasurement(definition.id, it) },
                            textColor = valueColor,
                            flagColor = flag,
                            imeAction = if (index == context.activeParameters.lastIndex) ImeAction.Done else ImeAction.Next,
                            onNext = { focusManager.moveFocus(FocusDirection.Next) },
                            onDone = { focusManager.clearFocus() },
                            modifier = fieldModifier
                                .testTag("rapid-measurement-${definition.parameter.storageValue}")
                                .then(focusRequester?.let(Modifier::focusRequester) ?: Modifier)
                                .semantics {
                                    contentDescription = description
                                    stateDescription = unit
                                },
                        )
                    }
                    Column {
                        if (stacked) {
                            Column(Modifier.padding(top = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) { label() }
                                    rangeLabel()
                                }
                                editor(Modifier.fillMaxWidth())
                            }
                        } else {
                            Row(
                                Modifier.heightIn(min = 48.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.weight(1f)) { label() }
                                Box(Modifier.widthIn(min = 64.dp), contentAlignment = Alignment.CenterEnd) { rangeLabel() }
                                editor(Modifier.width(88.dp))
                            }
                        }
                        val hint = when {
                            !feedback.isValid -> stringResource(Res.string.invalid_measurement)
                            feedback.isOutsideIndicativeRange -> stringResource(Res.string.unusual_measurement)
                            else -> null
                        }
                        if (hint != null) {
                            Text(
                                hint,
                                modifier = Modifier.padding(bottom = 6.dp),
                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                color = if (!feedback.isValid) MaterialTheme.colorScheme.error else attentionTextColor(),
                            )
                        }
                        // Stacked, the value's own line already closes the row: a second line would double it.
                        if (stacked) Spacer(Modifier.height(8.dp)) else CarnetDivider()
                    }
                }
                item {
                    CarnetSectionTitle(
                        stringResource(Res.string.maintenance_heading),
                        Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MaintenanceActionType.entries.forEach { type ->
                            MaintenanceChip(type, state.actions[type], viewModel)
                        }
                    }
                }
                val editing = state.editingAction?.let(state.actions::get)
                if (editing != null) {
                    item {
                        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionDetails(editing, viewModel)
                        }
                    }
                }
                item {
                    NoteRow(
                        stringResource(if (state.showObservation) Res.string.hide_observation else Res.string.add_observation),
                        viewModel::toggleObservation,
                        Modifier.padding(top = 8.dp),
                    )
                    if (state.showObservation) {
                        OutlinedTextField(
                            value = state.observation,
                            onValueChange = viewModel::updateObservation,
                            label = { Text(stringResource(Res.string.observation)) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                    }
                }
                item {
                    NoteRow(
                        stringResource(if (state.showIncident) Res.string.hide_incident else Res.string.add_incident),
                        viewModel::toggleIncident,
                    )
                    if (state.showIncident) {
                        OutlinedTextField(
                            value = state.incident,
                            onValueChange = viewModel::updateIncident,
                            label = { Text(stringResource(Res.string.incident)) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                    }
                }
                if (state.saveError) {
                    item {
                        Text(
                            stringResource(Res.string.session_save_error),
                            modifier = Modifier.padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/** A carnet value: right-aligned number over a line instead of a box; the line carries the attention colour. */
@Composable
private fun UnderlinedValueField(
    value: String,
    onValueChange: (String) -> Unit,
    textColor: Color,
    flagColor: Color?,
    imeAction: ImeAction,
    onNext: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        interactionSource = interaction,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            textAlign = TextAlign.End,
        ),
        cursorBrush = SolidColor(primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onDone() }),
        decorationBox = { inner ->
            Column(Modifier.heightIn(min = 48.dp), verticalArrangement = Arrangement.Center) {
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.CenterEnd) { inner() }
                Box(
                    Modifier.fillMaxWidth()
                        .height(if (focused || flagColor != null) 2.dp else 1.dp)
                        .background(flagColor ?: if (focused) primary else outline),
                )
            }
        },
    )
}

@Composable
private fun NoteRow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CarnetDivider()
    }
}

/** « 6,5–7,5 », « ≤ 40 », « ≥ 3 », or nothing when the parameter has no indicative range. */
private fun ParameterDefinition.rangeText(formatter: LocalizedFormatters): String? {
    val min = indicativeMinimum?.let { formatter.number(it, precision) }
    val max = indicativeMaximum?.let { formatter.number(it, precision) }
    return when {
        min != null && max != null -> "$min–$max"
        max != null -> "≤ $max"
        min != null -> "≥ $min"
        else -> null
    }
}

@Composable
private fun MaintenanceChip(type: MaintenanceActionType, action: MaintenanceActionInput?, viewModel: HomeViewModel) {
    val label = type.label()
    val summary = action?.let { listOf(it.quantity, it.unit, it.product).filter(String::isNotBlank).joinToString(" ") }
    FilterChip(
        selected = action != null,
        // A selected chip opens its quantity editor; the trailing icon is what removes it.
        onClick = { if (action == null) viewModel.toggleMaintenanceAction(type) else viewModel.editMaintenanceAction(type) },
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
                Icon(
                    painterResource(Res.drawable.ic_close),
                    stringResource(Res.string.remove_action, label),
                    Modifier.clickable(role = Role.Button) { viewModel.toggleMaintenanceAction(type) },
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun ActionDetails(action: MaintenanceActionInput, viewModel: HomeViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = action.quantity,
            onValueChange = { viewModel.updateMaintenanceQuantity(action.type, it) },
            label = { Text(stringResource(Res.string.action_quantity)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = action.unit,
            onValueChange = { viewModel.updateMaintenanceUnit(action.type, it) },
            label = { Text(stringResource(Res.string.parameter_unit)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    if (action.type == MaintenanceActionType.FERTILIZATION) {
        OutlinedTextField(
            value = action.product,
            onValueChange = { viewModel.updateMaintenanceProduct(action.type, it) },
            label = { Text(stringResource(Res.string.action_product)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
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

private const val SEPARATOR = " · "
