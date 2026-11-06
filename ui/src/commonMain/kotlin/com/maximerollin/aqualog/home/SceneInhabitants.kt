package com.maximerollin.aqualog.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.maximerollin.aqualog.resources.Res
import com.maximerollin.aqualog.resources.allDrawableResources
import com.maximerollin.aqualog.resources.family_catfish
import com.maximerollin.aqualog.resources.family_cichlid
import com.maximerollin.aqualog.resources.family_cyprinid
import com.maximerollin.aqualog.resources.family_labyrinth
import com.maximerollin.aqualog.resources.family_livebearer
import com.maximerollin.aqualog.resources.family_plant
import com.maximerollin.aqualog.resources.family_shrimp
import com.maximerollin.aqualog.resources.family_snail
import com.maximerollin.aqualog.resources.family_tetra
import com.maximerollin.aqualog.shared.Inhabitant
import com.maximerollin.aqualog.shared.encyclopedia.DrawingFamily
import com.maximerollin.aqualog.shared.encyclopedia.MotionFamily
import com.maximerollin.aqualog.shared.encyclopedia.Species
import com.maximerollin.aqualog.shared.encyclopedia.SwimZone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.jetbrains.compose.resources.DrawableResource

/** One animal drawn in the scene. [tint] recolours a family silhouette; a dedicated drawing has none. */
data class SceneSprite(val drawing: DrawableResource, val tint: Color?, val motion: MotionFamily, val zone: SwimZone, val seed: Int)

/** A group's drawing: its species' own, else its family silhouette tinted with the species colour (or a default one). */
fun inhabitantDrawing(inhabitant: Inhabitant, species: Species?): Pair<DrawableResource, Color?> =
    species?.drawing?.let { Res.allDrawableResources[it] }?.let { it to null }
        ?: (familyDrawing(inhabitant.family) to Color(species?.color ?: if (inhabitant.family == DrawingFamily.PLANT) 0xFF4F9D5D else 0xFFF2A65A))

private fun familyDrawing(family: DrawingFamily): DrawableResource = when (family) {
    DrawingFamily.TETRA -> Res.drawable.family_tetra
    DrawingFamily.LIVEBEARER -> Res.drawable.family_livebearer
    DrawingFamily.CICHLID -> Res.drawable.family_cichlid
    DrawingFamily.LABYRINTH -> Res.drawable.family_labyrinth
    DrawingFamily.CYPRINID -> Res.drawable.family_cyprinid
    DrawingFamily.CATFISH -> Res.drawable.family_catfish
    DrawingFamily.SHRIMP -> Res.drawable.family_shrimp
    DrawingFamily.SNAIL -> Res.drawable.family_snail
    DrawingFamily.PLANT -> Res.drawable.family_plant
}

/** How a group outside the encyclopedia (custom, or unreadable file) moves: its family's usual way. */
private fun familyMotion(family: DrawingFamily): Pair<MotionFamily, SwimZone> = when (family) {
    DrawingFamily.TETRA, DrawingFamily.CYPRINID -> MotionFamily.SCHOOL to SwimZone.MIDDLE
    DrawingFamily.LIVEBEARER, DrawingFamily.CICHLID -> MotionFamily.GLIDE to SwimZone.MIDDLE
    DrawingFamily.LABYRINTH -> MotionFamily.SLOW to SwimZone.SURFACE
    DrawingFamily.CATFISH -> MotionFamily.BOTTOM to SwimZone.BOTTOM
    DrawingFamily.SHRIMP, DrawingFamily.SNAIL, DrawingFamily.PLANT -> MotionFamily.CRAWL to SwimZone.BOTTOM
}

/**
 * At most [max] sprites for the present groups, shared in proportion to their counts with at least one per group
 * (the biggest groups when there are more than [max]). Plants are decor, not swimmers: they add none.
 * A sprite's seed is its group's (from the group id, so it keeps its place when other groups change) times 16 plus its rank.
 */
