package com.maximerollin.aqualog.home

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.rememberReducedMotion
import com.maximerollin.aqualog.shared.Aquarium
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tan
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

private const val SwayMillis = 5_000
private const val BubbleMillis = 4_000
// Waiting this long, then for the next vsync, gives about 30 frames a second: plenty for a slow sway, half the battery of 60.
private const val FrameDelayMillis = 25L

/** Where a looping animation of [periodMillis] stands, 0..1, after [elapsedMillis]. */
fun cycleProgress(elapsedMillis: Long, periodMillis: Int): Float = (elapsedMillis % periodMillis).toFloat() / periodMillis

/** Sway angle in degrees for a 0..1 cycle: 0 at the ends, [amplitudeDegrees] at the middle, eased. */
fun plantSway(progress: Float, amplitudeDegrees: Float = 4f): Float =
    amplitudeDegrees * sin(PI.toFloat() * progress)

/** Vertical offset (negative is up) and alpha of a bubble for a 0..1 rise over [height]. */
// 0f - x (not -x) keeps the offset at +0f when progress is 0.
fun bubbleRise(progress: Float, height: Float): Pair<Float, Float> = (0f - progress * height) to 1f - progress

private class Plant(val x: Float, val height: Float, val bend: Float, val stem: Color, val delay: Float, val leaves: List<Pair<Float, Float>>)

private val leafColor = Color(0xFF5FB36F)
private val plants = listOf(
    Plant(.06f, 70f, -5f, Color(0xFF4F9D5D), 0f, listOf(-2f to 50f, 3f to 36f, -3f to 22f)),
    Plant(.78f, 84f, 5f, Color(0xFF4F9D5D), .4f, listOf(3f to 66f, -3f to 50f, 3f to 32f)),
    Plant(.9f, 56f, -4f, Color(0xFF5FB36F), .7f, listOf(-3f to 40f, 3f to 24f)),
)
// One per plant group of the Aquarium (decorPlants), up to 3, between the fixed ones.
private val extraPlants = listOf(
    Plant(.6f, 76f, -5f, Color(0xFF3F8D4F), .55f, listOf(-3f to 58f, 3f to 42f, -3f to 26f)),
    Plant(.42f, 62f, 4f, Color(0xFF4F9D5D), .2f, listOf(3f to 44f, -3f to 30f, 3f to 16f)),
    Plant(.2f, 48f, 3f, Color(0xFF5FB36F), .85f, listOf(3f to 34f, -3f to 20f)),
)
private val bubbles = listOf(.3f to 0f, .34f to .375f, .28f to .7f) // x fraction, phase
private val rays = listOf(.12f to 1f, .5f to .6f) // x fraction, alpha

