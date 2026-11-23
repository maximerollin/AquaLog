package com.maximerollin.aqualog.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import com.maximerollin.aqualog.ParameterEditorState
import com.maximerollin.aqualog.SEPARATOR
import com.maximerollin.aqualog.currentLocalizedFormatters
import com.maximerollin.aqualog.label
import com.maximerollin.aqualog.rangeText
import com.maximerollin.aqualog.resources.Res
import com.maximerollin.aqualog.resources.enabled_for_parameter
import com.maximerollin.aqualog.resources.invalid_parameters
import com.maximerollin.aqualog.resources.move_parameter_down
import com.maximerollin.aqualog.resources.move_parameter_up
import com.maximerollin.aqualog.resources.parameter_range_invalid
import com.maximerollin.aqualog.resources.parameter_range_none
import com.maximerollin.aqualog.resources.parameters_count_active
import com.maximerollin.aqualog.resources.parameters_hint
import com.maximerollin.aqualog.resources.parameters_title
import com.maximerollin.aqualog.resources.profile_cycling
import com.maximerollin.aqualog.resources.profile_established
import com.maximerollin.aqualog.resources.profile_planted_shrimp
import com.maximerollin.aqualog.resources.try_mini_session
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.ui.icon.AquagendaIcons
import com.maximerollin.aqualog.ui.icon.GripVertical
import com.maximerollin.aqualog.ui.theme.LargeFontScale
import com.maximerollin.aqualog.ui.theme.PaperSheetTop
import com.maximerollin.aqualog.ui.theme.overlapNext
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Where a dragged row goes: −1 once it is past half of the row above, +1 past half of the row below, else 0.
 * A missing neighbour (the first or last row) never wins.
 */
fun reorderOffset(dragY: Float, previousHeight: Float?, nextHeight: Float?): Int = when {
    previousHeight != null && dragY < -previousHeight / 2 -> -1
    nextHeight != null && dragY > nextHeight / 2 -> 1
    else -> 0
}

/** « 6,5–7,5 pH », « Aucune plage » when no bound is set, « Plage à revoir » while the row is invalid. */
@Composable
fun rangePillText(state: ParameterEditorState): String {
    val definition = state.toPreviewDefinition(0) ?: return stringResource(Res.string.parameter_range_invalid)
    val range = definition.rangeText(currentLocalizedFormatters()) ?: return stringResource(Res.string.parameter_range_none)
    return "$range ${definition.unit}"
}

