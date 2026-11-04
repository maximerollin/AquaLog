package com.maximerollin.aqualog.encyclopedia

import com.maximerollin.aqualog.resources.Res
import com.maximerollin.aqualog.shared.Inhabitant
import com.maximerollin.aqualog.shared.encyclopedia.Species
import com.maximerollin.aqualog.shared.encyclopedia.decodeSpecies
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.ExperimentalResourceApi

private var cached: List<Species>? = null
private val loadLock = Mutex()

@OptIn(ExperimentalResourceApi::class)
suspend fun loadEncyclopedia(): List<Species> = loadLock.withLock {
    cached ?: decodeSpecies(Res.readBytes("files/encyclopedia/species.json").decodeToString()).also { cached = it }
}

fun Species.localName(languageTag: String): String =
    names[languageTag.substringBefore('-').lowercase()] ?: names["fr"] ?: scientificName

/** The typed name of a custom species, else the encyclopedia name; the species id when the encyclopedia is unavailable. */
fun Inhabitant.displayName(encyclopedia: List<Species>?, languageTag: String): String =
    customName?.takeIf(String::isNotBlank) ?: speciesId?.let { id -> encyclopedia?.firstOrNull { it.id == id }?.localName(languageTag) ?: id } ?: ""
