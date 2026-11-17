package com.maximerollin.aqualog.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import com.maximerollin.aqualog.SessionTileState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.maximerollin.aqualog.home.HeroText
import com.maximerollin.aqualog.home.TankScene
import com.maximerollin.aqualog.shared.Aquarium
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import com.maximerollin.aqualog.ageDays
import com.maximerollin.aqualog.resources.reading_age_days
import com.maximerollin.aqualog.shared.assistance.ReadingLevel
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximerollin.aqualog.HomeValue
import com.maximerollin.aqualog.LocalizedFormatters
import com.maximerollin.aqualog.MainDestination
import com.maximerollin.aqualog.label
import com.maximerollin.aqualog.resources.Res
import com.maximerollin.aqualog.resources.home_no_measurement_yet
import com.maximerollin.aqualog.resources.home_values_next
import com.maximerollin.aqualog.resources.home_values_previous
import com.maximerollin.aqualog.resources.measure_action
import com.maximerollin.aqualog.resources.navigation_history
import com.maximerollin.aqualog.resources.navigation_more
import com.maximerollin.aqualog.resources.navigation_tank
import com.maximerollin.aqualog.resources.navigation_trends
import com.maximerollin.aqualog.ui.icon.AquagendaIcons
import com.maximerollin.aqualog.ui.icon.ChevronLeft
import com.maximerollin.aqualog.ui.icon.ChevronRight
import com.maximerollin.aqualog.ui.icon.Fish
import com.maximerollin.aqualog.ui.icon.Menu
import com.maximerollin.aqualog.ui.icon.Plus
import com.maximerollin.aqualog.ui.icon.RotateCcwClock
import com.maximerollin.aqualog.ui.icon.TrendingUp
import java.time.LocalDate
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Deep water behind every text block and button on the hero: white keeps 4.5:1 over the lightest sprite and over a white
 * photo (ScenePhysicsTest). The hero shade alone cannot, since the water under it can be anything.
 */
internal val HeroTextBacking = Color(0xFF0F4A43).copy(alpha = .75f)

/** Test tag of the shape that marks an out-of-range [ValueTile], so its state is never carried by colour alone. */
const val LEVEL_MARK_TAG = "value-level-mark"

/** Round button laid over the full-bleed water scene: deep-water glass with a light rim, white icon; it stays visible over a white photo. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(48.dp).clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(40.dp).background(HeroTextBacking, CircleShape)
                .border(1.dp, Color.White.copy(alpha = .3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
    }
}

/**
 * Top edge of the paper sheet that slides over the scene: rounded corners, handle, soft shadow. A list item, not a container.
 * It lies over the hero's bottom, so it takes the pointer: a tap or a swipe on it never reaches the water under it.
 */
@Composable
fun PaperSheetTop(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    Box(
        modifier.fillMaxWidth().height(30.dp)
            // Hit-testing stops at the first sibling with a pointer handler; the list still scrolls, nothing is consumed.
            .pointerInput(Unit) {}
            .shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = .2f), spotColor = Color.Black.copy(alpha = .2f))
            .background(MaterialTheme.colorScheme.background, shape),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier.padding(top = 10.dp).size(width = 40.dp, height = 5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
    }
}

/** Dp of bottom padding a list needs to scroll clear of the [FloatingNavBar]. */
const val FloatingNavBarClearance = 96

