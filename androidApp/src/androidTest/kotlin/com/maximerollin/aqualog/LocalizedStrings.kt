package com.maximerollin.aqualog

import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getSystemResourceEnvironment

/** Resolves Compose string resources in one language, as a localized Android Context did for R.string. */
internal class LocalizedStrings(language: String? = null) {
    // The system resource environment reads its language from Locale.getDefault().
    private val environment = if (language == null) getSystemResourceEnvironment() else {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag(language))
        try { getSystemResourceEnvironment() } finally { Locale.setDefault(previous) }
    }

    fun getString(resource: StringResource, vararg args: Any): String =
        runBlocking { getString(environment, resource, *args) }

    fun getQuantityString(resource: PluralStringResource, quantity: Int, vararg args: Any): String =
        runBlocking { getPluralString(environment, resource, quantity, *args) }
}
