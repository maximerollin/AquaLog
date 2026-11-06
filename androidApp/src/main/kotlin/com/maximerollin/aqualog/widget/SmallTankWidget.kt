package com.maximerollin.aqualog.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ColorFilter
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.testTag
import androidx.glance.unit.ColorProvider
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.maximerollin.aqualog.R
import com.maximerollin.aqualog.WidgetTarget
import com.maximerollin.aqualog.shared.encyclopedia.DrawingFamily
import com.maximerollin.aqualog.shared.encyclopedia.PresentGroup
import com.maximerollin.aqualog.shared.encyclopedia.SwimZone

class SmallTankWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = tankWidgetContent(context, getAppWidgetState<Preferences>(context, id)[PINNED_AQUARIUM_ID])
        provideContent {
            val content = liveWidgetContent(initial) { tankWidgetContent(context, it[PINNED_AQUARIUM_ID]) }
            WidgetTheme { SmallTankContent(content) }
        }
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
        is WidgetContent.Tank -> SmallTank(content.text, content.groups)
        // shortcut: the small widget never loads every Aquarium; this falls back to the first one.
        is WidgetContent.Tanks -> content.rows.firstOrNull()?.let { SmallTank(it, emptyList()) } ?: NoAquariumInvitation()
    }
}

@Composable
private fun SmallTank(text: WidgetTankText, groups: List<PresentGroup>) {
    Column(
        widgetSurface(openAppAction(text.aquariumId, WidgetTarget.HOME))
            .background(GlanceTheme.colors.surface)
            .semantics { contentDescription = text.contentDescription },
    ) {
        Box(GlanceModifier.fillMaxWidth().height(42.dp)) {
            Image(
                ImageProvider(R.drawable.widget_scene),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize(),
            )
            groups.take(3).forEachIndexed { slot, group -> Silhouette(group, slot) }
        }
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

internal fun WidgetFamilyTag(family: DrawingFamily) = "widget-family-${family.name.lowercase()}"

// A custom group has no species colour: near-white sand, 3.4:1 on the lightest water of widget_scene.
private const val CustomTint = 0xFFF7F3E8

// Species tints sit near the water and sand tones; a black under-layer outlines every silhouette:
// 3.1:1 on the darkest water, 5.6:1 on the lightest, 4.2:1 on the plants, 12.2:1 on the sand.
private const val HaloTint = 0xFF000000

/** One silhouette of the scene: vertical place by the group's zone, horizontal by its rank (largest first). */
@Composable
private fun Silhouette(group: PresentGroup, slot: Int) {
    val vertical = when (group.species?.zone ?: familyZone(group.family)) {
        SwimZone.SURFACE -> Alignment.Top
        SwimZone.MIDDLE -> Alignment.CenterVertically
        SwimZone.BOTTOM -> Alignment.Bottom
    }
    val horizontal = listOf(Alignment.Start, Alignment.CenterHorizontally, Alignment.End)[slot]
    Box(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 2.dp), contentAlignment = Alignment(horizontal, vertical)) {
        Box(GlanceModifier.size(28.dp), contentAlignment = Alignment.Center) {
            Image(
                ImageProvider(familyDrawable(group.family)),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ColorProvider(Color(HaloTint))),
                modifier = GlanceModifier.fillMaxSize(),
            )
            Image(
                ImageProvider(familyDrawable(group.family)),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ColorProvider(Color(group.species?.color ?: CustomTint))),
                modifier = GlanceModifier.size(24.dp).semantics { testTag = WidgetFamilyTag(group.family) },
            )
        }
    }
}

private fun familyZone(family: DrawingFamily) = when (family) {
    DrawingFamily.LABYRINTH -> SwimZone.SURFACE
    DrawingFamily.CATFISH, DrawingFamily.SHRIMP, DrawingFamily.SNAIL, DrawingFamily.PLANT -> SwimZone.BOTTOM
    else -> SwimZone.MIDDLE
}

// Plants never reach the widget (decor, no twin drawable): the PLANT arm only keeps the mapping total.
private fun familyDrawable(family: DrawingFamily) = when (family) {
    DrawingFamily.TETRA -> R.drawable.widget_family_tetra
    DrawingFamily.LIVEBEARER -> R.drawable.widget_family_livebearer
    DrawingFamily.CICHLID -> R.drawable.widget_family_cichlid
    DrawingFamily.LABYRINTH -> R.drawable.widget_family_labyrinth
    DrawingFamily.CYPRINID -> R.drawable.widget_family_cyprinid
    DrawingFamily.CATFISH -> R.drawable.widget_family_catfish
    DrawingFamily.SHRIMP -> R.drawable.widget_family_shrimp
    DrawingFamily.SNAIL -> R.drawable.widget_family_snail
    DrawingFamily.PLANT -> R.drawable.widget_family_snail
}
