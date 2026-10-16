package com.maximerollin.aqualog

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalizationContractTest {
    private val res = File("src/main/res")
    private val catalogs = listOf("values", "values-fr", "values-de", "values-it", "values-es")

    @Test
    fun `all five catalogs expose the same strings and plurals`() {
        val keys = catalogs.associateWith(::resourceKeys)
        val reference = keys.getValue("values")
        keys.forEach { (catalog, actual) ->
            assertEquals(reference, actual, "$catalog must not miss or add translation keys")
        }
        assertTrue(reference.size >= 300, "the contract must cover the complete product catalog")
    }

    @Test
    fun `aquarium glossary is translated consistently in each catalog`() {
        assertEquals("Aquarien", stringValue("values-de", "aquariums_title"))
        assertEquals("Aquariums", stringValue("values-en", "aquariums_title"))
        assertEquals("Aquariums", stringValue("values-fr", "aquariums_title"))
        assertEquals("Acquari", stringValue("values-it", "aquariums_title"))
        assertEquals("Acuarios", stringValue("values-es", "aquariums_title"))
        assertEquals("Cronologia", stringValue("values-it", "timeline_title"))
        assertEquals("Cronología", stringValue("values-es", "timeline_title"))
    }

    @Test
    fun `interface locale does not choose measurement units`() {
        val frenchGallons = LocalizedFormatters(Locale.FRANCE).volume(12.5, VolumeDisplayUnit.US_GALLONS)
        val englishLiters = LocalizedFormatters(Locale.US).volume(12.5, VolumeDisplayUnit.LITERS)
        assertEquals("12,5 US gal", frenchGallons)
        assertEquals("12.5 L", englishLiters)
    }

    @Test
    fun `decimal parsing accepts point and comma independently from display locale`() {
        assertEquals(7.25, LocalizedFormatters(Locale.GERMANY).parseDecimal("7.25"))
        assertEquals(7.25, LocalizedFormatters(Locale.US).parseDecimal("7,25"))
    }

    @Test
    fun `visible compose literals are resource backed`() {
        val uiFiles = File("src/main/kotlin/com/maximerollin/aqualog")
            .walkTopDown().filter { it.isFile && it.name.endsWith("Ui.kt") }
        val forbidden = Regex("(?:Text\\s*\\(\\s*|contentDescription\\s*=\\s*)\\\"[A-Za-zÀ-ÿ]")
        val violations = uiFiles.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (forbidden.containsMatchIn(line)) "${file.name}:${index + 1}" else null
            }
        }.toList()
        assertTrue(violations.isEmpty(), "visible literals must use resources: $violations")
    }

    private fun resourceKeys(folder: String): Set<String> {
        val root = document(folder)
        return buildSet {
            listOf("string", "plurals").forEach { tag ->
                val nodes = root.getElementsByTagName(tag)
                repeat(nodes.length) { add("$tag:${nodes.item(it).attributes.getNamedItem("name").nodeValue}") }
            }
        }
    }

    private fun stringValue(folder: String, name: String): String {
        val nodes = document(folder).getElementsByTagName("string")
        repeat(nodes.length) {
            val node = nodes.item(it)
            if (node.attributes.getNamedItem("name").nodeValue == name) return node.textContent
        }
        error("Missing $folder/$name")
    }

    private fun document(folder: String) = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder().parse(File(res, "$folder/strings.xml"))
}
