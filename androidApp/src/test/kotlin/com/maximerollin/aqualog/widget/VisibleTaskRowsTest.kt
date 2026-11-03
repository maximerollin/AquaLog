package com.maximerollin.aqualog.widget

import kotlin.test.Test
import kotlin.test.assertEquals

class VisibleTaskRowsTest {
    @Test
    fun smallestWidget_fitsNoRow() = assertEquals(0, visibleTaskRows(110f, taskCount = 3))

    @Test
    fun tallWidget_fitsThree() = assertEquals(3, visibleTaskRows(250f, taskCount = 5))

    @Test
    fun fewerTasksThanSpace_showsTheTasks() = assertEquals(1, visibleTaskRows(250f, taskCount = 1))

    @Test
    fun noTasks_showsNoRow() = assertEquals(0, visibleTaskRows(250f, taskCount = 0))

    @Test
    fun midHeight_fitsAsManyAsRoomAllows() = assertEquals(2, visibleTaskRows(180f, taskCount = 3))
}
