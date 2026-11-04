package com.maximerollin.aqualog.photo

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.home.TankScene
import com.maximerollin.aqualog.shared.Aquarium

/** The Aquarium's photo, or its still mini-scene when it has none (or the photo cannot be read). */
@Composable
fun AquariumThumbnail(
    aquarium: Aquarium,
    size: Dp,
    loadPhoto: suspend (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
    cachedPhoto: (String) -> ImageBitmap? = { null },
) {
    val photoId = aquarium.photoId
    // A photo already decoded shows on the first frame, without the mini-scene flashing first.
    val photo by produceState(photoId?.let(cachedPhoto), photoId) { if (value == null) value = photoId?.let { loadPhoto(it) } }
    val clipped = modifier.size(size).clip(RoundedCornerShape(12.dp))
    photo?.let { Image(it, contentDescription = null, modifier = clipped, contentScale = ContentScale.Crop) }
        ?: TankScene(aquarium, clipped, animate = false)
}
