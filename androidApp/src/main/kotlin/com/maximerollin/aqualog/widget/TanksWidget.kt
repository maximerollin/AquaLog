package com.maximerollin.aqualog.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.maximerollin.aqualog.WidgetTarget

class TanksWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = tanksWidgetContent(context)
        provideContent {
            val content = liveWidgetContent(initial) { tanksWidgetContent(context) }
            WidgetTheme { TanksContent(content) }
        }
    }
}

class TanksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TanksWidget()
}

@Composable
fun TanksContent(content: WidgetContent) {
    when (content) {
        WidgetContent.NoAquarium -> NoAquariumInvitation()
        WidgetContent.NeedsPro -> NeedsProInvitation()
        // shortcut: « Mes bacs » never loads a single Aquarium; this falls back to the NoAquarium invitation.
        is WidgetContent.Tank -> NoAquariumInvitation()
        is WidgetContent.Tanks -> TankList(content.rows)
    }
}

@Composable
private fun TankList(rows: List<WidgetTankText>) {
    // No click on the card: each row opens its own Aquarium.
    LazyColumn(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(16.dp)
            .background(GlanceTheme.colors.surface).padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        items(rows, itemId = { it.aquariumId.hashCode().toLong() }) { TankRow(it) }
    }
}

@Composable
private fun TankRow(text: WidgetTankText) {
    Row(
        GlanceModifier.fillMaxWidth().height(48.dp)
            .clickable(openAppAction(text.aquariumId, WidgetTarget.HOME))
            .semantics { contentDescription = text.contentDescription },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(text)
        Column(GlanceModifier.defaultWeight().padding(start = 12.dp)) {
            Text(text.name, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp), maxLines = 1)
            Text(text.next, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp), maxLines = 1)
        }
    }
}