/** Floating pill: four icon-only tabs and a round « + » that opens a rapid Session. */
@Composable
fun FloatingNavBar(
    selected: MainDestination,
    onSelect: (MainDestination) -> Unit,
    onMeasure: () -> Unit,
    measureEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val pill = RoundedCornerShape(32.dp)
    Row(
        modifier.height(64.dp).shadow(8.dp, pill).background(colors.surface, pill)
            .border(1.dp, colors.outlineVariant, pill).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MainDestination.entries.forEach { destination ->
            val label = when (destination) {
                MainDestination.TANK -> stringResource(Res.string.navigation_tank)
                MainDestination.HISTORY -> stringResource(Res.string.navigation_history)
                MainDestination.TRENDS -> stringResource(Res.string.navigation_trends)
                MainDestination.MORE -> stringResource(Res.string.navigation_more)
            }
            val icon = when (destination) {
                MainDestination.TANK -> AquagendaIcons.Fish
                MainDestination.HISTORY -> AquagendaIcons.RotateCcwClock
                MainDestination.TRENDS -> AquagendaIcons.TrendingUp
                MainDestination.MORE -> AquagendaIcons.Menu
            }
            val isSelected = destination == selected
            Box(
                Modifier.size(48.dp)
                    .background(if (isSelected) colors.secondaryContainer else Color.Transparent, CircleShape)
                    .clickable(role = Role.Tab) { onSelect(destination) }
                    .semantics {
                        contentDescription = label
                        role = Role.Tab
                        this.selected = isSelected
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon, contentDescription = null, modifier = Modifier.size(24.dp),
                    tint = if (isSelected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                )
            }
        }
        val measureLabel = stringResource(Res.string.measure_action)
        Box(
            Modifier.padding(start = 4.dp).size(52.dp)
                .alpha(if (measureEnabled) 1f else .38f)
                .background(colors.primary, CircleShape)
                .clickable(role = Role.Button) { if (measureEnabled) onMeasure() }
                .semantics {
                    contentDescription = measureLabel
                    if (!measureEnabled) disabled()
                },
            contentAlignment = Alignment.Center,
        ) { Icon(AquagendaIcons.Plus, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(24.dp)) }
    }
}

/**
 * One value of the home: label, big tabular number with its unit and trend arrow, the age when it is older than the latest
 * Measurement. Out of range, it is tinted and marked by a shape; everything is spoken as one sentence.
 * [reserveAgeLine] keeps an empty age line so the tiles of a row share one height.
 */
@Composable
fun ValueTile(
    value: HomeValue,
    formatter: LocalizedFormatters,
    today: LocalDate,
    modifier: Modifier = Modifier,
    reserveAgeLine: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val label = value.definition.parameter.label()
    val unit = value.definition.unit.takeIf { it.isNotBlank() && it != label } // pH's unit is pH
    val shown = value.value?.let { formatter.number(it, value.definition.precision) }
    val container = levelContainerColor(value.level)
    val textColor = levelTextColor(value.level) ?: colors.onSurface
    val secondaryColor = if (container != null) textColor else colors.onSurfaceVariant
    val shape = RoundedCornerShape(20.dp)
    val unmeasured = stringResource(Res.string.home_no_measurement_yet)
    val age = value.ageDays(today)?.let { days -> pluralStringResource(Res.plurals.reading_age_days, days, days) }
    val trend = value.trend?.let(::trendIcon)
    val trendWords = trend?.let { stringResource(it.second) }
    val levelWords = levelDescription(value.level)
    val spoken = listOfNotNull(label, shown ?: unmeasured, shown?.let { unit }, age, trendWords, levelWords).joinToString(", ")
    Column(
        // Grows past 96 dp rather than clipping: « Ammoniak / Ammonium » or « 25,5 °C » at 150 %.
        modifier.widthIn(min = 96.dp)
            .background(container ?: colors.surface, shape)
            .let { if (container == null) it.border(BorderStroke(1.dp, colors.outlineVariant), shape) else it }
            .padding(horizontal = 10.dp, vertical = 12.dp)
            .clearAndSetSemantics { text = AnnotatedString(spoken) },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value.level != ReadingLevel.OK) {
                LevelMark(value.level, textColor, Modifier.padding(end = 4.dp).testTag(LEVEL_MARK_TAG))
            }
            Text(label, fontSize = 12.sp, color = secondaryColor, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                shown ?: "—", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = textColor,
                style = TextStyle(fontFeatureSettings = "tnum"), maxLines = 1,
            )
            if (shown != null && unit != null) {
                Text(text = " $unit", fontSize = 12.sp, color = textColor, maxLines = 1, modifier = Modifier.padding(bottom = 3.dp))
            }
            if (trend != null) {
                Icon(
                    trend.first, contentDescription = null, tint = secondaryColor,
                    modifier = Modifier.align(Alignment.CenterVertically).padding(start = 4.dp).size(16.dp),
                )
            }
        }
        if (age != null || reserveAgeLine) Text(age.orEmpty(), fontSize = 11.sp, color = secondaryColor, maxLines = 1)
    }
}

