package com.maximerollin.aqualog.ui.theme

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.TextStyle
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
private fun LevelMark(level: ReadingLevel, color: Color, modifier: Modifier) {
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
