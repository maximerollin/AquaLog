package com.maximerollin.aqualog

import java.io.File
import java.util.Locale
import java.util.TimeZone
import com.maximerollin.aqualog.shared.ANNUAL_PRO_PRODUCT_ID
import com.maximerollin.aqualog.shared.AnnualProOffer
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
        assertEquals(
            "Actions d'entretien",
            stringValue("values-fr", "maintenance_heading").replace('’', '\'').replace("\\'", "'"),
        )
        assertTrue(stringValue("values-it", "tasks_title").startsWith("Attività"))
        listOf("open_tasks", "tasks_title", "new_task", "task_title_label", "save_task", "no_pending_tasks").forEach { key ->
            assertTrue(
                stringValue("values-it", key).contains("attività", ignoreCase = true),
                "values-it/$key must use the canonical Task term Attività",
            )
        }
        listOf(
            "welcome_description",
            "maintenance_heading",
            "delete_session_description",
            "portable_import_confirmation_description",
            "portable_problem_actions",
        ).forEach { key ->
            assertTrue(
                stringValue("values-de", key).contains("Wartungsaktion", ignoreCase = true),
                "values-de/$key must use the canonical Maintenance action term Wartungsaktion",
            )
        }
        listOf("session_maintenance_count", "timeline_action_count").forEach { key ->
            assertTrue(
                pluralText("values-de", key).contains("Wartungsaktion"),
                "values-de/$key must use the canonical Maintenance action term Wartungsaktion",
            )
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
    fun `saved session composes localized zero one and many counts`() {
        catalogs.forEach { catalog ->
            assertEquals(
                listOf("one", "other"),
                pluralQuantities(catalog, "session_measurement_count"),
                "$catalog measurement plural",
            )
            assertEquals(
                listOf("one", "other"),
                pluralQuantities(catalog, "session_maintenance_count"),
                "$catalog maintenance plural",
            )
            assertEquals(
                listOf("%1\$s", "%2\$s"),
                Regex("%\\d+\\\$s").findAll(stringValue(catalog, "session_saved_description")).map { it.value }.toList(),
            )
        }
    }

    @Test
    fun `catalogs avoid known machine translation anti patterns`() {
        val forbidden = mapOf(
            "values-fr" to listOf("Vélo", "procès n'a commencé", "Life Pro", ">Dos<", ">Maison<", ">Histoire<", "À payer", "actions de maintenance", "une maintenance"),
            "values-de" to listOf("Radfahren", "Proaktiv", "Lautstärke", "Prozess gestartet", ">Heim<", ">Geschichte<", "Archivaquarium", "Aufstand", "Klare Zeit"),
            "values-it" to listOf("Ciclismo", "Nessun processo", ">Casa<", ">Storia<", "Archivio Acquario", ">Vicino<", "Tempo sereno", "Fecondazione", "Ritorniamo"),
            "values-es" to listOf("Ciclismo", ">SDT<", "ningún juicio", "Life Pro", ">Hogar<", ">Historia<", "Archivo Acuario", ">Cerca<", ">Rever<", "tiempo claro", ">7 dias<"),
        )
        forbidden.forEach { (catalog, fragments) ->
            val xml = File(res, "$catalog/strings.xml").readText()
            fragments.forEach { assertFalse(xml.contains(it, ignoreCase = true), "$catalog contains '$it'") }
        }
    }

    @Test
    fun `interface locale does not choose measurement units`() {
        val frenchGallons = LocalizedFormatters(formatLocale = Locale.FRANCE).volume(12.5, com.maximerollin.aqualog.shared.VolumeUnit.US_GALLONS)
        val englishLiters = LocalizedFormatters(formatLocale = Locale.US).volume(12.5, com.maximerollin.aqualog.shared.VolumeUnit.LITERS)
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
            val formatter = LocalizedFormatters(formatLocale = locale, timeZone = TimeZone.getTimeZone("UTC"))
            assertEquals(number, formatter.number(1234.5, maximumFractionDigits = 1))
            assertTrue(formatter.dateTime(0).isNotBlank())
            assertTrue(formatter.time(9 * 60 + 5).contains("09") || formatter.time(9 * 60 + 5).contains("9"))
        }
    }

    @Test
    fun `wall clock time does not shift with winter or summer timezone offsets`() {
        val paris = LocalizedFormatters(Locale.FRANCE, TimeZone.getTimeZone("Europe/Paris"))
        val utc = LocalizedFormatters(Locale.FRANCE, TimeZone.getTimeZone("UTC"))
        assertEquals("09:05", paris.time(9 * 60 + 5, 1_768_435_200_000L))
        assertEquals("09:05", paris.time(9 * 60 + 5, 1_784_073_600_000L))
        assertEquals("09:05", utc.time(9 * 60 + 5))
    }

    @Test
    fun `format locale remains independent from interface locale`() {
        val interfaceLocales = listOf(Locale.ENGLISH, Locale.FRENCH, Locale.GERMAN, Locale.ITALIAN, Locale.forLanguageTag("es"))
        interfaceLocales.forEach { uiLocale ->
            assertEquals("1,5", LocalizedFormatters(Locale.FRANCE).number(1.5), "ui=$uiLocale")
            assertEquals("1.5", LocalizedFormatters(Locale.US).number(1.5), "ui=$uiLocale")
        }
    }

    @Test
    fun `structured trial billing date is formatted only at presentation`() {
        val offer = AnnualProOffer(ANNUAL_PRO_PRODUCT_ID, "14.99", 14, 1_767_225_600_000L)
        val utc = TimeZone.getTimeZone("UTC")
        assertTrue(offer.localizedBillingDate(LocalizedFormatters(Locale.US, utc)).contains("2026"))
        assertTrue(offer.localizedBillingDate(LocalizedFormatters(Locale.FRANCE, utc)).contains("2026"))
        assertFalse(offer.localizedBillingDate(LocalizedFormatters(Locale.US, utc)) == offer.localizedBillingDate(LocalizedFormatters(Locale.FRANCE, utc)))
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

    private fun pluralQuantities(folder: String, name: String): List<String> {
        val nodes = document(folder).getElementsByTagName("plurals")
        repeat(nodes.length) {
            val node = nodes.item(it)
            if (node.attributes.getNamedItem("name").nodeValue == name) {
                return (0 until node.childNodes.length).mapNotNull { index ->
                    node.childNodes.item(index).attributes?.getNamedItem("quantity")?.nodeValue
                }.sorted()
            }
        }
        error("Missing $folder/$name")
    }

    private fun pluralText(folder: String, name: String): String {
        val nodes = document(folder).getElementsByTagName("plurals")
        repeat(nodes.length) {
            val node = nodes.item(it)
            if (node.attributes.getNamedItem("name").nodeValue == name) return node.textContent
        }
        error("Missing $folder/$name")
    }

    private fun document(folder: String) = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder().parse(File(res, "$folder/strings.xml"))
}
