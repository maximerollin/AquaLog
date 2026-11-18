package com.maximerollin.aqualog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximerollin.aqualog.resources.*
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.EventType
import com.maximerollin.aqualog.shared.RecordedSession
import com.maximerollin.aqualog.shared.SessionEvent
import com.maximerollin.aqualog.shared.assistance.readingLevel
import com.maximerollin.aqualog.ui.icon.*
import com.maximerollin.aqualog.ui.theme.GlassIconButton
import com.maximerollin.aqualog.ui.theme.GroupLabel
import com.maximerollin.aqualog.ui.theme.PaperSheetTop
import com.maximerollin.aqualog.ui.theme.SettingsGroup
import com.maximerollin.aqualog.ui.theme.SettingsRow
import com.maximerollin.aqualog.ui.theme.ValueTile
import com.maximerollin.aqualog.ui.theme.WaterHeader
import com.maximerollin.aqualog.ui.theme.overlapNext
import java.time.LocalDate
import org.jetbrains.compose.resources.stringResource

/** A recorded Session under the water: its readings as tiles, its maintenance and notes in cards, then edit and delete. */
@Composable
fun SessionDetailsScreen(
    recorded: RecordedSession,
    setup: AquariumSetup,
    returnToTimeline: Boolean,
    showDeleteConfirmation: Boolean,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val today = viewModel.today()
    SessionDetailsContent(
        recorded = recorded,
        setup = setup,
        returnToTimeline = returnToTimeline,
        showDeleteConfirmation = showDeleteConfirmation,
        today = LocalDate.of(today.year, today.month, today.day),
        actions = SessionDetailsActions(
            onBack = viewModel::closeSessionDetails,
            onEdit = viewModel::editSession,
            onRequestDeletion = viewModel::requestSessionDeletion,
            onConfirmDeletion = viewModel::confirmSessionDeletion,
            onCancelDeletion = viewModel::cancelSessionDeletion,
        ),
        modifier = modifier,
    )
}

/** What the Session details screen asks of its ViewModel; every default does nothing, for tests. */
internal class SessionDetailsActions(
    val onBack: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onRequestDeletion: () -> Unit = {},
    val onConfirmDeletion: () -> Unit = {},
    val onCancelDeletion: () -> Unit = {},
)

/** The stateless screen. [today] only feeds the tiles, which never show an age here: every value is of this Session. */
@Composable
internal fun SessionDetailsContent(
    recorded: RecordedSession,
    setup: AquariumSetup,
    returnToTimeline: Boolean,
    showDeleteConfirmation: Boolean,
    today: LocalDate,
    actions: SessionDetailsActions,
    modifier: Modifier = Modifier,
) {
    val formatter = currentLocalizedFormatters()
    val aquarium = setup.aquarium
    val occurredAt = recorded.session.occurredAtEpochMillis
    val parametersById = setup.parameters.associateBy { it.id }
    val readings = recorded.measurements.mapNotNull { measurement ->
        parametersById[measurement.parameterDefinitionId]?.let { definition ->
            HomeValue(
                definition = definition,
                value = measurement.value,
                occurredAtEpochMillis = occurredAt,
                level = readingLevel(definition, measurement.value, aquarium.profile),
                isOlderThanLatestReading = false,
            )
        }
    }
    val listState = rememberLazyListState()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("session-details-list"),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp + bottomInset),
        ) {
            item(key = "header") {
                WaterHeader(
                    aquarium = aquarium,
                    title = formatter.shortDateTime(occurredAt),
                    subtitle = stringResource(
                        Res.string.session_details_context, stringResource(Res.string.session_details), aquarium.name,
                    ),
                    leading = {
                        GlassIconButton(
                            AquagendaIcons.ArrowLeft,
                            stringResource(if (returnToTimeline) Res.string.back_to_timeline else Res.string.back),
                            actions.onBack,
                            Modifier.testTag("session-details-back"),
                        )
                    },
                    // The sheet's top edge, the next item, covers the water's bottom.
                    modifier = Modifier.overlapNext(30.dp),
                )
            }
            item(key = "sheet-top") { PaperSheetTop() }
            if (readings.isNotEmpty()) {
                item(key = "readings") {
                    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 18.dp)) {
                        val columns = sessionDetailsColumns(LocalDensity.current.fontScale, maxWidth)
                        Column(verticalArrangement = Arrangement.spacedBy(MeasureTileGap)) {
                            readings.chunked(columns).forEach { row ->
                                key(row.first().definition.id) {
                                    MeasureTileRow(
                                        tiles = row.map { value ->
                                            { tileModifier: Modifier ->
                                                ValueTile(
                                                    value, formatter, today,
                                                    tileModifier.testTag("session-reading-${value.definition.parameter.storageValue}"),
                                                )
                                            }
                                        },
                                        columns = columns,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (recorded.maintenanceActions.isNotEmpty()) {
                labelItem("maintenance-label") { stringResource(Res.string.maintenance_heading) }
                groupItem("maintenance") {
                    SettingsGroup {
                        recorded.maintenanceActions.forEach { action ->
                            key(action.id) {
                                val detail = listOf(
                                    action.quantity?.let(formatter::number).orEmpty(),
                                    action.unit.orEmpty(),
                                    action.product.orEmpty(),
                                ).filter(String::isNotBlank).joinToString(" ")
                                SettingsRow(
                                    label = action.type.label(),
                                    icon = action.type.icon(),
                                    value = detail.takeIf(String::isNotBlank),
                                    onClick = null,
                                )
                            }
                        }
                    }
                }
            }
            if (recorded.events.isNotEmpty()) {
                labelItem("notes-label") { stringResource(Res.string.session_notes_heading) }
                groupItem("notes") {
                    SettingsGroup {
                        recorded.events.forEach { event -> key(event.id) { NoteRow(event) } }
                    }
                }
            }
            item(key = "buttons") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(onClick = actions.onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.edit_session))
                    }
                    val error = MaterialTheme.colorScheme.error
                    OutlinedButton(
                        onClick = actions.onRequestDeletion,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = error),
                        border = BorderStroke(1.dp, error),
                    ) {
                        Text(stringResource(Res.string.delete_session))
                    }
                }
            }
        }
        StatusBarOverHeader(listState)
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = actions.onCancelDeletion,
            title = { Text(stringResource(Res.string.delete_session_title)) },
            text = { Text(stringResource(Res.string.delete_session_description)) },
            confirmButton = {
                TextButton(onClick = actions.onConfirmDeletion) {
                    Text(stringResource(Res.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = actions.onCancelDeletion) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

private fun LazyListScope.labelItem(key: String, text: @Composable () -> String) =
    item(key = key) { GroupLabel(text(), Modifier.padding(top = 8.dp, bottom = 8.dp)) }

private fun LazyListScope.groupItem(key: String, content: @Composable () -> Unit) =
    item(key = key) { Box(Modifier.padding(bottom = 14.dp)) { content() } }

/**
 * One note of the Session, read-only: the icon tile of a [SettingsRow], then its kind in small type above the note itself.
 * One node, so TalkBack reads the kind, then the note.
 */
@Composable
private fun NoteRow(event: SessionEvent) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).semantics(mergeDescendants = true) {}.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).background(colors.secondaryContainer, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(AquagendaIcons.NotepadText, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(if (event.type == EventType.OBSERVATION) Res.string.observation else Res.string.incident),
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
            )
            Text(event.note, fontSize = 16.sp, color = colors.onSurface)
        }
    }
}
