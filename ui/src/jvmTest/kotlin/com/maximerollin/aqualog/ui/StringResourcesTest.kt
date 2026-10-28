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
            assertEquals("Continuer vers l'Aquarium", getString(Res.string.continue_to_aquarium))
            assertEquals("2 Mesures", getPluralString(Res.plurals.session_measurement_count, 2, 2))
            assertEquals("1 Mesure", getPluralString(Res.plurals.session_measurement_count, 1, 1))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
