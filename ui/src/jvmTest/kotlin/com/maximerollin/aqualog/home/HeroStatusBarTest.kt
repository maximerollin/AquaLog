package com.maximerollin.aqualog.home

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeroStatusBarTest {
    // The hero item takes 400 px of the list (its overlap already removed); the status bar is 100 px.
    private fun overWater(index: Int, offset: Int) =
        heroUnderStatusBar(firstVisibleItemIndex = index, firstVisibleItemScrollOffset = offset, heroHeightPx = 400, statusBarHeightPx = 100)

    @Test fun `at the top the status bar is over the water`() = assertTrue(overWater(0, 0))

    @Test fun `scrolled a little the water is still under the status bar`() = assertTrue(overWater(0, 299))

    @Test fun `once the sheet reaches the status bar it is over paper`() = assertFalse(overWater(0, 300))

    @Test fun `with the hero scrolled away it is over paper`() = assertFalse(overWater(1, 0))

    // The first frame, before the list has laid the hero out: the list opens on the water, so no flash of paper.
    @Test fun `before the hero is measured the top of the list is water`() =
        assertTrue(heroUnderStatusBar(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0, heroHeightPx = null, statusBarHeightPx = 100))
}
