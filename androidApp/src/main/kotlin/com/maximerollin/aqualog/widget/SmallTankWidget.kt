package com.maximerollin.aqualog.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.maximerollin.aqualog.R
import com.maximerollin.aqualog.WidgetTarget

class SmallTankWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val pinnedId = getAppWidgetState<Preferences>(context, id)[PINNED_AQUARIUM_ID]
        val content = tankWidgetContent(context, pinnedId)
        provideContent { WidgetTheme { SmallTankContent(content) } }
    }
}

class SmallTankWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SmallTankWidget()
}

@Composable
fun SmallTankContent(content: WidgetContent) {
    when (content) {
        WidgetContent.NoAquarium -> NoAquariumInvitation()
        WidgetContent.NeedsPro -> NeedsProInvitation()
        is WidgetContent.Tank -> SmallTank(content.text)
        // shortcut: the small widget never loads every Aquarium; this falls back to the first one.
        is WidgetContent.Tanks -> content.rows.firstOrNull()?.let { SmallTank(it) } ?: NoAquariumInvitation()
    }
}

@Composable
private fun SmallTank(text: WidgetTankText) {
    Column(
        widgetSurface(openAppAction(text.aquariumId, WidgetTarget.HOME))
            .background(GlanceTheme.colors.surface)
            .semantics { contentDescription = text.name + ", " + text.next },
    ) {
        Image(
            ImageProvider(R.drawable.widget_scene),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = GlanceModifier.fillMaxWidth().height(42.dp),
        )
        Column(GlanceModifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text.name,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                ),
                maxLines = 1,
            )
            Row(GlanceModifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(text)
                Text(
                    text.next,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
                    maxLines = 2,
                    modifier = GlanceModifier.padding(start = 6.dp),
                )
            }
        }
    }
}
