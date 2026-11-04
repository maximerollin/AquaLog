package com.maximerollin.aqualog.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
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
import androidx.glance.layout.ContentScale
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.Spacer
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.maximerollin.aqualog.AquaLogApplication
import com.maximerollin.aqualog.WidgetTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TanksWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = tanksWidgetContent(context)
        val initialPhotos = tankPhotos(context, initial)
        provideContent {
            val content = liveWidgetContent(initial) { tanksWidgetContent(context) }
            val photoIds = (content as? WidgetContent.Tanks)?.rows?.map { it.aquariumId to it.photoId }
            val photos = produceState(initialPhotos, photoIds) {
                if (content !== initial) value = tankPhotos(context, content)
            }.value
            WidgetTheme { TanksContent(content, photos) }
        }
    }
}

class TanksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TanksWidget()
}

@Composable
fun TanksContent(content: WidgetContent, photos: Map<String, Bitmap> = emptyMap()) {
    when (content) {
        WidgetContent.NoAquarium -> NoAquariumInvitation()
        WidgetContent.NeedsPro -> NeedsProInvitation()
        // shortcut: « Mes bacs » never loads a single Aquarium; this falls back to the NoAquarium invitation.
        is WidgetContent.Tank -> NoAquariumInvitation()
        is WidgetContent.Tanks -> TankList(content.rows, photos)
    }
}

@Composable
private fun TankList(rows: List<WidgetTankText>, photos: Map<String, Bitmap>) {
    // No click on the card: each row opens its own Aquarium.
    LazyColumn(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(16.dp)
            .background(GlanceTheme.colors.surface).padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        items(rows, itemId = { it.aquariumId.hashCode().toLong() }) { TankRow(it, photos[it.aquariumId]) }
    }
}

@Composable
private fun TankRow(text: WidgetTankText, photo: Bitmap?) {
    Row(
        GlanceModifier.fillMaxWidth().height(48.dp)
            .clickable(openAppAction(text.aquariumId, WidgetTarget.HOME))
            .semantics { contentDescription = text.contentDescription },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Decorative: the row's description already reads the name.
        if (photo != null) {
            Image(
                ImageProvider(photo), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = GlanceModifier.size(36.dp).cornerRadius(10.dp).semantics { testTag = TankPhotoTag(text.aquariumId) },
            )
            Spacer(GlanceModifier.width(12.dp))
        }
        StatusDot(text)
        Column(GlanceModifier.defaultWeight().padding(start = 12.dp)) {
            Text(text.name, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp), maxLines = 1)
            Text(text.next, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp), maxLines = 1)
        }
    }
}

internal fun TankPhotoTag(aquariumId: String) = "tank-photo-$aquariumId"

private const val PHOTO_TARGET_PX = 96

/** The largest power-of-two subsampling that keeps the shorter side at [PHOTO_TARGET_PX] or more. */
internal fun tankPhotoSampleSize(width: Int, height: Int): Int {
    var sample = 1
    while (minOf(width, height) / (sample * 2) >= PHOTO_TARGET_PX) sample *= 2
    return sample
}

/** One small bitmap per listed Aquarium that has a readable photo; the others are simply left out. */
private suspend fun tankPhotos(context: Context, content: WidgetContent): Map<String, Bitmap> {
    val rows = (content as? WidgetContent.Tanks)?.rows ?: return emptyMap()
    val repository = (context.applicationContext as AquaLogApplication).aquariumRepository
    return withContext(Dispatchers.IO) {
        buildMap {
            for (row in rows) {
                val photoId = row.photoId ?: continue
                // A missing or corrupt photo is « no image » for that row, never a crash.
                val bytes = runCatching { repository.readAquariumPhoto(photoId) }.getOrNull() ?: continue
                decodeTankPhoto(bytes)?.let { put(row.aquariumId, it) }
            }
        }
    }
}

private fun decodeTankPhoto(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val options = BitmapFactory.Options().apply { inSampleSize = tankPhotoSampleSize(bounds.outWidth, bounds.outHeight) }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}