/** A small triangle beside the label of an out-of-range value: outlined for attention, filled for alert. */
@Composable
internal fun LevelMark(level: ReadingLevel, color: Color, modifier: Modifier) {
    Canvas(modifier.size(10.dp).clearAndSetSemantics {}) {
        val triangle = Path().apply {
            moveTo(size.width / 2, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        if (level == ReadingLevel.ALERT) {
            drawPath(triangle, color)
        } else {
            drawPath(triangle, color, style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round))
        }
    }
}

/** Horizontally scrolling row of [ValueTile]s with edge chevrons that appear only when more values are out of view. */
@Composable
fun ValueRow(values: List<HomeValue>, formatter: LocalizedFormatters, today: LocalDate, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val canBack = state.canScrollBackward
    val canForward = state.canScrollForward
    val anyAge = values.any { it.ageDays(today) != null }
    fun page(direction: Int) = scope.launch {
        val viewport = state.layoutInfo.viewportSize.width
        state.animateScrollBy(direction * (viewport - with(density) { 40.dp.toPx() }))
    }
    Box(modifier.fillMaxWidth()) {
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(values, key = { index, v -> "${index}_${v.definition.id}" }) { _, v ->
                ValueTile(v, formatter, today, reserveAgeLine = anyAge)
            }
        }
        // matchParentSize: the row's own height, so the fade covers the tiles top to bottom (a list item has no bounded height).
        if (canBack) {
            Box(Modifier.matchParentSize()) {
                Chevron(
                    AquagendaIcons.ChevronLeft, stringResource(Res.string.home_values_previous), atStart = true,
                    onClick = { page(-1) },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
        }
        if (canForward) {
            Box(Modifier.matchParentSize()) {
                Chevron(
                    AquagendaIcons.ChevronRight, stringResource(Res.string.home_values_next), atStart = false,
                    onClick = { page(1) },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }
}

@Composable
private fun Chevron(icon: ImageVector, description: String, atStart: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val fade = listOf(colors.background.copy(alpha = .85f), Color.Transparent)
    Box(
        modifier.width(56.dp).fillMaxHeight()
            .background(Brush.horizontalGradient(if (atStart) fade else fade.reversed())),
        contentAlignment = if (atStart) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Box(
            Modifier.size(48.dp).clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(30.dp).shadow(4.dp, CircleShape).background(colors.surfaceContainerHigh, CircleShape)
                    .border(1.dp, colors.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(18.dp)) }
        }
    }
}

/**
 * Measures the content at full height but reports [overlap] less to the parent, so the next list item,
 * drawn later, covers the content's bottom.
 */
fun Modifier.overlapNext(overlap: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val height = (placeable.height - overlap.roundToPx()).coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(placeable.width, height) { placeable.place(0, 0) }
}

/** The shade over a [WaterHeader]'s whole height: its lightest stop is what the text's contrast is checked against (ScenePhysicsTest). */
internal val WaterHeaderShadeTop = Color.Black.copy(alpha = .45f)
internal val WaterHeaderShadeBottom = Color.Black.copy(alpha = .25f)
internal val WaterHeaderShade: Brush = Brush.verticalGradient(0f to WaterHeaderShadeTop, 1f to WaterHeaderShadeBottom)

/**
 * The compact water of a screen opened over the home: the tank scene, a title (and subtitle) with an [action] slot at the right (a glass button),
 * then [below]. The paper sheet that follows overlaps its bottom by 30 dp ([overlapNext]); it grows with large fonts.
 */
@Composable
fun WaterHeader(
    aquarium: Aquarium,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
    below: @Composable () -> Unit = {},
) {
    Box(modifier.fillMaxWidth().heightIn(min = 170.dp)) {
        // Still water: motion belongs to the large headers only (spec §3).
        TankScene(aquarium, inhabitants = emptyList(), modifier = Modifier.matchParentSize(), decorPlants = 0, animate = false)
        Box(Modifier.matchParentSize().background(WaterHeaderShade))
        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(top = 8.dp)) {
                    Text(
                        title,
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = frauncesFamily(), fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 32.sp,
                        ),
                        color = HeroText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(subtitle, modifier = Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = HeroText)
                }
                action()
            }
            below()
            Spacer(Modifier.height(30.dp + 16.dp))
        }
    }
}

/** Test tag of the selected period's pill, so a test can check it fills its segment. */
internal const val PERIOD_PILL_TAG = "period-pill"

/** The period track's tint; it lies on the paper ([ColorScheme.background]). */
internal fun ColorScheme.periodTrack(): Color = surfaceVariant.copy(alpha = .6f)

/** The selected period's pill: it must stand out from the track by 3:1 in both themes (CarnetColorTest). */
internal fun ColorScheme.periodPill(): Color = primary

/** The selected period's label, on [periodPill]. */
internal fun ColorScheme.onPeriodPill(): Color = onPrimary

/**
 * Period choice as a pill row: a tinted track, the selected option in a raised [periodPill] that fills its segment. Labels
 * stay on one line; when they don't fit (long German words, large fonts) the row scrolls sideways instead of wrapping.
 * A [selectedIndex] of -1 selects none: the screen shows a window that is none of these periods.
 */
@Composable
fun PeriodSegmented(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val track = RoundedCornerShape(24.dp)
    val pill = RoundedCornerShape(20.dp)
    // The track (tint, rim, rounded corners) is the outer box; the scroll lives inside it, so a pill's shadow and the corners
    // are never cut by the scroll container. Options share the track equally; one that needs more grows and the row scrolls.
    BoxWithConstraints(
        modifier.fillMaxWidth().height(48.dp)
            .background(colors.periodTrack(), track)
            .border(1.dp, colors.outlineVariant, track),
    ) {
        val equalWidth = maxWidth / labels.size.coerceAtLeast(1)
        Row(
            Modifier.fillMaxHeight().horizontalScroll(rememberScrollState()).selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                // The scroll leaves the width unbounded: the segment hands its minimum size down so the pill fills it.
                Box(
                    Modifier.fillMaxHeight().defaultMinSize(minWidth = maxOf(equalWidth, 48.dp))
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) })
                        .padding(3.dp),
                    contentAlignment = Alignment.Center,
                    propagateMinConstraints = true,
                ) {
                    Box(
                        Modifier.fillMaxSize()
                            .then(
                                if (selected) {
                                    Modifier.testTag(PERIOD_PILL_TAG).shadow(2.dp, pill).background(colors.periodPill(), pill)
                                } else {
                                    Modifier
                                },
                            )
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                            color = if (selected) colors.onPeriodPill() else colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun SessionTileState.level(): ReadingLevel = when (this) {
    SessionTileState.ABOVE, SessionTileState.BELOW -> ReadingLevel.ATTENTION
    SessionTileState.FAR_ABOVE, SessionTileState.FAR_BELOW -> ReadingLevel.ALERT
    else -> ReadingLevel.OK
}

/**
 * « label value unit » as a small pill: tinted and marked when out of range, neutral otherwise. Decorative for semantics:
 * the card that holds it speaks its readings itself.
 */
@Composable
fun ValuePill(label: String, value: String, unit: String?, state: SessionTileState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val level = state.level()
    val container = levelContainerColor(level)
    val textColor = levelTextColor(level) ?: colors.onSurface
    val secondary = if (container != null) textColor else colors.onSurfaceVariant
    Row(
        modifier.background(container ?: colors.surfaceVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (level != ReadingLevel.OK) LevelMark(level, textColor, Modifier.padding(end = 4.dp).testTag(LEVEL_MARK_TAG))
        val spaced = SpanStyle(fontSize = 13.sp, color = textColor)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 13.sp, color = secondary)) { append(label) }
                append(' ')
                withStyle(spaced.copy(fontWeight = FontWeight.SemiBold)) { append(value) }
                if (unit != null) {
                    append(' ')
                    withStyle(spaced) { append(unit) }
                }
            },
            style = TextStyle(fontFeatureSettings = "tnum"),
        )
    }
}

/** The dot on a timeline: a filled circle when [emphasized] (every Session), a tinted one for the others. Decorative. */
@Composable
fun TimelineNode(icon: ImageVector, emphasized: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.size(36.dp).background(if (emphasized) colors.primary else colors.secondaryContainer, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, contentDescription = null, modifier = Modifier.size(18.dp),
            tint = if (emphasized) colors.onPrimary else colors.primary,
        )
    }
}
