package com.maximerollin.aqualog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedbackLanguageTest {
    @Test
    fun `catalog languages are kept`() {
        for (language in listOf("fr", "en", "de", "it", "es")) assertEquals(language, feedbackLanguage(language))
    }

    @Test
    fun `unsupported or empty languages fall back to English`() {
        assertEquals("en", feedbackLanguage("pt"))
        assertEquals("en", feedbackLanguage(""))
    }

    @Test
    fun `every result matches the server format`() {
        val format = Regex("^[A-Za-z]{2,3}(-[A-Za-z0-9]{1,8})*$")
        for (language in listOf("fr", "en", "de", "it", "es", "pt", "", "zh", "und", "de-DE-u-mu-celsius")) {
            assertTrue(format.matches(feedbackLanguage(language)), language)
        }
    }
}