fun sceneSprites(present: List<Inhabitant>, species: Map<String, Species>, max: Int = 12): List<SceneSprite> {
    require(max <= 16) { "A sprite's rank is packed in the low 4 bits of its seed" }
    val groups = present.filter { it.family != DrawingFamily.PLANT && it.count > 0 }.sortedByDescending { it.count }.take(max)
    val shown = IntArray(groups.size) { 1 }
    repeat(max - groups.size) {
        // Each spare sprite goes to the group with the most animals per sprite already shown.
        val next = groups.indices.filter { shown[it] < groups[it].count }.maxByOrNull { groups[it].count.toFloat() / shown[it] }
        if (next != null) shown[next]++
    }
    return groups.flatMapIndexed { index, group ->
        val known = group.speciesId?.let(species::get)
        val (drawing, tint) = inhabitantDrawing(group, known)
        val (motion, zone) = known?.let { it.motion to it.zone } ?: familyMotion(group.family)
        List(shown[index]) { rank -> SceneSprite(drawing, tint, motion, zone, (group.id.hashCode() and 0xFFFFF) shl 4 or rank) }
    }
}

/** Plants are decor, not swimmers: each plant group adds one plant to the scene's decor, up to 3. */
fun decorPlants(present: List<Inhabitant>): Int = present.count { it.family == DrawingFamily.PLANT }.coerceAtMost(3)

/**
 * Top-left corner of a sprite at [elapsedMillis], inside a [width] × [height] area (the scene minus the sprite's size,
 * so it is never clipped). Pure: elapsed 0 is the still frame reduced motion shows.
 */
fun spritePosition(motion: MotionFamily, zone: SwimZone, seed: Int, elapsedMillis: Long, width: Float, height: Float): Offset {
    // 0..1 and back, eased at both ends where the sprite turns round; the phase comes from the key.
    fun swim(periodMillis: Int, key: Int, salt: Int) =
        (1 - cos(2 * PI.toFloat() * cycleProgress(elapsedMillis + (noise(key, salt) * periodMillis).toLong(), periodMillis))) / 2
    fun bob(periodMillis: Int) = sin(2 * PI.toFloat() * cycleProgress(elapsedMillis + (noise(seed, 9) * periodMillis).toLong(), periodMillis))
    // Called for every sprite on every frame: plain floats and an Offset, nothing allocated.
    val top = when (zone) {
        SwimZone.SURFACE -> .05f
        SwimZone.MIDDLE -> .3f
        SwimZone.BOTTOM -> .8f
    }
    val bottom = when (zone) {
        SwimZone.SURFACE -> .3f
        SwimZone.MIDDLE -> .65f
        SwimZone.BOTTOM -> 1f
    }
    val inZone = top + (bottom - top) * noise(seed, 4)
    val at = when (motion) {
        MotionFamily.SCHOOL -> {
            val school = seed shr 4
            val centre = .15f + .7f * swim(26_000 + (noise(school, 1) * 8_000).toInt(), school, 2)
            Offset(centre + (noise(seed, 3) - .5f) * .2f, (inZone + .03f * bob(3_000)).coerceIn(top, bottom))
        }
        MotionFamily.BOTTOM -> Offset(.05f + .9f * swim(36_000 + (noise(seed, 1) * 12_000).toInt(), seed, 2), .8f + .2f * noise(seed, 4))
        MotionFamily.GLIDE -> Offset(.05f + .9f * swim(44_000 + (noise(seed, 1) * 12_000).toInt(), seed, 2), (inZone + .03f * bob(8_000)).coerceIn(top, bottom))
        MotionFamily.CRAWL -> {
            // Most crawl on the sand; one in four on the front glass.
            val onGlass = noise(seed, 6) < .25f
            Offset(.7f * noise(seed, 1) + .3f * swim(80_000, seed, 2), if (onGlass) .45f + .3f * noise(seed, 4) else .82f + .18f * noise(seed, 4))
        }
        MotionFamily.SLOW -> Offset(.1f + .7f * noise(seed, 1) + .1f * swim(40_000, seed, 2), (inZone + .02f * bob(7_000)).coerceIn(top, bottom))
    }
    return Offset(at.x.coerceIn(0f, 1f) * width, at.y.coerceIn(0f, 1f) * height)
}

/** A stable pseudo-random 0..1 for a seed and a salt (an integer hash, so it never changes between runs). */
private fun noise(seed: Int, salt: Int): Float {
    var h = seed * -0x61c88647 + salt * -0x7a143595
    h = (h xor (h ushr 16)) * 0x7feb352d
    h = (h xor (h ushr 15)) * -0x7b935975
    h = h xor (h ushr 16)
    return (h ushr 8) / 16_777_216f
}
