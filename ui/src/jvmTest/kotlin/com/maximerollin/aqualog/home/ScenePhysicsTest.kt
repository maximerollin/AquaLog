package com.maximerollin.aqualog.home

import com.maximerollin.aqualog.shared.Inhabitant
import com.maximerollin.aqualog.shared.encyclopedia.DrawingFamily
import com.maximerollin.aqualog.shared.encyclopedia.MotionFamily
import com.maximerollin.aqualog.shared.encyclopedia.SwimZone
import com.maximerollin.aqualog.shared.encyclopedia.decodeSpecies
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import java.io.File
import kotlin.test.assertFailsWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScenePhysicsTest {
    private val species = decodeSpecies(File("src/commonMain/composeResources/files/encyclopedia/species.json").readText())
    private val byId = species.associateBy { it.id }

    private fun group(id: String, speciesId: String?, count: Int, family: DrawingFamily = DrawingFamily.TETRA, customName: String? = null) =
        Inhabitant(id, "a", speciesId, customName, family, count, 0L)

    @Test
    fun plantSwayEasesInAndOutOfTheAmplitude() {
        assertEquals(0f, plantSway(0f), 1e-4f)
        assertEquals(4f, plantSway(0.5f), 1e-4f)
        assertEquals(0f, plantSway(1f), 1e-4f)
    }

    @Test
    fun bubbleRisesLinearlyAndFadesOut() {
        assertEquals(0f to 1f, bubbleRise(0f, 140f))
        assertEquals(-140f to 0f, bubbleRise(1f, 140f))
    }

    @Test
    fun cycleProgressLoopsOverItsPeriod() {
        assertEquals(0f, cycleProgress(0, 4_000))
        assertEquals(.25f, cycleProgress(1_000, 4_000))
        assertEquals(0f, cycleProgress(4_000, 4_000))
        assertEquals(.5f, cycleProgress(10_000, 4_000))
    }

    @Test
    fun `200 neons and 10 groups give at most 12 sprites, one per group`() {
        val others = species.filter { it.id != "neon" }.take(9).mapIndexed { i, s -> group("g$i", s.id, 5, s.family) }
        val sprites = sceneSprites(listOf(group("neons", "neon", 200)) + others, byId)
        assertEquals(12, sprites.size)
        val perDrawing = sprites.groupingBy { it.drawing }.eachCount()
        assertEquals(10, perDrawing.size)
        // Proportional: the 200 neons take the spare sprites.
        assertEquals(3, perDrawing.getValue(sceneSprites(listOf(group("n", "neon", 1)), byId).single().drawing))
    }

    @Test
    fun `more groups than sprites keep the biggest groups, one each`() {
        val groups = (1..14).map { group("g$it", "neon", it) }
        val sprites = sceneSprites(groups, byId)
        assertEquals(12, sprites.size)
        assertEquals(12, sprites.map { it.seed shr 4 }.distinct().size)
    }

    @Test
    fun `a group never shows more sprites than animals`() {
        assertEquals(3, sceneSprites(listOf(group("a", "neon", 1), group("b", "guppy", 2)), byId).size)
    }

    @Test
    fun `plants give no sprite`() {
        assertEquals(emptyList(), sceneSprites(listOf(group("p", null, 8, DrawingFamily.PLANT, "Anubias")), byId))
    }

    @Test
    fun `a dedicated drawing is not tinted, a silhouette is`() {
        assertNull(sceneSprites(listOf(group("a", "neon", 1)), byId).single().tint)
        // Without the encyclopedia (load failure) or for a custom species, the family silhouette moves with its family.
        val fallback = sceneSprites(listOf(group("a", "neon", 1)), emptyMap()).single()
        assertNotNull(fallback.tint)
        assertEquals(MotionFamily.SCHOOL, fallback.motion)
        val custom = sceneSprites(listOf(group("c", null, 1, DrawingFamily.CATFISH, "Pléco")), byId).single()
        assertEquals(MotionFamily.BOTTOM, custom.motion)
        assertEquals(SwimZone.BOTTOM, custom.zone)
    }

    @Test
    fun `BOTTOM stays in the lowest 20 percent for any time`() {
        for (seed in 0 until 40) for (t in 0L until 1_000L) {
            val y = spritePosition(MotionFamily.BOTTOM, SwimZone.MIDDLE, seed * 37, t * 997, 300f, 120f).y
            assertTrue(y >= 120f * .8f && y <= 120f, "seed $seed at ${t * 997} ms: y = $y")
        }
    }

    @Test
    fun `every motion stays inside the scene`() {
        for (motion in MotionFamily.entries) for (zone in SwimZone.entries) for (seed in 0 until 50) for (t in 0L until 200L) {
            val p = spritePosition(motion, zone, seed * 131 + 7, t * 4_999, 300f, 120f)
            assertTrue(p.x in 0f..300f && p.y in 0f..120f, "$motion $zone seed $seed at ${t * 4_999} ms: $p")
        }
    }

    @Test
    fun `elapsed 0 is a stable still frame`() {
        for (motion in MotionFamily.entries) {
            assertEquals(spritePosition(motion, SwimZone.MIDDLE, 42, 0, 300f, 120f), spritePosition(motion, SwimZone.MIDDLE, 42, 0, 300f, 120f))
        }
        // Fish of a school start apart, not stacked on one point.
        val school = (0 until 6).map { spritePosition(MotionFamily.SCHOOL, SwimZone.MIDDLE, (5 shl 4) or it, 0, 300f, 120f) }
        assertEquals(6, school.distinct().size)
    }

    @Test
    fun `a school moves together`() {
        // Members of one school stay within a loose cluster while it crosses the scene.
        for (t in 0L until 100L) {
            val xs = (0 until 6).map { spritePosition(MotionFamily.SCHOOL, SwimZone.MIDDLE, (9 shl 4) or it, t * 1_000, 300f, 120f).x }
            assertTrue(xs.max() - xs.min() <= 300f * .3f, "at ${t}s: $xs")
        }
    }

    private data class Golden(val motion: MotionFamily, val zone: SwimZone, val seed: Int, val elapsed: Long, val x: Float, val y: Float)

    @Test
    fun `positions are unchanged by the allocation-free rewrite`() {
        // Recorded from the first implementation (Pair-based) before it stopped allocating per frame.
        listOf(
        Golden(MotionFamily.SCHOOL, SwimZone.MIDDLE, 7, 0L, 113.90116f, 53.735992f),
        Golden(MotionFamily.SCHOOL, SwimZone.MIDDLE, 1234, 12345L, 47.929176f, 59.064877f),
        Golden(MotionFamily.SCHOOL, SwimZone.MIDDLE, 1235, 987654L, 253.74493f, 36.0f),
        Golden(MotionFamily.BOTTOM, SwimZone.MIDDLE, 7, 0L, 123.15826f, 106.69221f),
        Golden(MotionFamily.BOTTOM, SwimZone.MIDDLE, 1234, 12345L, 280.14047f, 108.84962f),
        Golden(MotionFamily.BOTTOM, SwimZone.MIDDLE, 1235, 987654L, 28.433239f, 96.15823f),
        Golden(MotionFamily.GLIDE, SwimZone.BOTTOM, 7, 0L, 123.15563f, 105.71503f),
        Golden(MotionFamily.GLIDE, SwimZone.BOTTOM, 1234, 12345L, 263.09314f, 109.881096f),
        Golden(MotionFamily.GLIDE, SwimZone.BOTTOM, 1235, 987654L, 122.723625f, 98.053665f),
        Golden(MotionFamily.GLIDE, SwimZone.MIDDLE, 7, 0L, 123.15563f, 53.734177f),
        Golden(MotionFamily.GLIDE, SwimZone.MIDDLE, 1234, 12345L, 263.09314f, 59.518314f),
        Golden(MotionFamily.GLIDE, SwimZone.MIDDLE, 1235, 987654L, 122.723625f, 38.17234f),
        Golden(MotionFamily.GLIDE, SwimZone.SURFACE, 7, 0L, 123.15563f, 18.388071f),
        Golden(MotionFamily.GLIDE, SwimZone.SURFACE, 1234, 12345L, 263.09314f, 23.093506f),
        Golden(MotionFamily.GLIDE, SwimZone.SURFACE, 1235, 987654L, 122.723625f, 8.093225f),
        Golden(MotionFamily.CRAWL, SwimZone.MIDDLE, 7, 0L, 74.88236f, 108.02299f),
        Golden(MotionFamily.CRAWL, SwimZone.MIDDLE, 1234, 12345L, 149.57275f, 109.96465f),
        Golden(MotionFamily.CRAWL, SwimZone.MIDDLE, 1235, 987654L, 114.303085f, 98.542404f),
        Golden(MotionFamily.SLOW, SwimZone.MIDDLE, 7, 0L, 80.845955f, 54.060425f),
        Golden(MotionFamily.SLOW, SwimZone.MIDDLE, 1234, 12345L, 147.49832f, 56.35431f),
        Golden(MotionFamily.SLOW, SwimZone.MIDDLE, 1235, 987654L, 158.40343f, 36.0f),
        ).forEach { g ->
            val p = spritePosition(g.motion, g.zone, g.seed, g.elapsed, 300f, 120f)
            assertEquals(g.x, p.x, 1e-3f, "$g")
            assertEquals(g.y, p.y, 1e-3f, "$g")
        }
    }

    @Test
    fun `sprite ranks fit in the seed`() {
        assertFailsWith<IllegalArgumentException> { sceneSprites(emptyList(), byId, max = 17) }
    }

    @Test
    fun `each plant group adds a decor plant, up to 3`() {
        val plant = { id: String -> group(id, null, 5, DrawingFamily.PLANT, "Anubias") }
        assertEquals(0, decorPlants(listOf(group("n", "neon", 10))))
        assertEquals(1, decorPlants(listOf(plant("a"), group("n", "neon", 10))))
        assertEquals(3, decorPlants((1..5).map { plant("p$it") }))
    }

    @Test
    fun `white header text stays readable over the lightest sprites`() {
        for (sprite in listOf(Color(0xFFE3EAEC), Color(0xFFE8E0D0), Color(0xFFDCE0E6), Color.White)) {
            val behind = HeaderScrim.compositeOver(sprite).luminance()
            val contrast = (Color.White.luminance() + .05f) / (behind + .05f)
            assertTrue(contrast >= 4.5f, "over $sprite: $contrast")
        }
    }
}