/** Step 3: the Parameters as one card of rows to switch, drag into order, and tap to set their range in a sheet. */
@Composable
fun ParametersContent(
    parameters: List<ParameterEditorState>,
    profile: AquariumProfile,
    showError: Boolean,
    actions: OnboardingActions,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var openRange by rememberSaveable { mutableStateOf<String?>(null) }
    val profileLabel = stringResource(
        when (profile) {
            AquariumProfile.ESTABLISHED -> Res.string.profile_established
            AquariumProfile.CYCLING -> Res.string.profile_cycling
            AquariumProfile.PLANTED_SHRIMP -> Res.string.profile_planted_shrimp
        },
    )
    val activeCount = parameters.count(ParameterEditorState::isActive)
    val subtitle = profileLabel + SEPARATOR + pluralStringResource(Res.plurals.parameters_count_active, activeCount, activeCount)
    OnboardingScaffold(
        listState = listState,
        primaryLabel = stringResource(Res.string.try_mini_session),
        onPrimary = actions.continueToPractice,
        modifier = modifier,
    ) {
        item {
            OnboardingHeader(
                aquarium = onboardingPreviewAquarium("", "", VolumeUnit.LITERS, profile),
                minHeight = 200.dp,
                animate = false,
                modifier = Modifier.overlapNext(30.dp),
                step = 3,
            ) {
                OnboardingTitle(stringResource(Res.string.parameters_title), subtitle)
            }
        }
        item { PaperSheetTop() }
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(Res.string.parameters_hint),
                    modifier = Modifier.padding(horizontal = 20.dp),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ParametersCard(parameters, actions, onOpen = { openRange = it.storageValue })
                if (showError) {
                    Text(
                        stringResource(Res.string.invalid_parameters),
                        modifier = Modifier.padding(horizontal = 20.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
    parameters.firstOrNull { it.parameter.storageValue == openRange }?.let { state ->
        RangeSheet(state, actions, onDismiss = { openRange = null })
    }
}

/**
 * Every row in one surface card. The card holds the drag: the row being dragged and how far it is from its slot, so a drag
 * goes on when the row is moved to its new index (rows are keyed by Parameter).
 */
@Composable
private fun ParametersCard(
    parameters: List<ParameterEditorState>,
    actions: OnboardingActions,
    onOpen: (BuiltInParameter) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val heights = remember { HashMap<String, Float>() }
    var dragged by remember { mutableStateOf<String?>(null) }
    val dragOffset = remember { mutableFloatStateOf(0f) }
    val currentParameters by rememberUpdatedState(parameters)
    val currentActions by rememberUpdatedState(actions)
    val dragBy: (String, Float) -> Unit = remember {
        { key, delta ->
            val list = currentParameters
            val index = list.indexOfFirst { it.parameter.storageValue == key }
            if (index != -1) {
                val sum = dragOffset.floatValue + delta
                val previous = list.getOrNull(index - 1)?.let { heights[it.parameter.storageValue] }
                val next = list.getOrNull(index + 1)?.let { heights[it.parameter.storageValue] }
                // Past half a neighbour: the row takes its place, and the neighbour's height comes off the offset.
                val offset = reorderOffset(sum, previous, next)
                if (offset != 0) currentActions.moveParameter(list[index].parameter, offset)
                dragOffset.floatValue = when (offset) {
                    -1 -> sum + previous!!
                    1 -> sum - next!!
                    else -> sum
                }
            }
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(colors.surface, RoundedCornerShape(22.dp)),
    ) {
        parameters.forEachIndexed { index, state ->
            val storageValue = state.parameter.storageValue
            key(storageValue) {
                if (index > 0) HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
                ParameterRow(
                    state = state,
                    canMoveUp = index > 0,
                    canMoveDown = index < parameters.lastIndex,
                    onOpen = { onOpen(state.parameter) },
                    onMove = { actions.moveParameter(state.parameter, it) },
                    onActive = { actions.setParameterActive(state.parameter, it) },
                    dragging = dragged == storageValue,
                    dragOffset = { dragOffset.floatValue },
                    onDragStart = {
                        dragged = storageValue
                        dragOffset.floatValue = 0f
                    },
                    onDrag = { dragBy(storageValue, it) },
                    onDragEnd = {
                        dragged = null
                        dragOffset.floatValue = 0f
                    },
                    modifier = Modifier.onSizeChanged { heights[storageValue] = it.height.toFloat() },
                )
            }
        }
    }
}

/**
 * One Parameter: the drag handle, the name and its range pill, the switch. The row is one button that opens the range sheet
 * (the switch stays its own control), and it carries the « Déplacer … vers le haut / bas » custom actions, so a screen reader
 * reorders it from the node it lands on.
 */
@Composable
private fun ParameterRow(
    state: ParameterEditorState,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onMove: (Int) -> Unit,
    onActive: (Boolean) -> Unit,
    dragging: Boolean,
    dragOffset: () -> Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val label = state.parameter.label()
    val storageValue = state.parameter.storageValue
    val moveUp = stringResource(Res.string.move_parameter_up, label)
    val moveDown = stringResource(Res.string.move_parameter_down, label)
    val enabledDescription = stringResource(Res.string.enabled_for_parameter, label)
    val pill = rangePillText(state)
    val invalid = state.toDraft(0) == null
    val currentOnMove by rememberUpdatedState(onMove)
    val raised = with(LocalDensity.current) { 4.dp.toPx() }
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationY = if (dragging) dragOffset() else 0f
                shadowElevation = if (dragging) raised else 0f
                this.shape = shape
            }
            .then(if (dragging) Modifier.background(colors.surface, shape) else Modifier)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag("parameter-row-$storageValue")
            .clip(
                if (dragging) shape else RoundedCornerShape(
                    topStart = if (canMoveUp) 0.dp else 22.dp, topEnd = if (canMoveUp) 0.dp else 22.dp,
                    bottomStart = if (canMoveDown) 0.dp else 22.dp, bottomEnd = if (canMoveDown) 0.dp else 22.dp,
                ),
            )
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics {
                customActions = listOfNotNull(
                    CustomAccessibilityAction(moveUp) { currentOnMove(-1); true }.takeIf { canMoveUp },
                    CustomAccessibilityAction(moveDown) { currentOnMove(1); true }.takeIf { canMoveDown },
                )
            }
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DragHandle(storageValue, onDragStart, onDrag, onDragEnd)
        NameAndPill(label, pill, invalid, Modifier.weight(1f).alpha(if (state.isActive) 1f else .55f).padding(vertical = 8.dp))
        Switch(
            checked = state.isActive,
            onCheckedChange = onActive,
            modifier = Modifier.padding(start = 8.dp).semantics { contentDescription = enabledDescription },
        )
    }
}

/** The name, and the range pill at its end, or under it at large font sizes. */
@Composable
private fun NameAndPill(label: String, pill: String, invalid: Boolean, modifier: Modifier) {
    val name: @Composable (Modifier) -> Unit = { nameModifier ->
        Text(
            label,
            modifier = nameModifier,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    if (LocalDensity.current.fontScale >= LargeFontScale) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            name(Modifier)
            RangePill(pill, invalid)
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            name(Modifier.weight(1f))
            RangePill(pill, invalid)
        }
    }
}

@Composable
private fun RangePill(text: String, invalid: Boolean) {
    val colors = MaterialTheme.colorScheme
    val color = if (invalid) colors.error else colors.onSurfaceVariant
    Text(
        text,
        modifier = Modifier
            .border(1.dp, if (invalid) colors.error else colors.outlineVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        fontSize = 12.sp,
        color = color,
    )
}

/**
 * The grip: a drag sends the finger's vertical movement in window coordinates, so the row moving under it (translated, or
 * laid out at a new index) never feeds back into the drag. The drag is consumed, so the list does not scroll with it.
 */
@Composable
private fun DragHandle(storageValue: String, onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Box(
        Modifier
            .size(48.dp)
            .testTag("parameter-handle-$storageValue")
            .onGloballyPositioned { coordinates[0] = it }
            .pointerInput(Unit) {
                fun rootY(change: PointerInputChange): Float =
                    coordinates[0]?.takeIf { it.isAttached }?.localToRoot(change.position)?.y ?: change.position.y
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var lastY = rootY(down)
                    val start = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() } ?: return@awaitEachGesture
                    currentOnDragStart()
                    rootY(start).let { currentOnDrag(it - lastY); lastY = it }
                    drag(start.id) { change ->
                        val y = rootY(change)
                        currentOnDrag(y - lastY)
                        lastY = y
                        change.consume()
                    }
                    currentOnDragEnd()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(AquagendaIcons.GripVertical, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.outline)
    }
}
