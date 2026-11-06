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
import com.maximerollin.aqualog.home.SceneSprite
import com.maximerollin.aqualog.home.TankScene
import com.maximerollin.aqualog.shared.Aquarium

/** The Aquarium's photo, or its still mini-scene (with [inhabitants]) when it has none or the photo cannot be read. */
@Composable
fun AquariumThumbnail(
    aquarium: Aquarium,
    size: Dp,
    loadPhoto: suspend (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
    cachedPhoto: (String) -> ImageBitmap? = { null },
    inhabitants: List<SceneSprite> = emptyList(),
    decorPlants: Int = 0,
) {
    val photoId = aquarium.photoId
    // The seed only applies on the first frame (no mini-scene flash); later photo ids are always assigned, even null.
    val photo by produceState(photoId?.let(cachedPhoto), photoId) { value = photoId?.let { cachedPhoto(it) ?: loadPhoto(it) } }
    val clipped = modifier.size(size).clip(RoundedCornerShape(12.dp))
    photo?.let { Image(it, contentDescription = null, modifier = clipped, contentScale = ContentScale.Crop) }
        ?: TankScene(aquarium, inhabitants, clipped, decorPlants, animate = false)
}
