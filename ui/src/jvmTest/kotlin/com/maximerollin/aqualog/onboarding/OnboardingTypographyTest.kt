package com.maximerollin.aqualog.onboarding

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Onboarding text comes from the theme: a bare TextStyle would replace LocalTextStyle and fall back to the system font. */
class OnboardingTypographyTest {
    @Test
    fun `every TextStyle call in the onboarding sources names a font family`() {
        val dir = File("src/commonMain/kotlin/com/maximerollin/aqualog/onboarding")
        val files = dir.listFiles { file -> file.extension == "kt" }.orEmpty()
        assertTrue(files.isNotEmpty(), "onboarding sources not found from ${File(".").absolutePath}")
        val bare = mutableListOf<String>()
        files.forEach { file ->
            val text = file.readText()
            var index = text.indexOf("TextStyle(")
            while (index >= 0) {
                var depth = 0
                var end = index + "TextStyle".length
                do {
                    when (text[end]) { '(' -> depth++; ')' -> depth-- }
                    end++
                } while (depth > 0)
                if (!text.substring(index, end).contains("fontFamily")) {
                    bare += "${file.name}:${text.substring(0, index).count { it == '\n' } + 1}"
                }
                index = text.indexOf("TextStyle(", end)
            }
        }
        assertTrue(bare.isEmpty(), "TextStyle without fontFamily at $bare")
    }
}
