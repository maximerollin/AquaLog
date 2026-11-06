package com.maximerollin.aqualog.widget

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The widget cannot read compose resources, so it carries Android copies of the family silhouettes. */
class WidgetFamilyTwinsTest {
    private val composeDrawables = File("../ui/src/commonMain/composeResources/drawable")
    private val androidDrawables = File("src/main/res/drawable")
    private val pathData = Regex("android:pathData=\"([^\"]*)\"")

    private fun File.paths() = pathData.findAll(readText()).map { it.groupValues[1] }.toList()

    // The widget draws no plants: they are decor, not swimmers.
    private val families = composeDrawables.listFiles().orEmpty()
        .filter { it.name.startsWith("family_") && it.name != "family_plant.xml" }

    @Test
    fun everyFamilyHasATwinWithTheSamePaths() {
        assertTrue(families.size >= 8, "found ${families.size} families in $composeDrawables")
        for (family in families) {
            val twin = File(androidDrawables, "widget_${family.name}")
            assertTrue(twin.exists(), "missing ${twin.name}")
            assertEquals(family.paths(), twin.paths(), "${twin.name} paths differ from ${family.name}")
        }
    }
}
