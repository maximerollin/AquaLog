package com.maximerollin.aqualog.widget

import kotlin.test.Test
import kotlin.test.assertEquals

class VisibleTaskRowsTest {
    @Test
    fun smallestWidget_fitsNoRow() = assertEquals(0, visibleTaskRows(110f, taskCount = 3))

    @Test
    fun smallestMediumWidget_fitsOneRow() = assertEquals(1, visibleTaskRows(130f, taskCount = 3))

    @Test
    fun tallWidget_fitsThree() = assertEquals(3, visibleTaskRows(250f, taskCount = 5))

    @Test
    fun fewerTasksThanSpace_showsTheTasks() = assertEquals(1, visibleTaskRows(250f, taskCount = 1))

    @Test
    fun noTasks_showsNoRow() = assertEquals(0, visibleTaskRows(250f, taskCount = 0))

    @Test
    fun midHeight_fitsAsManyAsRoomAllows() = assertEquals(2, visibleTaskRows(180f, taskCount = 3))

    // A 4×2 on the Pixel 9a launcher: two 104 dp rows and a 16 dp gap.
    @Test
    fun pixelLauncherDefault4x2_fitsTwoRows() = assertEquals(2, visibleTaskRows(224f, taskCount = 3))
}
