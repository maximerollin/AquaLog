package com.maximerollin.aqualog.ui.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The immersive redesign has no framed fields left (spec: « plus aucun champ encadré »): text goes in [FilledField], a choice
 * in a selectable card, row or segmented control. This scan keeps the stock Material inputs from coming back.
 */
class NoStockMaterialInputsTest {
    @Test
    fun `ui sources use no outlined text field and no radio button`() {
        val root = File("src/commonMain/kotlin")
        val files = root.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty(), "ui sources not found from ${File(".").absolutePath}")
        val forbidden = listOf("OutlinedTextField(", "RadioButton(")
        val found = files.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                forbidden.firstOrNull { it in line }?.let { "${file.name}:${index + 1} $it" }
            }
        }
        assertTrue(found.isEmpty(), "stock Material inputs at $found")
    }
}
