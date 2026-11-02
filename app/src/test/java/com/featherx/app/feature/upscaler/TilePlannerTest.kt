package com.featherx.app.feature.upscaler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TilePlannerTest {

    private fun assertExactCoverage(w: Int, h: Int) {
        val cover = IntArray(w * h)
        for (t in TilePlanner.plan(w, h)) {
            assertTrue(t.cw in 1..TilePlanner.CORE)
            assertTrue(t.ch in 1..TilePlanner.CORE)
            // The 224x224 window (core +- MARGIN) must contain the whole core.
            assertTrue(t.cx - TilePlanner.MARGIN + TilePlanner.TILE >= t.cx + t.cw)
            assertTrue(t.cy - TilePlanner.MARGIN + TilePlanner.TILE >= t.cy + t.ch)
            for (y in t.cy until t.cy + t.ch) {
                for (x in t.cx until t.cx + t.cw) cover[y * w + x]++
            }
        }
        assertTrue("every pixel covered exactly once for ${w}x$h", cover.all { it == 1 })
    }

    @Test
    fun coversEverySourcePixelExactlyOnce() {
        val sizes = listOf(
            1 to 1, 7 to 300, 208 to 208, 209 to 209, 224 to 224,
            640 to 480, 639 to 481, 1000 to 3,
        )
        for ((w, h) in sizes) assertExactCoverage(w, h)
    }

    @Test
    fun tileCountsMatchExpectation() {
        assertEquals(1, TilePlanner.plan(208, 208).size)
        assertEquals(4, TilePlanner.plan(209, 209).size)
        assertEquals(12, TilePlanner.plan(640, 480).size)
    }

    @Test
    fun constantsAreConsistentWithModelShape() {
        assertEquals(224, TilePlanner.TILE)
        assertEquals(672, TilePlanner.OUT_TILE)
        assertEquals(208, TilePlanner.CORE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyImage() {
        TilePlanner.plan(0, 10)
    }
}
