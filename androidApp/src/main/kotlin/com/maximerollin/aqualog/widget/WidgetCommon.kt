package com.maximerollin.aqualog.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.maximerollin.aqualog.AquaLogApplication
import com.maximerollin.aqualog.MainActivity
import com.maximerollin.aqualog.R
import com.maximerollin.aqualog.WidgetTarget
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.encyclopedia.PresentGroup
import com.maximerollin.aqualog.shared.isActiveAt
import com.maximerollin.aqualog.ui.theme.AlertMark
import com.maximerollin.aqualog.ui.theme.AlertMarkDark
import com.maximerollin.aqualog.ui.theme.AttentionMark
import com.maximerollin.aqualog.ui.theme.AttentionMarkDark
import com.maximerollin.aqualog.ui.theme.CarnetDarkColors
import com.maximerollin.aqualog.ui.theme.CarnetLightColors
import java.time.Instant
import java.time.ZoneId

sealed interface WidgetContent {
    data object NoAquarium : WidgetContent
    data object NeedsPro : WidgetContent
    data class Tank(val text: WidgetTankText, val groups: List<PresentGroup> = emptyList()) : WidgetContent
    data class Tanks(val rows: List<WidgetTankText>) : WidgetContent
}

/** Glance state of the small and medium widgets: the Aquarium pinned by reconfiguring, absent to follow the app. */
val PINNED_AQUARIUM_ID = stringPreferencesKey("aquarium-id")

/** Glance state bumped by [updateAllWidgets] so a live session reloads its content. */
val REFRESH_COUNT = longPreferencesKey("refresh-count")

/** What a one-Aquarium widget shows: [pinnedId] if it still exists, else the Aquarium last viewed in the app. */
suspend fun tankWidgetContent(context: Context, pinnedId: String?): WidgetContent {
    val application = context.applicationContext as AquaLogApplication
    val (today, taskDate) = application.widgetToday()
    val state = application.aquariumRepository.loadWidgetAquarium(pinnedId, taskDate, application.currentTimeZoneId())
        ?: return WidgetContent.NoAquarium
    if (!application.isProCached()) return WidgetContent.NeedsPro
    return WidgetContent.Tank(widgetText(state, today), state.groups)
}

/** What « Mes bacs » shows: every non-archived Aquarium, gated like [tankWidgetContent]. */
suspend fun tanksWidgetContent(context: Context): WidgetContent {
    val application = context.applicationContext as AquaLogApplication
    val (today, taskDate) = application.widgetToday()
    val states = application.aquariumRepository.loadWidgetAquariums(taskDate, application.currentTimeZoneId())
    if (states.isEmpty()) return WidgetContent.NoAquarium
    if (!application.isProCached()) return WidgetContent.NeedsPro
    return WidgetContent.Tanks(states.map { widgetText(it, today) })
}

/**
 * [initial], then [load] again each time the widget's Glance state changes. A live Glance session (about 45 s after
 * a composition) only recomposes from that state on update(), so [updateAllWidgets] bumps it to bring new data.
 */
@Composable
fun <T> liveWidgetContent(initial: T, load: suspend (Preferences) -> T): T {
    val state = currentState<Preferences>()
    return produceState(initial, state) { value = load(state) }.value
}

private fun AquaLogApplication.isProCached() = proEntitlementCache.read().isActiveAt(currentTimeMillis())

private fun AquaLogApplication.widgetToday(): Pair<java.time.LocalDate, TaskDate> {
    val today = Instant.ofEpochMilli(currentTimeMillis()).atZone(ZoneId.of(currentTimeZoneId())).toLocalDate()
    return today to TaskDate(today.year, today.monthValue, today.dayOfMonth)
}

private val TargetKey = ActionParameters.Key<String>(MainActivity.EXTRA_WIDGET_TARGET)
private val AquariumKey = ActionParameters.Key<String>(MainActivity.EXTRA_WIDGET_AQUARIUM_ID)

/** Opens MainActivity, which reads the parameters as intent extras. */
fun openAppAction(aquariumId: String?, target: WidgetTarget): Action = actionStartActivity<MainActivity>(
    if (aquariumId == null) actionParametersOf(TargetKey to target.name)
    else actionParametersOf(TargetKey to target.name, AquariumKey to aquariumId),
)

@Composable
fun WidgetTheme(content: @Composable () -> Unit) {
    GlanceTheme(colors = ColorProviders(light = CarnetLightColors, dark = CarnetDarkColors), content = content)
}

/** The widget's card: the whole surface is one tap target, so it is far above 48 dp. */
fun widgetSurface(action: Action): GlanceModifier = GlanceModifier.fillMaxSize()
    .appWidgetBackground()
    .cornerRadius(16.dp)
    .clickable(action)

/** The home's status dot; colour never carries the state alone, [WidgetTankText.lightDescription] does. */
@Composable
fun StatusDot(text: WidgetTankText) {
    val color = when (text.light) {
        WidgetLight.ALERT -> ColorProvider(AlertMark, AlertMarkDark)
        WidgetLight.ATTENTION -> ColorProvider(AttentionMark, AttentionMarkDark)
        WidgetLight.OK -> if (text.isMeasured) GlanceTheme.colors.primary else GlanceTheme.colors.outline
    }
    // shortcut: cornerRadius is ignored below API 31, so the dot is a square there; use a shape drawable if that matters.
    Box(
        GlanceModifier.size(8.dp).cornerRadius(4.dp).background(color)
            .semantics { contentDescription = text.lightDescription },
    ) {}
}

@Composable
fun NeedsProInvitation() {
    val context = LocalContext.current
    Column(widgetSurface(openAppAction(null, WidgetTarget.PRO)).background(GlanceTheme.colors.surface).padding(12.dp)) {
        Text(
            context.getString(R.string.widget_needs_pro),
            style = TextStyle(color = GlanceTheme.colors.onSurface),
            maxLines = 3,
        )
        Text(
            context.getString(R.string.widget_needs_pro_action),
            style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold),
            maxLines = 1,
            modifier = GlanceModifier.padding(top = 8.dp),
        )
    }
}

@Composable
fun NoAquariumInvitation() {
    Column(widgetSurface(openAppAction(null, WidgetTarget.HOME)).background(GlanceTheme.colors.surface).padding(12.dp)) {
        Text(
            LocalContext.current.getString(R.string.widget_no_aquarium),
            style = TextStyle(color = GlanceTheme.colors.onSurface),
            maxLines = 4,
        )
    }
}