/** Decorative tank (water, sand, plants, bubbles, light) with the [inhabitants] swimming in it and [decorPlants] more plants. */
@Composable
@Suppress("UNUSED_PARAMETER") // the decor is the same for every Aquarium; only the inhabitants differ
fun TankScene(
    aquarium: Aquarium,
    inhabitants: List<SceneSprite> = emptyList(),
    modifier: Modifier = Modifier,
    decorPlants: Int = 0,
    animate: Boolean = !rememberReducedMotion(),
) {
    val shownPlants = plants + extraPlants.take(decorPlants)
    val painters = inhabitants.map { it.drawing }.distinct().associateWith { key(it) { painterResource(it) } }
    // Read only while drawing: a new time redraws the scene without recomposing it. Stays 0 (a still frame) without motion.
    val elapsed = remember(animate) { mutableLongStateOf(0L) }
    if (animate) {
        LaunchedEffect(Unit) {
            var start = -1L
            while (true) {
                withInfiniteAnimationFrameNanos { now ->
                    if (start < 0) start = now
                    elapsed.longValue = (now - start) / 1_000_000
                }
                delay(FrameDelayMillis)
            }
        }
    }
    Spacer(
        modifier.height(140.dp).clearAndSetSemantics { }.drawWithCache {
            // Everything but the plant angles and the bubbles is built once per size.
            val dp = density
            val w = size.width
            val h = size.height
            val water = Brush.verticalGradient(listOf(Color(0xFF3D9184), Color(0xFF1F7468), Color(0xFF0F4A43)))
            val lean = tan(18f * PI.toFloat() / 180f) * h // top edge leans right of the bottom edge
            val rayPaths = rays.map { (x, alpha) ->
                val left = w * x
                val width = 30 * dp
                Path().apply {
                    moveTo(left + lean, 0f); lineTo(left + lean + width, 0f); lineTo(left + width, h); lineTo(left, h); close()
                } to Brush.verticalGradient(listOf(Color.White.copy(alpha = .14f * alpha), Color.Transparent), 0f, h)
            }
            val baseY = h - 10 * dp
            val stems = shownPlants.map { plant ->
                val x = w * plant.x
                Path().apply {
                    moveTo(x, baseY)
                    quadraticTo(x + plant.bend * dp, baseY - plant.height * dp / 2, x + plant.bend * -.3f * dp, baseY - plant.height * dp)
                }
            }
            val sandTop = h - 16 * dp
            val sand = Path().apply {
                moveTo(0f, h); lineTo(0f, sandTop + 6 * dp)
                quadraticTo(w / 2, sandTop - 6 * dp, w, sandTop + 6 * dp)
                lineTo(w, h); close()
            }
            val sandBrush = Brush.verticalGradient(listOf(Color(0xFFD6C497), Color(0xFFC3AE7E)), sandTop, h)
            val stemStroke = Stroke(3 * dp)
            val bubbleStroke = Stroke(dp)
            // Sprites are drawn at their vector size in the 140 dp scene, smaller in a thumbnail.
            val spriteScale = minOf(1f, h / (140 * dp))
            val tints = inhabitants.map { sprite -> sprite.tint?.let { ColorFilter.tint(it) } }
            val leafSize = Size(10 * dp, 4 * dp)
            onDrawBehind {
                val sway = cycleProgress(elapsed.longValue, SwayMillis)
                val rise = cycleProgress(elapsed.longValue, BubbleMillis)
                drawRect(water)
                rayPaths.forEach { (path, brush) -> drawPath(path, brush) }
                shownPlants.forEachIndexed { index, plant ->
                    val x = w * plant.x
                    rotate(plantSway((sway + plant.delay) % 1f), Offset(x, baseY)) {
                        drawPath(stems[index], plant.stem, style = stemStroke)
                        plant.leaves.forEach { (dx, y) ->
                            drawOval(leafColor, Offset(x + (dx - 5) * dp, baseY - y * dp - 2 * dp), leafSize)
                        }
                    }
                }
                drawPath(sand, sandBrush)
                inhabitants.forEachIndexed { index, sprite ->
                    val painter = painters.getValue(sprite.drawing)
                    val spriteSize = painter.intrinsicSize * spriteScale
                    val areaWidth = w - spriteSize.width
                    val areaHeight = h - spriteSize.height
                    val at = spritePosition(sprite.motion, sprite.zone, sprite.seed, elapsed.longValue, areaWidth, areaHeight)
                    // Drawings face right: one moving left is mirrored.
                    val ahead = spritePosition(sprite.motion, sprite.zone, sprite.seed, elapsed.longValue + 250, areaWidth, areaHeight)
                    translate(at.x, at.y) {
                        scale(if (ahead.x < at.x) -1f else 1f, 1f, spriteSize.center) {
                            with(painter) { draw(spriteSize, colorFilter = tints[index]) }
                        }
                    }
                }
                bubbles.forEach { (x, phase) ->
                    val (dy, alpha) = bubbleRise((rise + phase) % 1f, h - 26 * dp)
                    drawCircle(Color.White.copy(alpha = .7f * alpha), 2.5f * dp, Offset(w * x, h - 14 * dp + dy), style = bubbleStroke)
                }
            }
        },
    )
}
