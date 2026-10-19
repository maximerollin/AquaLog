package com.maximerollin.aqualog

import java.io.File
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalizationContractTest {
    private val res = File("src/main/res")
    private val catalogs = listOf("values", "values-en", "values-fr", "values-de", "values-it", "values-es")

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
        val expectedSessions = mapOf(
            "values-en" to "Session",
            "values-fr" to "Session",
            "values-de" to "Sitzung",
            "values-it" to "Sessione",
            "values-es" to "Sesión",
        )
        expectedSessions.forEach { (catalog, term) ->
            assertTrue(stringValue(catalog, "rapid_session_title").contains(term, ignoreCase = true), catalog)
        }
        val glossary = mapOf(
            "values-en" to mapOf("timeline_session" to "Session", "timeline_events" to "Events", "tasks_title" to "Tasks"),
            "values-fr" to mapOf("timeline_session" to "Session", "timeline_events" to "Événements", "tasks_title" to "Tâches"),
            "values-de" to mapOf("timeline_session" to "Sitzung", "timeline_events" to "Ereignisse", "tasks_title" to "Aufgaben"),
            "values-it" to mapOf("timeline_session" to "Sessione", "timeline_events" to "Eventi", "tasks_title" to "Attività"),
            "values-es" to mapOf("timeline_session" to "Sesión", "timeline_events" to "Eventos", "tasks_title" to "Tareas"),
        )
        glossary.forEach { (catalog, terms) ->
            terms.forEach { (key, term) -> assertTrue(stringValue(catalog, key).contains(term), "$catalog/$key") }
        }
        catalogs.filterNot { it == "values" }.forEach { catalog ->
            assertEquals("TDS", stringValue(catalog, "parameter_tds"), "$catalog must preserve scientific abbreviations")
        }
    }

    @Test
    fun `translated placeholders and plural quantities match English`() {
        val reference = formatContracts("values-en")
        catalogs.filterNot { it == "values" || it == "values-en" }.forEach { catalog ->
            assertEquals(reference, formatContracts(catalog), "$catalog changed placeholders or plural quantities")
        }
    }

    @Test
    fun `catalogs avoid known machine translation anti patterns`() {
        val forbidden = mapOf(
            "values-fr" to listOf("Vélo", "procès n'a commencé", "Life Pro", ">Dos<", ">Maison<", ">Histoire<", "À payer"),
            "values-de" to listOf("Radfahren", "Proaktiv", "Lautstärke", "Prozess gestartet", ">Heim<", ">Geschichte<", "Archivaquarium", "Aufstand", "Klare Zeit"),
            "values-it" to listOf("Ciclismo", "Nessun processo", ">Casa<", ">Storia<", "Archivio Acquario", ">Vicino<", "Tempo sereno"),
            "values-es" to listOf("Ciclismo", ">SDT<", "ningún juicio", "Life Pro", ">Hogar<", ">Historia<", "Archivo Acuario", ">Cerca<", ">Rever<", "tiempo claro"),
        )
        forbidden.forEach { (catalog, fragments) ->
            val xml = File(res, "$catalog/strings.xml").readText()
            fragments.forEach { assertFalse(xml.contains(it, ignoreCase = true), "$catalog contains '$it'") }
        }
    }

    @Test
    fun `interface locale does not choose measurement units`() {
        val frenchGallons = LocalizedFormatters(Locale.FRANCE).volume(12.5, com.maximerollin.aqualog.shared.VolumeUnit.US_GALLONS)
        val englishLiters = LocalizedFormatters(Locale.US).volume(12.5, com.maximerollin.aqualog.shared.VolumeUnit.LITERS)
        assertEquals("12,5 US gal", frenchGallons)
        assertEquals("12.5 L", englishLiters)
    }

    @Test
    fun `five locales format numbers dates and times naturally`() {
        val expectedNumbers = mapOf(
            Locale.ENGLISH to "1234.5", Locale.FRENCH to "1234,5", Locale.GERMAN to "1234,5",
            Locale.ITALIAN to "1234,5", Locale.forLanguageTag("es") to "1234,5",
        )
        expectedNumbers.forEach { (locale, number) ->
            val formatter = LocalizedFormatters(locale, TimeZone.getTimeZone("UTC"))
            assertEquals(number, formatter.number(1234.5, maximumFractionDigits = 1))
            assertTrue(formatter.dateTime(0).isNotBlank())
            assertTrue(formatter.time(9 * 60 + 5).contains("09") || formatter.time(9 * 60 + 5).contains("9"))
        }
    }

    @Test
    fun `visible compose literals are resource backed`() {
        val productionFiles = File("src/main/kotlin/com/maximerollin/aqualog")
            .walkTopDown().filter { it.isFile && it.extension == "kt" }
        val forbidden = listOf(
            Regex("Text\\s*\\(\\s*\\\"", setOf(RegexOption.MULTILINE)),
            Regex("contentDescription\\s*=\\s*\\\"", setOf(RegexOption.MULTILINE)),
            Regex("(?:showSnackbar|makeText)\\s*\\([^)]*\\\"[A-Za-zÀ-ÿ]", setOf(RegexOption.DOT_MATCHES_ALL)),
            Regex("(?:message|safeDetail)\\s*=\\s*\\\"[A-Za-zÀ-ÿ]", setOf(RegexOption.MULTILINE)),
        )
        val violations = productionFiles.flatMap { file ->
            val source = file.readText()
            forbidden.flatMap { pattern -> pattern.findAll(source).map { match ->
                val line = source.take(match.range.first).count { it == '\n' } + 1
                "${file.name}:$line: ${match.value.replace('\n', ' ').take(120)}"
            } }
        }.toList()
        assertTrue(violations.isEmpty(), "visible literals must use resources: $violations")
    }

    private fun formatContracts(folder: String): Map<String, String> {
        val root = document(folder)
        return buildMap {
            listOf("string", "plurals").forEach { tag ->
                val nodes = root.getElementsByTagName(tag)
                repeat(nodes.length) {
                    val node = nodes.item(it)
                    val name = node.attributes.getNamedItem("name").nodeValue
                    val placeholders = Regex("%\\d+\\$[sdf]|%[sdf]").findAll(node.textContent).map { match -> match.value }.sorted().toList()
                    val quantities = if (tag == "plurals") {
                        (0 until node.childNodes.length).mapNotNull { childIndex ->
                            node.childNodes.item(childIndex).attributes?.getNamedItem("quantity")?.nodeValue
                        }.sorted()
                    } else emptyList()
                    put("$tag:$name", "$placeholders|$quantities")
                }
            }
        }
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
