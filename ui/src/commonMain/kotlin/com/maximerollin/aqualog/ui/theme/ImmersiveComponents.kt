package com.maximerollin.aqualog.ui.theme

import androidx.compose.foundation.BorderStroke
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
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Round translucent button laid over the full-bleed water scene: white glass, white icon. */
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
            Modifier.size(40.dp).background(Color.White.copy(alpha = .2f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = .3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
    }
}

/** Top edge of the paper sheet that slides over the scene: rounded corners, handle, soft shadow. A list item, not a container. */
@Composable
fun PaperSheetTop(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    Box(
        modifier.fillMaxWidth().height(30.dp)
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

/** One value of the home hero: label, big tabular number with its unit, tinted when out of range. */
@Composable
fun ValueTile(value: HomeValue, formatter: LocalizedFormatters, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val label = value.definition.parameter.label()
    val unit = value.definition.unit.takeIf { it.isNotBlank() && it != label } // pH's unit is pH
    val shown = value.value?.let { formatter.number(it, value.definition.precision) }
    val container = levelContainerColor(value.level)
    val textColor = levelTextColor(value.level) ?: colors.onSurface
    val shape = RoundedCornerShape(20.dp)
    val unmeasured = stringResource(Res.string.home_no_measurement_yet)
    val levelWords = levelDescription(value.level)
    val spoken = listOfNotNull(label, shown ?: unmeasured, shown?.let { unit }, levelWords).joinToString(", ")
    Column(
        // Grows past 96 dp rather than clipping: « Ammoniak / Ammonium » or « 25,5 °C » at 150 %.
        modifier.widthIn(min = 96.dp)
            .background(container ?: colors.surface, shape)
            .let { if (container == null) it.border(BorderStroke(1.dp, colors.outlineVariant), shape) else it }
            .padding(horizontal = 10.dp, vertical = 12.dp)
            .clearAndSetSemantics { text = AnnotatedString(spoken) },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, fontSize = 12.sp, color = if (container != null) textColor else colors.onSurfaceVariant, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                shown ?: "—", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = textColor,
                style = TextStyle(fontFeatureSettings = "tnum"), maxLines = 1,
            )
            if (shown != null && unit != null) {
                Text(text = " $unit", fontSize = 12.sp, color = textColor, maxLines = 1, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

/** Horizontally scrolling row of [ValueTile]s with edge chevrons that appear only when more values are out of view. */
@Composable
fun ValueRow(values: List<HomeValue>, formatter: LocalizedFormatters, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val canBack = state.canScrollBackward
    val canForward = state.canScrollForward
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
            itemsIndexed(values, key = { index, v -> "${index}_${v.definition.id}" }) { _, v -> ValueTile(v, formatter) }
        }
        if (canBack) {
            Chevron(
                AquagendaIcons.ChevronLeft, stringResource(Res.string.home_values_previous), atStart = true,
                onClick = { page(-1) },
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
        if (canForward) {
            Chevron(
                AquagendaIcons.ChevronRight, stringResource(Res.string.home_values_next), atStart = false,
                onClick = { page(1) },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun Chevron(icon: ImageVector, description: String, atStart: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val fade = listOf(colors.background.copy(alpha = .35f), Color.Transparent)
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
                Modifier.size(30.dp).shadow(4.dp, CircleShape).background(colors.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(18.dp)) }
        }
    }
}
