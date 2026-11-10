package com.maximerollin.aqualog

private val catalogLanguages = setOf("fr", "en", "de", "it", "es")

/** The language of the catalog the user sees, as sent with feedback: the device language when translated, English otherwise. */
fun feedbackLanguage(language: String): String = language.lowercase().takeIf { it in catalogLanguages } ?: "en"
