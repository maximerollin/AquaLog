package com.maximerollin.aqualog.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.rememberReducedMotion
import com.maximerollin.aqualog.shared.Aquarium
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tan

private const val SwayMillis = 5_000
private const val BubbleMillis = 4_000

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
private val bubbles = listOf(.3f to 0f, .34f to .375f, .28f to .7f) // x fraction, phase
private val rays = listOf(.12f to 1f, .5f to .6f) // x fraction, alpha

/** Decorative tank (water, sand, plants, bubbles, light). Lot 5 puts the fish on top of it. */
@Composable
@Suppress("UNUSED_PARAMETER") // the aquarium will drive the fish
fun TankScene(aquarium: Aquarium, modifier: Modifier = Modifier, animate: Boolean = !rememberReducedMotion()) {
    val sway: State<Float>
    val rise: State<Float>
    if (animate) {
        val transition = rememberInfiniteTransition(label = "tank")
        sway = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(SwayMillis, easing = LinearEasing), RepeatMode.Restart), "sway")
        rise = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(BubbleMillis, easing = LinearEasing), RepeatMode.Restart), "rise")
    } else {
        // Single still frame: plants upright, bubbles spread along their rise.
        sway = mutableStateOf(0f)
        rise = mutableStateOf(0f)
    }
    Canvas(modifier.height(140.dp).clearAndSetSemantics { }) {
        drawTank(sway.value, rise.value)
    }
}

private fun DrawScope.drawTank(sway: Float, rise: Float) {
    val dp = density
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF3D9184), Color(0xFF1F7468), Color(0xFF0F4A43))))
    rays.forEach { (x, alpha) -> drawRay(w * x, 30 * dp, h, alpha) }
    val sandTop = h - 16 * dp
    plants.forEach { drawPlant(it, w * it.x, h - 10 * dp, plantSway((sway + it.delay) % 1f), dp) }
    drawRoundSand(sandTop, w, h)
    bubbles.forEach { (x, phase) ->
        val (dy, alpha) = bubbleRise((rise + phase) % 1f, h - 26 * dp)
        drawCircle(Color.White.copy(alpha = .7f * alpha), 2.5f * dp, Offset(w * x, h - 14 * dp + dy), style = Stroke(dp))
    }
}

private fun DrawScope.drawRay(x: Float, width: Float, height: Float, alpha: Float) {
    val lean = tan(18f * PI.toFloat() / 180f) * height // top edge leans right of the bottom edge
    val path = Path().apply {
        moveTo(x + lean, 0f); lineTo(x + lean + width, 0f); lineTo(x + width, height); lineTo(x, height); close()
    }
    drawPath(path, Brush.verticalGradient(listOf(Color.White.copy(alpha = .14f * alpha), Color.Transparent), 0f, height))
}

private fun DrawScope.drawPlant(plant: Plant, x: Float, baseY: Float, degrees: Float, dp: Float) {
    val top = baseY - plant.height * dp
    rotate(degrees, Offset(x, baseY)) {
        val stem = Path().apply {
            moveTo(x, baseY)
            quadraticTo(x + plant.bend * dp, baseY - plant.height * dp / 2, x + plant.bend * -.3f * dp, top)
        }
        drawPath(stem, plant.stem, style = Stroke(3 * dp))
        plant.leaves.forEach { (dx, y) ->
            drawOval(leafColor, Offset(x + (dx - 5) * dp, baseY - y * dp - 2 * dp), Size(10 * dp, 4 * dp))
        }
    }
}

private fun DrawScope.drawRoundSand(top: Float, w: Float, h: Float) {
    val path = Path().apply {
        moveTo(0f, h); lineTo(0f, top + 6 * density)
        quadraticTo(w / 2, top - 6 * density, w, top + 6 * density)
        lineTo(w, h); close()
    }
    drawPath(path, Brush.verticalGradient(listOf(Color(0xFFD6C497), Color(0xFFC3AE7E)), top, h))
}
