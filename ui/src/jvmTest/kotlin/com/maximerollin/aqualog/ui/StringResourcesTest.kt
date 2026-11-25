package com.maximerollin.aqualog.ui

import com.maximerollin.aqualog.resources.*
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

class StringResourcesTest {
    @Test
    fun `android escapes and positional plurals resolve like Android resources`() = runBlocking {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.FRENCH)
        try {
            assertEquals("Continuer vers l’aquarium", getString(Res.string.continue_to_aquarium))
            assertEquals("2 mesures", getPluralString(Res.plurals.session_measurement_count, 2, 2))
            assertEquals("1 mesure", getPluralString(Res.plurals.session_measurement_count, 1, 1))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
